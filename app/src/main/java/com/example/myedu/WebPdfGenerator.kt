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
        language: String = "ru",
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

            val dateLocale = if (language == "en") "en-US" else "ru-RU"

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
                const lang = "$language";

                // Mock moment.js
                const ${'$'} = function(d) { return { format: (f) => (d ? new Date(d) : new Date()).toLocaleDateString("$dateLocale") }; };
                ${'$'}.locale = function() {};
            </script>

            <script>
                try {
                    ${resources.combinedScript}
                    AndroidBridge.log("JS: Scripts linked.");
                } catch(e) { AndroidBridge.returnError("Script Error: " + e.message); }
            </script>

            <script>
                // Helper to replace Russian phrases within strings (preserving codes)
                function translateString(str) {
                    if (!str) return str;
                    let s = str;
                    const replacements = {
                        "Лечебное дело": "General Medicine",
                        "Очное (специалитет)": "Full-time (Specialist)",
                        "Международный медицинский факультет": "International Medical Faculty",
                        "Экзамен": "Exam",
                        "Зачет": "Credit",
                        "Курсовая работа": "Coursework",
                        "Отлично": "Excellent",
                        "Хорошо": "Good",
                        "Удовл.": "Satisfactory",
                        "Удовлетворительно": "Satisfactory",
                        "Неудовл.": "Unsatisfactory",
                        "Зачтено": "Passed",
                        "Не зачтено": "Failed"
                    };
                    
                    for (const [key, value] of Object.entries(replacements)) {
                        // Global replace
                        s = s.split(key).join(value);
                    }
                    return s;
                }

                function translateData() {
                    if (lang !== "en") return;
                    
                    // 1. Translate Student Info Fields
                    ["faculty_ru", "direction_ru", "speciality_ru"].forEach(field => {
                        if (studentInfo[field]) studentInfo[field] = translateString(studentInfo[field]);
                    });
                    
                    if (studentInfo.lastStudentMovement && 
                        studentInfo.lastStudentMovement.edu_form && 
                        studentInfo.lastStudentMovement.edu_form.name_ru) {
                        studentInfo.lastStudentMovement.edu_form.name_ru = translateString(studentInfo.lastStudentMovement.edu_form.name_ru);
                    }

                    // 2. Translate Transcript Data
                    if(Array.isArray(transcriptData)) {
                        transcriptData.forEach(year => {
                            if(year.semesters) year.semesters.forEach(sem => {
                                // Format: "1-семестр" -> "Semester 1" (Handles spaces too)
                                if (sem.semester) {
                                    sem.semester = sem.semester.replace(/(\d+)\s*-\s*семестр/g, "Semester ${'$'}1");
                                }
                                
                                if(sem.subjects) sem.subjects.forEach(sub => {
                                    if(sub.exam) sub.exam = translateString(sub.exam);
                                    if(sub.exam_rule && sub.exam_rule.word_ru) {
                                        sub.exam_rule.word_ru = translateString(sub.exam_rule.word_ru);
                                    }
                                });
                            });
                        });
                    }
                }

                function startGeneration() {
                    try {
                        AndroidBridge.log("JS: Driver started (" + lang + ")...");
                        
                        // TRANSLATE
                        translateData();

                        // CALC STATS
                        let totalCredits = 0, yearlyGpas = [];

                        if (Array.isArray(transcriptData)) {
                            transcriptData.forEach(year => {
                                let semGpas = [];
                                if (year.semesters) {
                                    year.semesters.forEach(sem => {
                                        // Sum Credits
                                        if (sem.subjects) {
                                            sem.subjects.forEach(sub => {
                                                totalCredits += (Number(sub.credit) || 0);
                                                if (sub.exam_rule && sub.exam_rule.digital) {
                                                    sub.exam_rule.digital = Math.ceil(Number(sub.exam_rule.digital) * 100) / 100;
                                                }
                                            });
                                        }
                                        
                                        // Filter Exams (Keyword depends on lang)
                                        const keyword = lang === "en" ? "Exam" : "Экзамен";
                                        const exams = sem.subjects ? sem.subjects.filter(r => 
                                            r.exam_rule && r.mark_list && r.exam && r.exam.includes(keyword)
                                        ) : [];

                                        // Calc GPA
                                        const examCredits = exams.reduce((acc, curr) => acc + (Number(curr.credit)||0), 0);
                                        if (exams.length > 0 && examCredits > 0) {
                                            const weightedSum = exams.reduce((acc, curr) => acc + (curr.exam_rule.digital * (Number(curr.credit)||0)), 0);
                                            sem.gpa = Math.ceil((weightedSum / examCredits) * 100) / 100;
                                            semGpas.push(sem.gpa);
                                        } else {
                                            sem.gpa = 0;
                                        }
                                    });
                                }
                                const validSems = semGpas.filter(g => g > 0);
                                if (validSems.length > 0) yearlyGpas.push(validSems.reduce((a, b) => a + b, 0) / validSems.length);
                            });
                        }

                        // Final GPA
                        let cumulativeGpa = 0;
                        if (yearlyGpas.length > 0) {
                            const rawAvg = yearlyGpas.reduce((a, b) => a + b, 0) / yearlyGpas.length;
                            cumulativeGpa = Math.ceil(rawAvg * 100) / 100;
                        }
                        
                        const stats = [totalCredits, cumulativeGpa, new Date().toLocaleDateString("$dateLocale")];

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