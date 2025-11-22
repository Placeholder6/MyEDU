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
                fun log(msg: String) = logCallback(msg)
            }, "AndroidBridge")

            val validStamp = if (resources.stampBase64.isNotEmpty()) resources.stampBase64 else "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"

            val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <script src="https://cdnjs.cloudflare.com/ajax/libs/pdfmake/0.2.7/pdfmake.min.js"></script>
                <script src="https://cdnjs.cloudflare.com/ajax/libs/pdfmake/0.2.7/vfs_fonts.js"></script>
            </head>
            <body>
            <script>
                window.onerror = function(msg, url, line) { AndroidBridge.returnError(msg + " @ Line " + line); };
                
                const studentInfo = $studentInfoJson;
                const transcriptData = $transcriptJson;
                const linkId = $linkId;
                const qrCodeUrl = "$qrUrl";
                const mt = "$validStamp";

                // MOCKS
                const ${'$'} = function(d) {
                    return { format: (f) => (d ? new Date(d) : new Date()).toLocaleDateString("ru-RU") };
                };
                ${'$'}.locale = function() {};
                function K(obj, pathArray) {
                    if(!pathArray) return '';
                    return pathArray.reduce((o, key) => (o && o[key] !== undefined) ? o[key] : '', obj);
                }
                const U = (cp, pc) => ({ margin: [40, 0, 25, 0], columns: [{ text: 'MYEDU ' + new Date().toLocaleDateString("ru-RU"), fontSize: 8 }, { text: 'Страница ' + cp + ' из ' + pc, alignment: 'right', fontSize: 8 }] });
                const J = { textCenter: { alignment: 'center' }, textRight: { alignment: 'right' }, textLeft: { alignment: 'left' }, fb: { bold: true }, f7: { fontSize: 7 }, f8: { fontSize: 8 }, f9: { fontSize: 9 }, f10: { fontSize: 10 }, f11: { fontSize: 11 }, l2: {}, tableExample: { margin: [0, 5, 0, 15] } };
            </script>
            <script>
                try {
                    ${resources.logicCode}
                    AndroidBridge.log("JS: Logic injected.");
                } catch(e) {
                    AndroidBridge.returnError("JS Injection Error: " + e.message);
                }
            </script>
            <script>
                function startGeneration() {
                    try {
                        AndroidBridge.log("JS: Driver starting...");
                        
                        // Stats Calculation
                        let totalCredits = 0, gpaSum = 0, gpaCount = 0;
                        if (Array.isArray(transcriptData)) {
                            transcriptData.forEach(y => {
                                if(y.semesters) y.semesters.forEach(s => {
                                    let sCred = 0, sPoints = 0;
                                    if(s.subjects) s.subjects.forEach(sub => {
                                        const cr = parseInt(sub.credit || 0);
                                        sCred += cr; totalCredits += cr;
                                        if(sub.exam_rule && sub.exam_rule.digital) sPoints += (parseFloat(sub.exam_rule.digital) * cr);
                                    });
                                    if(sCred > 0) s.gpa = (Math.ceil((sPoints / sCred) * 100) / 100).toFixed(2);
                                    else s.gpa = 0;
                                    if(parseFloat(s.gpa) > 0) { gpaSum += parseFloat(s.gpa); gpaCount++; }
                                });
                            });
                        }
                        const avgGpa = gpaCount > 0 ? (Math.ceil((gpaSum / gpaCount) * 100) / 100).toFixed(2) : 0;
                        const stats = [totalCredits, avgGpa, new Date().toLocaleDateString("ru-RU")];

                        if (typeof window.PDFGenerator !== 'function') {
                            throw "PDFGenerator is undefined. Logic extraction failed.";
                        }

                        AndroidBridge.log("JS: Calling PDFGenerator...");
                        const docDef = window.PDFGenerator(transcriptData, studentInfo, stats, qrCodeUrl);
                        
                        AndroidBridge.log("JS: creating PDF binary...");
                        pdfMake.createPdf(docDef).getBase64(b64 => AndroidBridge.returnPdf(b64));
                        
                    } catch(e) {
                        AndroidBridge.returnError("Driver Error: " + e.toString());
                    }
                }
            </script>
            </body>
            </html>
            """
            
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    webView.evaluateJavascript("startGeneration();", null)
                }
            }
            webView.loadDataWithBaseURL("https://myedu.oshsu.kg/", html, "text/html", "UTF-8", null)
        }
    }
}