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
        resources: PdfResources // <--- Dynamic Resources
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
                    webView.evaluateJavascript("startGeneration();", null)
                }
            }
            
            webView.loadDataWithBaseURL("https://myedu.oshsu.kg", htmlContent, "text/html", "UTF-8", null)
        }
    }

    private fun getHtmlContent(info: String, transcript: String, linkId: Long, qrUrl: String, res: PdfResources): String {
        // Valid Stamp or Transparent Pixel
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
    // --- INPUT DATA ---
    const studentInfo = $info;
    const transcriptData = $transcript;
    const linkId = $linkId;
    const qrCodeUrl = "$qrUrl";
    
    // --- MOCKS (Dependencies used by fetched code) ---
    
    // Mock 'moment.js': $(date).format("L") -> DD.MM.YYYY
    const $ = function(d) {
        return {
            format: function(fmt) {
                const date = new Date(d);
                return date.toLocaleDateString("ru-RU");
            }
        };
    };
    // Add locale method to satisfy $.locale("ru", T) call if present
    $.locale = function() {};

    // Mock 'KeysValue.js': K(obj, ['path', 'to', 'val'])
    function K(obj, pathArray) {
        return pathArray.reduce((o, key) => (o && o[key] !== undefined) ? o[key] : '', obj);
    }

    // Mock 'PdfFooter4.js': U(page, total)
    const U = function(currentPage, pageCount) {
        return { 
            margin: [40, 0, 25, 0],
            columns: [
                { text: 'MYEDU ' + new Date().toLocaleDateString("ru-RU"), fontSize: 8 },
                { text: 'Страница ' + currentPage + ' из ' + pageCount, alignment: 'right', fontSize: 8 }
            ]
        };
    };

    // Mock 'Signed.js' (We inject the image here)
    const mt = "$validStamp";

    // Mock 'PdfStyle.js' (J)
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

    // --- INJECTED FETCHED LOGIC ---
    
    // 1. Table Generator Function (yt)
    ${res.tableGenCode}

    // 2. Document Definition Function (Y)
    ${res.docDefCode}

    // --- DRIVER LOGIC ---
    function startGeneration() {
        try {
            // 1. Calculate GPA (Recreating 'W' logic briefly here or relying on injected code if it was complete)
            // Since 'W' is inside setup(), we replicate the calculation:
            let totalCredits = 0;
            let gpaSum = 0; 
            let gpaCount = 0;
            
            transcriptData.forEach(year => {
                year.semesters.forEach(sem => {
                    let sCred = 0; let sPoints = 0;
                    sem.subjects.forEach(sub => {
                        const cr = parseInt(sub.credit || 0);
                        sCred += cr;
                        totalCredits += cr;
                        if(sub.exam_rule && sub.exam_rule.digital) {
                            sPoints += (parseFloat(sub.exam_rule.digital) * cr);
                        }
                    });
                    // Calc Sem GPA
                    if(sCred > 0) sem.gpa = (sPoints / sCred).toFixed(2);
                    else sem.gpa = 0;
                    
                    if(parseFloat(sem.gpa) > 0) {
                        gpaSum += parseFloat(sem.gpa);
                        gpaCount++;
                    }
                });
            });
            const avgGpa = gpaCount > 0 ? (gpaSum / gpaCount).toFixed(2) : 0;
            const stats = [totalCredits, avgGpa, new Date().toLocaleDateString("ru-RU")];

            // 2. Call the Extracted Function (Dynamic Name)
            // The fetched code defined: const ${res.docDefName} = ...
            // So we call window["${res.docDefName}"]
            const docFunc = eval("${res.docDefName}"); // Safest way to access var by string name in local scope
            
            const docDef = docFunc(transcriptData, studentInfo, stats, qrCodeUrl);
            
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