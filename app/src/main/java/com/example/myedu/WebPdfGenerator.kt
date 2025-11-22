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

    interface PdfCallback {
        fun onPdfGenerated(base64: String)
        fun onError(error: String)
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun generatePdf(
        studentInfoJson: String,
        transcriptJson: String,
        linkId: Long,
        qrUrl: String
    ): ByteArray = suspendCancellableCoroutine { continuation ->
        
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            
            webView.addJavascriptInterface(object : Any() {
                @JavascriptInterface
                fun returnPdf(base64: String) {
                    try {
                        val cleanBase64 = base64.replace("data:application/pdf;base64,", "")
                        val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                        continuation.resume(bytes)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }

                @JavascriptInterface
                fun returnError(msg: String) {
                    continuation.resumeWithException(Exception("JS Error: $msg"))
                }
            }, "AndroidBridge")

            val htmlContent = getHtmlContent(studentInfoJson, transcriptJson, linkId, qrUrl)
            
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    webView.evaluateJavascript("startGeneration();", null)
                }
            }
            
            webView.loadDataWithBaseURL("https://myedu.oshsu.kg", htmlContent, "text/html", "UTF-8", null)
        }
    }

    private fun getHtmlContent(info: String, transcript: String, linkId: Long, qrUrl: String): String {
        return """
<!DOCTYPE html>
<html>
<head>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/pdfmake/0.2.7/pdfmake.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/pdfmake/0.2.7/vfs_fonts.js"></script>
</head>
<body>
<script>
    const studentInfo = $info;
    const transcriptData = $transcript;
    const linkId = $linkId;
    const qrCodeUrl = "$qrUrl";

    function K(obj, pathArray) {
        return pathArray.reduce((o, key) => (o && o[key] !== undefined) ? o[key] : '', obj);
    }

    function formatDate(dateStr) {
        if (!dateStr) return "";
        const d = new Date(dateStr);
        return d.toLocaleDateString("ru-RU");
    }
    const currentDate = new Date().toLocaleDateString("ru-RU");

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

    const yt = (C, a) => {
        let c = [];
        C.forEach((d, h) => {
            c.push({ margin: [0, 0, 0, 5], text: `Учебный год ${'$'}{d.edu_year}`, style: ["textCenter", "fb", "f10"] });
            d.semesters.forEach(o => {
                c.push({ margin: [0, 0, 0, 5], text: o.semester, style: ["textCenter", "fb", "f9"] });
                let n = [];
                n.push(a.map(i => ({ text: i.header, style: i.hStyle, fillColor: "#dddddd" })));
                o.subjects.forEach((i, b) => {
                    let g = [];
                    a.forEach(f => {
                        const D = f.header === "#" || f.header === "№" ? (b + 1).toString() : K(i, f.value.split("."));
                        g.push({ text: D, style: f.vStyle });
                    });
                    n.push(g);
                });
                const credits = o.subjects.reduce((i, b) => i + parseInt(b.credit || 0), 0);
                n.push([
                    { text: `Зарегистрировано кредитов - ${'$'}{credits}`, style: ["textCenter", "fb", "f9"], colSpan: 6, alignment: "right" }, 
                    {}, {}, {}, {}, {}, 
                    { text: `GPA: ${'$'}{o.gpa || 0}`, style: ["textCenter", "fb", "f9"], colSpan: 3, alignment: "center" }, 
                    {}, {}
                ]);
                c.push({ margin: [0, 0, 0, 5], style: ["tableExample", "f8"], table: { headerRows: 1, widths: [10, 40, "*", 30, 45, 25, 25, 20, 35], body: n } });
            });
        });
        return c;
    };

    const Y = (C, a, c, d) => {
        const o = yt(C, [
            { header: "№", value: "", hStyle: ["textCenter", "l2", "fb"], vStyle: ["textCenter"] },
            { header: "Б.Ч.", value: "code", hStyle: ["fb", "l2", "textCenter"], vStyle: "textCenter" },
            { header: "Дисциплины", value: "subject", hStyle: ["fb", "l2"], vStyle: "" },
            { header: "Кредит", value: "credit", hStyle: ["fb", "l2", "textCenter"], vStyle: "textCenter" },
            { header: "Форма контроля", value: "exam", hStyle: ["fb", "textCenter"], vStyle: "textCenter" },
            { header: "Баллы", value: "mark_list.total", hStyle: ["fb", "l2", "textCenter"], vStyle: "textCenter" },
            { header: "Цифр. экв.", value: "exam_rule.digital", hStyle: ["fb", "textCenter"], vStyle: "textCenter" },
            { header: "Букв.сист.", value: "exam_rule.alphabetic", hStyle: ["fb", "textCenter"], vStyle: "textCenter" },
            { header: "Трад. сист.", value: "exam_rule.word_ru", hStyle: ["fb", "textCenter"], vStyle: "textCenter" }
        ]);

        return {
            pageSize: "A4",
            pageOrientation: "portrait",
            footer: function(currentPage, pageCount) { 
                return { 
                    margin: [40, 0, 25, 0],
                    columns: [
                        { text: 'MYEDU ' + currentDate, fontSize: 8 },
                        { text: 'Страница ' + currentPage.toString() + ' из ' + pageCount, alignment: 'right', fontSize: 8 }
                    ]
                }; 
            },
            content: [
                { margin: [0, 0, 0, 5], text: "МИНИСТЕРСТВО НАУКИ, ВЫСШЕГО ОБРАЗОВАНИЯ И ИННОВАЦИЙ КЫРГЫЗСКОЙ РЕСПУБЛИКИ", style: ["f11", "textCenter"] },
                { margin: [0, 0, 0, 5], text: "ОШСКИЙ ГОСУДАРСТВЕННЫЙ УНИВЕРСИТЕТ", style: ["f11", "textCenter"] },
                { margin: [0, 0, 0, 5], text: a.faculty?.name_ru || "Факультет", style: ["f11", "fb", "textCenter"] },
                { margin: [0, 0, 0, 10], text: "ТРАНСКРИПТ", style: ["f11", "fb", "textCenter"] },
                {
                    margin: [0, 0, 0, 5],
                    columns: [
                        [
                            { margin: [0, 0, 0, 3], columns: [{ text: "ФИО:", width: 100, alignment: "left", style: ["f9", "fb"] }, { text: (a.last_name || "") + " " + (a.name || "") + " " + (a.father_name || ""), alignment: "left", style: ["f9", "fb"] }] },
                            { margin: [0, 0, 0, 3], columns: [{ text: "ID студента:", width: 100, alignment: "left", style: ["f9", "fb"] }, { text: a.id ? a.id.toString() : "", alignment: "left", style: ["f9", "fb"] }] },
                            { margin: [0, 0, 0, 3], columns: [{ text: "Дата рождения:", width: 100, alignment: "left", style: ["f9", "fb"] }, { text: formatDate(a.birthday), alignment: "left", style: ["f9", "fb"] }] },
                            { margin: [0, 0, 0, 3], columns: [{ text: "Направление:", width: 100, alignment: "left", style: ["f9", "fb"] }, { text: a.direction_code + ". " + a.direction_ru, alignment: "left", style: ["f9", "fb"] }] },
                            { margin: [0, 0, 0, 3], columns: [{ text: "Специальность:", width: 100, alignment: "left", style: ["f9", "fb"] }, { text: a.code + ". " + a.speciality_ru, alignment: "left", style: ["f9", "fb"] }] },
                            { margin: [0, 0, 0, 3], columns: [{ text: "Форма обучения:", width: 100, alignment: "left", style: ["f9", "fb"] }, { text: a.lastStudentMovement?.edu_form?.name_ru || "", alignment: "left", style: ["f9", "fb"] }] },
                            { margin: [0, 0, 0, 3], columns: [{ text: "Общий GPA:", width: 100, alignment: "left", style: ["f9", "fb"] }, { text: c[1].toString(), alignment: "left", style: ["f9", "fb"] }] },
                            { margin: [0, 0, 0, 10], text: `Всего зарегистрированых кредитов: ` + c[0], style: ["f9", "fb"] }
                        ],
                        { style: "textRight", width: 100, qr: d, foreground: "#000", background: "white", fit: "100" }
                    ]
                },
                ...o,
                { margin: [0, 0, 0, 0], text: "ПРИМЕЧАНИЕ: 1 кредит составляет 30 академических часов.", style: ["f7", "fb"] },
                { margin: [20, 30, 50, 0], text: "Достоверность документа можно проверить по QR-коду", style: ["f10", "textRight"] }
            ],
            styles: J,
            pageMargins: [40, 25, 25, 25]
        };
    };

    function processData() {
        let totalCredits = 0;
        transcriptData.forEach(year => {
            year.semesters.forEach(sem => {
                let semesterGradePoints = 0;
                let semesterCredits = 0;
                sem.subjects.forEach(sub => {
                    const credit = parseInt(sub.credit || 0);
                    sub.credit = credit;
                    totalCredits += credit;
                    if (sub.exam_rule && sub.mark_list && sub.exam && sub.exam.includes("Экзамен")) {
                        semesterCredits += credit;
                        const digital = parseFloat(sub.exam_rule.digital || 0);
                        semesterGradePoints += (digital * credit);
                    }
                });
                if (semesterCredits > 0) {
                    const rawGpa = semesterGradePoints / semesterCredits;
                    sem.gpa = Math.ceil(rawGpa * 100) / 100;
                } else {
                    sem.gpa = 0;
                }
            });
        });

        let gpaSum = 0;
        let gpaCount = 0;
        transcriptData.forEach(year => {
            year.semesters.forEach(sem => {
                if (sem.gpa && sem.gpa > 0) {
                    gpaSum += sem.gpa;
                    gpaCount++;
                }
            });
        });

        let overallGpa = 0;
        if (gpaCount > 0) {
            const rawAvg = gpaSum / gpaCount;
            overallGpa = Math.ceil(rawAvg * 100) / 100;
        }

        return [totalCredits, overallGpa, currentDate];
    }

    function startGeneration() {
        try {
            const stats = processData();
            const docDef = Y(transcriptData, studentInfo, stats, qrCodeUrl);
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