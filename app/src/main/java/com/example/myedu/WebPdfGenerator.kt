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
        resources: PdfResources,
        logCallback: (String) -> Unit
    ): ByteArray = suspendCancellableCoroutine { continuation ->
        
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            
            // Capture JS Console Logs
            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                    logCallback("[JS] ${cm.message()} (Line ${cm.lineNumber()})")
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
                    logCallback(msg)
                }
            }, "AndroidBridge")

            val htmlContent = getHtmlContent(studentInfoJson, transcriptJson, linkId, qrUrl, resources)
            
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    webView.evaluateJavascript("startGeneration();", null)
                }
            }
            
            // Load with correct base URL
            webView.loadDataWithBaseURL("https://myedu.oshsu.kg/", htmlContent, "text/html", "UTF-8", null)
        }
    }

    private fun getHtmlContent(info: String, transcript: String, linkId: Long, qrUrl: String, res: PdfResources): String {
        // Fallback if stamp is missing
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
    // 1. Global Error Handler
    window.onerror = function(message, source, lineno, colno, error) {
        AndroidBridge.returnError(message + " at line " + lineno);
    };

    // 2. Variables
    const studentInfo = $info;
    const transcriptData = $transcript;
    const linkId = $linkId;
    const qrCodeUrl = "$qrUrl";
    const mt = "$validStamp"; // 'mt' is typically used for the signed image

    // 3. MOCKS (Crucial for preventing crashes when imports are removed)
    
    // Mock Vue's date formatter ($)
    const ${'$'} = function(d) {
        return {
            format: function(fmt) {
                const date = d ? new Date(d) : new Date();
                return date.toLocaleDateString("ru-RU");
            }
        };
    };
    ${'$'}.locale = function() {};

    // Mock Helper 'K' (deep access)
    function K(obj, pathArray) {
        if(!pathArray) return '';
        return pathArray.reduce((o, key) => (o && o[key] !== undefined) ? o[key] : '', obj);
    }

    // Mock Footer 'U'
    const U = function(currentPage, pageCount) {
        return { 
            margin: [40, 0, 25, 0],
            columns: [
                { text: 'MYEDU ' + new Date().toLocaleDateString("ru-RU"), fontSize: 8 },
                { text: 'Страница ' + currentPage + ' из ' + pageCount, alignment: 'right', fontSize: 8 }
            ]
        };
    };

    // Mock Styles 'J'
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

    // 4. INJECT SERVER LOGIC
    try {
        ${res.logicCode}
    } catch(e) {
        AndroidBridge.returnError("Logic Injection Failed: " + e.message);
    }

    // 5. DRIVER CODE
    function startGeneration() {
        try {
            AndroidBridge.log("JS: Calculating stats...");
            
            // Calculate GPA/Credits (Frontend logic)
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

            // FIND THE GENERATOR FUNCTION
            // We look for a function in the global scope that accepts exactly 4 arguments.
            // Original signature: (data, student, stats, qr)
            let generatorFunc = null;
            
            // 1. Check for common minified names (Y is in your file)
            if (typeof window['Y'] === 'function' && window['Y'].length === 4) {
                 generatorFunc = window['Y'];
            }
            
            // 2. Scan window if Y isn't found
            if (!generatorFunc) {
                for (let key in window) {
                    try {
                        if (typeof window[key] === 'function' && window[key].length === 4) {
                            // Ignore standard functions
                            if (key !== 'setTimeout' && key !== 'setInterval' && key !== 'alert' && key.length < 5) {
                                generatorFunc = window[key];
                                AndroidBridge.log("JS: Found candidate function: " + key);
                                break;
                            }
                        }
                    } catch(e) {}
                }
            }

            if (!generatorFunc) throw "Generator function not found in scripts.";

            AndroidBridge.log("JS: Creating PDF definition...");
            const docDef = generatorFunc(transcriptData, studentInfo, statsArray, qrCodeUrl);
            
            AndroidBridge.log("JS: Generating PDF binary...");
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