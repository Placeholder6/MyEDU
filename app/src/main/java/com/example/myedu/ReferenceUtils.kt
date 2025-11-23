package com.example.myedu

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ReferenceJsFetcher {

    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"

    suspend fun fetchResources(logger: (String) -> Unit, language: String = "ru"): PdfResources = withContext(Dispatchers.IO) {
        try {
            // 1. Index
            logger("Fetching index.html...")
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""") 
                ?: throw Exception("Main JS missing")
            
            // 2. Main JS -> Find StudentDocuments
            logger("Fetching $mainJsName...")
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            val docsJsName = findMatch(mainJsContent, """["']\./(StudentDocuments\.[^"']+\.js)["']""") 
                ?: throw Exception("StudentDocuments JS missing")

            // 3. StudentDocuments JS -> Find References7
            logger("Fetching $docsJsName...")
            val docsJsContent = fetchString("$baseUrl/assets/$docsJsName")
            val refJsName = findMatch(docsJsContent, """["']\./(References7\.[^"']+\.js)["']""") 
                ?: throw Exception("References7 JS missing")

            // 4. References7 JS
            logger("Fetching $refJsName...")
            var refContent = fetchString("$baseUrl/assets/$refJsName")

            // 5. LINK MODULES
            val dependencies = StringBuilder()
            
            suspend fun linkModule(importRegex: Regex, exportRegex: Regex, finalVarName: String, fallbackValue: String) {
                var success = false
                try {
                    val fileNameMatch = importRegex.find(refContent) ?: importRegex.find(docsJsContent) ?: importRegex.find(mainJsContent)
                    if (fileNameMatch != null) {
                        val fileName = fileNameMatch.groupValues[1]
                        logger("Linking $finalVarName from $fileName")
                        var fileContent = fetchString("$baseUrl/assets/$fileName")
                        
                        val internalVarMatch = exportRegex.find(fileContent)
                        if (internalVarMatch != null) {
                            val internalVar = internalVarMatch.groupValues[1]
                            val cleanContent = fileContent.replace(Regex("""export\s*\{.*?\}"""), "")
                            dependencies.append("const $finalVarName = (() => {\n$cleanContent\nreturn $internalVar;\n})();\n")
                            success = true
                        }
                    }
                } catch (e: Exception) { logger("Link Error ($finalVarName): ${e.message}") }

                if (!success) dependencies.append("const $finalVarName = $fallbackValue;\n")
            }

            // Link Specific Dependencies for Reference (Form 8)
            linkModule(Regex("""from\s*["']\./(PdfStyle\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+P\s*\}"""), "J", "{}")
            linkModule(Regex("""from\s*["']\./(Signed\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+S\s*\}"""), "mt", "\"\"")
            linkModule(Regex("""from\s*["']\./(LicenseYear\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+L\s*\}"""), "Ly", "[]")
             linkModule(Regex("""from\s*["']\./(SpecialityLincense\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+S\s*\}"""), "Sl", "{}")
            linkModule(Regex("""from\s*["']\./(DocumentLink\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+D\s*\}"""), "Dl", "{}")
            linkModule(Regex("""from\s*["']\./(ru\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+r\s*\}"""), "T", "{}")

            // 6. CLEAN SCRIPT
            val importRegex = Regex("""import\s*\{(.*?)\}\s*from\s*['"].*?['"];?""")
            var cleanRef = refContent
                .replace(importRegex, "") 
                .replace(Regex("""export\s+default"""), "const ReferenceModule =")
                .replace(Regex("""export\s*\{.*?\}"""), "")

            // 7. EXPOSE
            val exposeCode = "\nwindow.PDFGeneratorRef = ReferenceModule;"

            val finalScript = dependencies.toString() + "\n" + cleanRef + exposeCode
            return@withContext PdfResources(finalScript)

        } catch (e: Exception) {
            logger("Ref Fetch Error: ${e.message}")
            e.printStackTrace()
            return@withContext PdfResources("")
        }
    }

    private fun fetchString(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            return response.body?.string() ?: ""
        }
    }

    private fun findMatch(content: String, regex: String): String? {
        return Regex(regex).find(content)?.let { match ->
            if (match.groupValues.size > 1) match.groupValues[1] else match.groupValues[0]
        }
    }
}

class ReferencePdfGenerator(private val context: Context) {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun generatePdf(
        studentInfoJson: String,
        licenseInfoJson: String,
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
                    logCallback("[JS Ref] ${cm.message()}")
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
                const licenseInfo = $licenseInfoJson;
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
                function startGeneration() {
                    try {
                        AndroidBridge.log("JS: Ref Driver started...");
                        
                        if (typeof window.PDFGeneratorRef !== 'function') throw "PDFGeneratorRef missing";
                        
                        const docDef = window.PDFGeneratorRef(studentInfo, licenseInfo, qrCodeUrl);
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