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
    
    // Extracted from Signed.53e0088b.1762755934747.js
    const signedImage = "data:image/jpeg;base64,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";

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
                { 
                    margin: [40, 0, 40, 0], 
                    columns: [
                        [{ margin: [20, 30, 20, 20], text: "Достоверность данного документа можно проверить отсканировав QR-код", style: ["f11", ""] }], 
                        { image: signedImage, width: 90 }
                    ] 
                },
                { margin: [20, 0, 50, 0], text: `${'$'}{c[2]}`, style: ["f10", "textRight"] }
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
                    sem.gpa = (Math.ceil(rawGpa * 100) / 100).toFixed(2);
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
                    gpaSum += parseFloat(sem.gpa);
                    gpaCount++;
                }
            });
        });

        let overallGpa = "0.00";
        if (gpaCount > 0) {
            const rawAvg = gpaSum / gpaCount;
            overallGpa = (Math.ceil(rawAvg * 100) / 100).toFixed(2);
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