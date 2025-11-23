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
            
            // 2. Main JS
            logger("Fetching Main JS...")
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            
            // 3. Find References7
            var refJsPath = findMatch(mainJsContent, """["']([^"']*References7\.[^"']+\.js)["']""")
            if (refJsPath == null) {
                logger("Searching StudentDocuments...")
                val docsJsPath = findMatch(mainJsContent, """["']([^"']*StudentDocuments\.[^"']+\.js)["']""")
                if (docsJsPath != null) {
                    val docsJsContent = fetchString("$baseUrl/assets/${getName(docsJsPath)}")
                    refJsPath = findMatch(docsJsContent, """["']([^"']*References7\.[^"']+\.js)["']""")
                }
            }
            if (refJsPath == null) throw Exception("References7 JS missing")
            val refJsName = getName(refJsPath)

            // 4. Fetch References7 JS
            logger("Fetching $refJsName...")
            val refContent = fetchString("$baseUrl/assets/$refJsName")

            // 5. LINK MODULES
            val dependencies = StringBuilder()
            val linkedVars = mutableSetOf<String>()

            suspend fun linkModule(fileKeyword: String, exportChar: String, fallbackName: String, fallbackValue: String) {
                // 1. Detect Variable Name (e.g. import { S as et } -> "et")
                var varName: String? = null
                
                if (exportChar == "DEFAULT_OR_NAMED") {
                     val regex = Regex("""import\s*\{\s*(\w+)\s*\}\s*from\s*['"][^'"]*$fileKeyword[^'"]*['"]""")
                     varName = findMatch(refContent, regex.pattern)
                } else {
                    val regex = Regex("""import\s*\{\s*$exportChar\s+as\s+(\w+)\s*\}\s*from\s*['"][^'"]*$fileKeyword[^'"]*['"]""")
                    varName = findMatch(refContent, regex.pattern)
                }

                if (varName == null) {
                    // If not used in the main script, we might not need to link it, but we fallback to be safe
                    varName = fallbackName
                } else {
                    logger("✅ Linked $fileKeyword as '$varName'")
                }
                linkedVars.add(varName)

                // 2. Fetch & Inject File
                val fileUrlRegex = Regex("""["']([^"']*$fileKeyword\.[^"']+\.js)["']""")
                val fileNameMatch = fileUrlRegex.find(refContent) ?: fileUrlRegex.find(mainJsContent)
                
                var success = false
                if (fileNameMatch != null) {
                    val fileName = getName(fileNameMatch.groupValues[1])
                    try {
                        val fileContent = fetchString("$baseUrl/assets/$fileName")
                        
                        val exportRegex = if (exportChar == "DEFAULT_OR_NAMED") 
                            Regex("""export\s*\{\s*(\w+)\s*\}""") 
                        else 
                            Regex("""export\s*\{\s*(\w+)\s+as\s+$exportChar\s*\}""") 
                            
                        val internalVarMatch = exportRegex.find(fileContent)
                        
                        if (internalVarMatch != null) {
                            val internalVar = internalVarMatch.groupValues[1]
                            val cleanContent = cleanJsContent(fileContent)
                            dependencies.append("var $varName = (() => {\n$cleanContent\nreturn $internalVar;\n})();\n")
                            success = true
                        }
                    } catch (e: Exception) { logger("Fetch Error $fileName: ${e.message}") }
                }

                if (!success) dependencies.append("var $varName = $fallbackValue;\n")
            }

            // --- LINK CONFIGURATION ---
            linkModule("PdfStyle", "P", "PdfStyle_Fallback", "{}")          
            linkModule("Signed", "S", "Signed_Fallback", "\"\"")            
            linkModule("SpecialityLincense", "S", "SpecLic_Fallback", "{}") 
            linkModule("DocumentLink", "DEFAULT_OR_NAMED", "DocLink_Fallback", "{}") 
            linkModule("ru", "r", "Ru_Fallback", "{}")                      
            // Skip LicenseYear (side-effect only) or others if not strictly imported with variables

            // 6. MOCK OTHER IMPORTS
            val varsToMock = mutableSetOf<String>()
            val genericImportRegex = Regex("""import\s*\{(.*?)\}\s*from\s*['"].*?['"];?""")
            genericImportRegex.findAll(refContent).forEach { match ->
                match.groupValues[1].split(",").forEach { item ->
                    val parts = item.trim().split(Regex("""\s+as\s+"""))
                    val name = if (parts.size == 2) parts[1] else parts[0]
                    if (name.isNotBlank()) varsToMock.add(name.trim())
                }
            }
            
            varsToMock.removeAll(linkedVars)
            varsToMock.remove("$") // Prevent conflict with HTML mock

            val dummyScript = StringBuilder()
            dummyScript.append("const UniversalDummy = new Proxy(function(){}, { get: () => UniversalDummy, apply: () => UniversalDummy, construct: () => UniversalDummy });\n")
            if (varsToMock.isNotEmpty()) {
                dummyScript.append("var ")
                dummyScript.append(varsToMock.joinToString(",") { "$it = UniversalDummy" })
                dummyScript.append(";\n")
            }

            // 7. DETECT MAIN EXPORT NAME
            // We look for: export { Nt as default }
            var mainExportName = "UniversalDummy"
            val exportDefaultRegex = Regex("""export\s*\{\s*(\w+)\s+as\s+default\s*\}""")
            val exportMatch = exportDefaultRegex.find(refContent)
            if (exportMatch != null) {
                mainExportName = exportMatch.groupValues[1]
                logger("Found Main Export: $mainExportName")
            }

            // 8. CLEAN MAIN SCRIPT
            val cleanRef = cleanJsContent(refContent)
            
            // 9. EXPOSE TO WINDOW
            val exposeCode = "\nwindow.PDFGeneratorRef = $mainExportName;"

            val finalScript = dummyScript.toString() + dependencies.toString() + "\n(() => {\n" + cleanRef + exposeCode + "\n})();"
            
            return@withContext PdfResources(finalScript)

        } catch (e: Exception) {
            logger("Ref Fetch Error: ${e.message}")
            e.printStackTrace()
            return@withContext PdfResources("")
        }
    }

    private fun cleanJsContent(content: String): String {
        return content
            .replace(Regex("""import\s*\{[^}]*\}\s*from\s*['"][^'"]+['"];?""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""import\s+[\w*]+\s+(?:as\s+\w+\s+)?from\s*['"][^'"]+['"];?"""), "")
            .replace(Regex("""import\s*['"][^'"]+['"];?"""), "")
            .replace(Regex("""export\s*\{[^}]*\}""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""export\s+default\s+"""), "")
    }

    private fun getName(path: String): String = path.split('/').last()

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
    ): ByteArray = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        
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
                    AndroidBridge.log("JS: Scripts loaded.");
                } catch(e) { AndroidBridge.returnError("Script Error: " + e.message); }
            </script>

            <script>
                function startGeneration() {
                    try {
                        AndroidBridge.log("JS: Ref Driver started...");
                        
                        if (typeof window.PDFGeneratorRef !== 'function') {
                             // Fallback: check if it's a Vue object or default export
                             // Sometimes the export is the Vue component itself.
                             // We might need to manually run the logic if it is a Vue setup function.
                             throw "PDFGeneratorRef missing or not a function";
                        }

                        // IMPORTANT: The References7 script is a Vue Component (defineComponent({ setup()... })).
                        // It does NOT expose a direct generation function like Transcript.js did.
                        // We must extract the logic or mock the Vue setup() execution.
                        
                        // Heuristic: The script likely returns a Vue component definition object.
                        // We need to run its 'setup()' function to get the generation logic.
                        
                        const component = window.PDFGeneratorRef;
                        if (component && component.setup) {
                           // Mock Vue Composition API hooks
                           const props = {};
                           
                           // We need to capture the PDF generation function returned by setup()
                           // setup() returns bindings. We look for 'F' (based on source: const F=async()=>{...})
                           
                           // Mock Vue hooks
                           const mockRef = (val) => ({ value: val });
                           const mockUseStore = () => ({ student: { studentMovement: studentInfo.lastStudentMovement } });
                           
                           // We can't easily execute the setup() because it relies on imports we mocked (ref, computed, etc).
                           // However, we have replaced imports 'w' (ref), 'u' (ref), 'z' (store) with UniversalDummy.
                           // The script WILL fail inside setup() if we don't provide working mocks for ref/useStore.
                           
                           // ALTERNATIVE STRATEGY:
                           // Instead of running the script, we recreate the PDF generation logic here in JS.
                           // The logic is simple: call createPdf with specific content.
                           // See at() function in source.
                           
                           throw "Direct execution of Vue component not supported yet. Logic needs extraction.";
                        }
                        
                        // If we reached here, we might be stuck. 
                        // But wait! The user provided the source of References7.js.
                        // It defines 'at' function which builds the DD (Document Definition).
                        // We can just regex-extract that 'at' function and run it!
                        
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