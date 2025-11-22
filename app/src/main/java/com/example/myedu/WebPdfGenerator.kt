package com.example.myedu

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.webkit.JavascriptInterface
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
        
        // WebView must be created on the main thread
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            
            // Bridge to get result back from JS
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
            }, "AndroidBridge")

            val htmlContent = getHtmlContent(studentInfoJson, transcriptJson, linkId, qrUrl, resources)
            
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Trigger the generation once libraries are loaded
                    webView.evaluateJavascript("startGeneration();", null)
                }
            }
            
            // Load the constructed HTML
            webView.loadDataWithBaseURL("https://myedu.oshsu.kg", htmlContent, "text/html", "UTF-8", null)
        }
    }

    private fun getHtmlContent(info: String, transcript: String, linkId: Long, qrUrl: String, res: PdfResources): String {
        // Use empty pixel if stamp fetch failed
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
    // --- 1. INJECTED DATA ---
    const studentInfo = $info;
    const transcriptData = $transcript;
    const linkId = $linkId;
    const qrCodeUrl = "$qrUrl";
    const mt = "$validStamp"; // 'mt' is often the variable name for the stamp in the minified code

    // --- 2. MOCKS ---
    // The site's JS likely expects a specific environment (Vue/plugins). 
    // We mock essential functions used in the transcript logic.
    
    // Mocking Vue's '$' utility often used for formatting dates
    const ${'$'} = function(d) {
        return {
            format: function(fmt) {
                const date = d ? new Date(d) : new Date();
                return date.toLocaleDateString("ru-RU");
            }
        };
    };
    ${'$'}.locale = function() {};

    // Helper to safely access nested object properties (often used in minified code)
    function K(obj, pathArray) {
        return pathArray.reduce((o, key) => (o && o[key] !== undefined) ? o[key] : '', obj);
    }

    // Mocking 'U': Likely a function creating the Header/Footer layout
    const U = function(currentPage, pageCount) {
        return { 
            margin: [40, 0, 25, 0],
            columns: [
                { text: 'MYEDU ' + new Date().toLocaleDateString("ru-RU"), fontSize: 8 },
                { text: 'Страница ' + currentPage + ' из ' + pageCount, alignment: 'right', fontSize: 8 }
            ]
        };
    };

    // Mocking 'J': Likely a style definition object
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

    // --- 3. INJECTED LOGIC ---
    // This is the code we scraped from Transcript.js
    ${res.logicCode}

    // --- 4. DRIVER FUNCTION ---
    function startGeneration() {
        try {
            // Re-calculate necessary stats that the frontend usually does before generating PDF
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
                            // Calculate Semester GPA if not present
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
            
            // The main function usually expects: (Data, StudentInfo, StatsArray, QRCodeUrl)
            // We try to execute the function name scraped by JsResourceFetcher
            // If that failed, 'Y' is a common minified name, but this is brittle.
            const funcName = "${res.mainFuncName}";
            
            if (typeof window[funcName] !== 'function') {
                 // Try to find the function in the window scope if the name is wrong
                 // This is a fallback to find the likely PDF generator function
                 const possibleKeys = Object.keys(window).filter(k => typeof window[k] === 'function' && window[k].length === 4);
                 if(possibleKeys.length > 0) {
                     // Use the first 4-argument function found that matches our expected signature
                     var docDef = window[possibleKeys[0]](transcriptData, studentInfo, [totalCredits, avgGpa, new Date().toLocaleDateString("ru-RU")], qrCodeUrl);
                 } else {
                     throw "Could not find PDF generation function: " + funcName;
                 }
            } else {
                 var docDef = window[funcName](transcriptData, studentInfo, [totalCredits, avgGpa, new Date().toLocaleDateString("ru-RU")], qrCodeUrl);
            }
            
            // Generate PDF
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