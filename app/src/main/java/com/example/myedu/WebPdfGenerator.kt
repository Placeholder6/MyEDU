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
                    logCallback("[JS] ${cm.message()}")
                    return true
                }
            }

            webView.addJavascriptInterface(object : Any() {
                @JavascriptInterface
                fun returnPdf(base64: String) {
                    try {
                        val clean = base64.replace("data:application/pdf;base64,", "")
                        val bytes = Base64.decode(clean, Base64.DEFAULT)
                        if (continuation.isActive) continuation.resume(bytes)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }

                @JavascriptInterface
                fun returnError(msg: String) {
                    if (continuation.isActive) continuation.resumeWithException(Exception("JS: $msg"))
                }
                
                @JavascriptInterface
                fun log(msg: String) = logCallback(msg)
            }, "AndroidBridge")

            val fallbackPng = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAACklEQVR4nGMAAQAABQABDQottAAAAABJRU5ErkJggg=="
            val safeStamp = if (resources.stampBase64.startsWith("data:image")) resources.stampBase64 else fallbackPng

            val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <script src="https://cdnjs.cloudflare.com/ajax/libs/pdfmake/0.2.7/pdfmake.min.js"></script>
                <script src="https://cdnjs.cloudflare.com/ajax/libs/pdfmake/0.2.7/vfs_fonts.js"></script>
            </head>
            <body>
            <script>
                window.onerror = function(msg, url, line) { AndroidBridge.returnError(msg + " @ " + line); };
                
                const studentInfo = $studentInfoJson;
                let transcriptData = $transcriptJson; 
                const linkId = $linkId;
                const qrCodeUrl = "$qrUrl";
                const mt = "$safeStamp"; 

                // --- REAL LOGIC FROM UPLOADED FILES ---

                // from helpers.js (Name Formatter)
                const ct = (...e) => {
                    let t = "";
                    e.forEach(a => {
                        // Check for "null" string and undefined
                        if (a && a !== "null") t += a + " ";
                    });
                    return t.trim();
                };

                // from PdfStyle.js
                const J = {
                    header: { fontSize: 18, bold: true, margin: [0, 0, 0, 10] },
                    subheader: { fontSize: 16, bold: true, margin: [0, 10, 0, 5] },
                    tableExample: { margin: [0, 5, 0, 20] },
                    tableHeader: { bold: true, fontSize: 13, color: "black" },
                    textLeft: { alignment: "left" },
                    textRight: { alignment: "right" },
                    quote: { italics: true },
                    fb: { bold: true },
                    f0: { fontSize: 0 }, f2: { fontSize: 2 }, f4: { fontSize: 4 }, f6: { fontSize: 6 }, f65: { fontSize: 6.5 },
                    f7: { fontSize: 7 }, f8: { fontSize: 8 }, f9: { fontSize: 9 }, f10: { fontSize: 10 }, f11: { fontSize: 11 },
                    f12: { fontSize: 12 }, f13: { fontSize: 13 }, f14: { fontSize: 14 }, f15: { fontSize: 15 }, f16: { fontSize: 16 },
                    f18: { fontSize: 18 }, f20: { fontSize: 20 }, f22: { fontSize: 22 }, f24: { fontSize: 23 },
                    c1: { color: "#000" }, c2: { color: "#222" }, c3: { color: "#444" }, c4: { color: "#666" },
                    c5: { color: "#888" }, c6: { color: "#aaa" }, c7: { color: "#ccc" }, c8: { color: "#eee" },
                    small: { fontSize: 8 },
                    footLTitle: { color: "#333", fontSize: 8, margin: [40, 0, 0, 0] },
                    footRTitle: { color: "#333", fontSize: 8, margin: [0, 0, 40, 0], alignment: "right" },
                    textCenter: { alignment: "center" },
                    textJustify: { alignment: "justify" },
                    italic: { italics: true },
                    l2: { margin: [0, 5, 0, 0] },
                    px1: { margin: [5, 0, 5, 0] },
                    px2: { margin: [10, 0, 10, 0] },
                    colorFFF: { color: "#fff" }
                };

                // from PdfFooter4.js
                const U = (t, e) => ({
                    columns: [
                        { text: `MYEDU ${'$'}{new Date().toLocaleDateString()}`, style: ["footLTitle"] },
                        { text: `Страница ${'$'}{t.toString()} из ${'$'}{e}`, style: ["footRTitle"] }
                    ],
                    margin: [0, 0, -15, 0]
                });

                // from KeysValue.js
                const K = (n, e) => e.length == 0 ? n : n[e[0]] != null ? K(n[e[0]], e.slice(1)) : "";

                // Basic Mocks
                const ${'$'} = function(d) { return { format: (f) => (d ? new Date(d) : new Date()).toLocaleDateString("ru-RU") }; };
                ${'$'}.locale = function() {};

            </script>
            <script>
                try {
                    ${resources.logicCode}
                } catch(e) { AndroidBridge.returnError("Injection: " + e.message); }
            </script>
            <script>
                function startGeneration() {
                    try {
                        AndroidBridge.log("JS: Driver started.");

                        let totalCredits = 0, gpaSum = 0, gpaCount = 0;
                        
                        if (Array.isArray(transcriptData)) {
                            transcriptData.forEach(y => {
                                if(y.semesters) y.semesters.forEach(s => {
                                    let sCred = 0, sPoints = 0;
                                    if(s.subjects) s.subjects.forEach(sub => {
                                        const cr = parseInt(sub.credit || 0);
                                        sCred += cr; totalCredits += cr;
                                        
                                        // FIX EXCESS ZEROS
                                        if(sub.exam_rule && sub.exam_rule.digital) {
                                            let dig = parseFloat(sub.exam_rule.digital);
                                            sub.exam_rule.digital = dig.toFixed(2); 
                                            sPoints += (dig * cr);
                                        }
                                        if(sub.mark_list && sub.mark_list.total) {
                                            let tot = parseFloat(sub.mark_list.total);
                                            sub.mark_list.total = Math.round(tot).toString(); 
                                        }
                                    });
                                    
                                    if(sCred > 0) s.gpa = (Math.ceil((sPoints / sCred) * 100) / 100).toFixed(2);
                                    else s.gpa = 0;
                                    
                                    if(parseFloat(s.gpa) > 0) { gpaSum += parseFloat(s.gpa); gpaCount++; }
                                });
                            });
                        }
                        const avgGpa = gpaCount > 0 ? (Math.ceil((gpaSum / gpaCount) * 100) / 100).toFixed(2) : 0;
                        const stats = [totalCredits, avgGpa, new Date().toLocaleDateString("ru-RU")];

                        if (typeof window.PDFGenerator !== 'function') throw "PDFGenerator missing";

                        AndroidBridge.log("JS: Generating...");
                        const docDef = window.PDFGenerator(transcriptData, studentInfo, stats, qrCodeUrl);
                        
                        pdfMake.createPdf(docDef).getBase64(b64 => AndroidBridge.returnPdf(b64));
                    } catch(e) {
                        AndroidBridge.returnError("Driver: " + e.toString());
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