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
                let transcriptData = $transcriptJson; 
                const linkId = $linkId;
                const qrCodeUrl = "$qrUrl";

                // Mock moment.js (Only missing dep)
                const ${'$'} = function(d) { return { format: (f) => (d ? new Date(d) : new Date()).toLocaleDateString("ru-RU") }; };
                ${'$'}.locale = function() {};
            </script>

            <script>
                try {
                    ${resources.combinedScript}
                    AndroidBridge.log("JS: Scripts linked.");
                } catch(e) { AndroidBridge.returnError("Script Error: " + e.message); }
            </script>

            <script>
                function startGeneration() {
                    try {
                        AndroidBridge.log("JS: Driver running...");
                        
                        // --- EXACT GPA CALCULATION ---
                        let totalCredits = 0;
                        let yearlyGpas = [];

                        if (Array.isArray(transcriptData)) {
                            transcriptData.forEach(year => {
                                let semGpas = [];
                                if (year.semesters) {
                                    year.semesters.forEach(sem => {
                                        // 1. Sum Credits
                                        if (sem.subjects) {
                                            sem.subjects.forEach(sub => {
                                                totalCredits += (Number(sub.credit) || 0);
                                                if (sub.exam_rule && sub.exam_rule.digital) {
                                                    sub.exam_rule.digital = Math.ceil(Number(sub.exam_rule.digital) * 100) / 100;
                                                }
                                            });
                                        }

                                        // 2. Filter Exams
                                        const exams = sem.subjects ? sem.subjects.filter(r => 
                                            r.exam_rule && r.mark_list && r.exam && r.exam.includes("Экзамен")
                                        ) : [];

                                        // 3. Calc Semester GPA
                                        const examCredits = exams.reduce((acc, curr) => acc + (Number(curr.credit)||0), 0);
                                        if (exams.length > 0 && examCredits > 0) {
                                            const weightedSum = exams.reduce((acc, curr) => acc + (curr.exam_rule.digital * (Number(curr.credit)||0)), 0);
                                            const rawGpa = weightedSum / examCredits;
                                            sem.gpa = Math.ceil(rawGpa * 100) / 100;
                                            semGpas.push(sem.gpa);
                                        } else {
                                            sem.gpa = 0;
                                        }
                                    });
                                }
                                // 4. Year GPA
                                const validSems = semGpas.filter(g => g > 0);
                                if (validSems.length > 0) {
                                    yearlyGpas.push(validSems.reduce((a, b) => a + b, 0) / validSems.length);
                                }
                            });
                        }

                        // 5. Final GPA
                        let cumulativeGpa = 0;
                        if (yearlyGpas.length > 0) {
                            const rawAvg = yearlyGpas.reduce((a, b) => a + b, 0) / yearlyGpas.length;
                            cumulativeGpa = Math.ceil(rawAvg * 100) / 100;
                        }
                        
                        const stats = [totalCredits, cumulativeGpa, new Date().toLocaleDateString("ru-RU")];

                        if (typeof window.PDFGenerator !== 'function') throw "PDFGenerator missing";

                        const docDef = window.PDFGenerator(transcriptData, studentInfo, stats, qrCodeUrl);
                        pdfMake.createPdf(docDef).getBase64(b64 => AndroidBridge.returnPdf(b64));
                        
                    } catch(e) { AndroidBridge.returnError("Driver: " + e.toString()); }
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