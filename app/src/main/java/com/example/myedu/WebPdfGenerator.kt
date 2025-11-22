package com.example.myedu

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WebPdfGenerator(private val context: Context) {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun generatePdf(
        studentInfoJson: String,
        transcriptJson: String,
        linkId: Long,
        qrUrl: String,
        resources: PdfResources
    ): ByteArray = suspendCancellableCoroutine { continuation ->
        
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            
            // Log JS console errors to Android Logcat
            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                    android.util.Log.d("WebViewConsole", "${cm.message()} -- From line ${cm.lineNumber()} of ${cm.sourceId()}")
                    return true
                }
            }

            webView.addJavascriptInterface(object : Any() {
                @JavascriptInterface
                fun returnPdf(base64: String) {
                    try {
                        val cleanBase64 = base64.replace("data:application/pdf;base64,", "")
                        val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                        if (continuation.isActive) continuation.resume(bytes)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }

                @JavascriptInterface
                fun returnError(msg: String) {
                    if (continuation.isActive) continuation.resumeWithException(Exception("JS Error: $msg"))
                }
                
                @JavascriptInterface
                fun log(msg: String) {
                    android.util.Log.d("WebViewJS", msg)
                }
            }, "AndroidBridge")

            val htmlContent = getHtmlContent(studentInfoJson, transcriptJson, linkId, qrUrl, resources)
            
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Start generation automatically when page loads
                    webView.evaluateJavascript("startGeneration();", null)
                }
            }
            
            webView.loadDataWithBaseURL("https://myedu.oshsu.kg/", htmlContent, "text/html", "UTF-8", null)
        }
    }

    private fun getHtmlContent(info: String, transcript: String, linkId: Long, qrUrl: String, res: PdfResources): String {
        // Fallback pixel if stamp extraction failed
        val validStamp = if (res.stampBase64.isNotEmpty()) res.stampBase64 else "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"

        return """
<!DOCTYPE html>
<html>
<head>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/pdfmake/0.2.7/pdfmake.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/pdfmake/0.2.7/vfs_fonts.js"></script>
</head>
<body>
<script>
    // Data from Android
    const studentInfo = $info;
    const transcriptData = $transcript;
    const linkId = $linkId;
    const qrCodeUrl = "$qrUrl";
    const mt = "$validStamp"; 

    // --- Mocks for Vue/Environment ---
    const ${'$'} = function(d) {
        return {
            format: function(fmt) {
                const date = d ? new Date(d) : new Date();
                return date.toLocaleDateString("ru-RU");
            }
        };
    };
    ${'$'}.locale = function() {};
    function K(obj, pathArray) {
        if(!pathArray) return '';
        return pathArray.reduce((o, key) => (o && o[key] !== undefined) ? o[key] : '', obj);
    }
    const U = function(currentPage, pageCount) {
        return { 
            margin: [40, 0, 25, 0],
            columns: [
                { text: 'MYEDU ' + new Date().toLocaleDateString("ru-RU"), fontSize: 8 },
                { text: 'Страница ' + currentPage + ' из ' + pageCount, alignment: 'right', fontSize: 8 }
            ]
        };
    };
    const J = {
        textCenter: { alignment: 'center' },
        textRight: { alignment: 'right' },
        textLeft: { alignment: 'left' },
        fb: { bold: true },
        f7: { fontSize: 7 },
        f8: { fontSize: 8 },
        f9: { fontSize: 9 },
        f10: { fontSize: 10 },
        f11: { fontSize: 11 },
        l2: {}, 
        tableExample: { margin: [0, 5, 0, 15] }
    };

    // --- Injected Logic from Server ---
    try {
        ${res.logicCode}
    } catch(e) {
        AndroidBridge.log("Error injecting logic: " + e.message);
    }

    function startGeneration() {
        try {
            AndroidBridge.log("Calculating stats...");
            // Calculate stats (Credits/GPA) locally to pass to the generator
            let totalCredits = 0;
            let gpaSum = 0; 
            let gpaCount = 0;
            
            if (Array.isArray(transcriptData)) {
                transcriptData.forEach(year => {
                    if(year.semesters) {
                        year.semesters.forEach(sem => {
                            let sCred = 0; let sPoints = 0;
                            if(sem.subjects) {
                                sem.subjects.forEach(sub => {
                                    const cr = parseInt(sub.credit || 0);
                                    sCred += cr;
                                    totalCredits += cr;
                                    if(sub.exam_rule && sub.exam_rule.digital) {
                                        sPoints += (parseFloat(sub.exam_rule.digital) * cr);
                                    }
                                });
                            }
                            if(sCred > 0) sem.gpa = (Math.ceil((sPoints / sCred) * 100) / 100).toFixed(2);
                            else sem.gpa = 0;
                            
                            if(parseFloat(sem.gpa) > 0) {
                                gpaSum += parseFloat(sem.gpa);
                                gpaCount++;
                            }
                        });
                    }
                });
            }
            const avgGpa = gpaCount > 0 ? (Math.ceil((gpaSum / gpaCount) * 100) / 100).toFixed(2) : 0;
            const statsArray = [totalCredits, avgGpa, new Date().toLocaleDateString("ru-RU")];

            AndroidBridge.log("Finding generator function...");
            
            // Heuristic: Find the function in the global scope (window) that takes 4 arguments.
            // The original minified function usually looks like: function Y(C, a, c, d) { ... }
            // We iterate over all window properties.
            let generatorFunc = null;
            
            // 1. Try common minified names if the heuristic fails
            const potentialNames = ['Y', 'W', 'Z', 'K'];
            for(let name of potentialNames) {
                if (typeof window[name] === 'function' && window[name].length === 4) {
                    generatorFunc = window[name];
                    AndroidBridge.log("Found generator by name: " + name);
                    break;
                }
            }
            
            // 2. Search all global functions
            if (!generatorFunc) {
                for (let key in window) {
                    try {
                        if (typeof window[key] === 'function' && window[key].length === 4) {
                            // Check if it looks like our function (simple check)
                            // This is risky but better than nothing
                            if (key.length < 3) { // Minified names are short
                                generatorFunc = window[key];
                                AndroidBridge.log("Found generator by signature: " + key);
                                break;
                            }
                        }
                    } catch(e) {}
                }
            }

            if (!generatorFunc) {
                throw "Could not locate the PDF generation function. Code might have changed.";
            }

            AndroidBridge.log("Generating PDF...");
            const docDef = generatorFunc(transcriptData, studentInfo, statsArray, qrCodeUrl);
            
            pdfMake.createPdf(docDef).getBase64(function(encoded) {
                AndroidBridge.returnPdf(encoded);
            });

        } catch(e) {
            AndroidBridge.returnError(e.toString());
        }
    }
</script>
</body>
</html>
        """.trimIndent()
    }
}