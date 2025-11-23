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

class ReferenceJsFetcher {

    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"

    suspend fun fetchResources(logger: (String) -> Unit, language: String = "ru"): PdfResources = withContext(Dispatchers.IO) {
        try {
            // 1. Find Main Script to locate References7
            logger("Fetching index...")
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""") ?: throw Exception("Main JS missing")
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            
            // 2. Find References7 path
            var refJsPath = findMatch(mainJsContent, """["']([^"']*References7\.[^"']+\.js)["']""")
            if (refJsPath == null) {
                // Try finding via StudentDocuments if lazy-loaded
                val docsJsPath = findMatch(mainJsContent, """["']([^"']*StudentDocuments\.[^"']+\.js)["']""")
                if (docsJsPath != null) {
                    val docsContent = fetchString("$baseUrl/assets/${getName(docsJsPath)}")
                    refJsPath = findMatch(docsContent, """["']([^"']*References7\.[^"']+\.js)["']""")
                }
            }
            if (refJsPath == null) throw Exception("References7.js not found")
            val refJsName = getName(refJsPath)
            
            logger("Fetching $refJsName...")
            val refContent = fetchString("$baseUrl/assets/$refJsName")

            // 3. LINK DEPENDENCIES (Dynamic Name Resolution)
            val dependencies = StringBuilder()
            val linkedVars = mutableSetOf<String>()

            suspend fun linkModule(keyword: String, exportChar: String, fallback: String, defaultVal: String) {
                // Regex to find what variable name the script uses: import { S as et } from ...
                var varName: String? = null
                if (exportChar == "DEFAULT") {
                    val r = Regex("""import\s*\{\s*(\w+)\s*\}\s*from\s*['"][^'"]*$keyword[^'"]*['"]""")
                    varName = findMatch(refContent, r.pattern)
                } else {
                    val r = Regex("""import\s*\{\s*$exportChar\s+as\s+(\w+)\s*\}\s*from\s*['"][^'"]*$keyword[^'"]*['"]""")
                    varName = findMatch(refContent, r.pattern)
                }
                
                val name = varName ?: fallback
                linkedVars.add(name)

                // Fetch file content
                val fileRegex = Regex("""["']([^"']*$keyword\.[^"']+\.js)["']""")
                val pathMatch = fileRegex.find(refContent) ?: fileRegex.find(mainJsContent)
                
                if (pathMatch != null) {
                    try {
                        val fName = getName(pathMatch.groupValues[1])
                        val fContent = fetchString("$baseUrl/assets/$fName")
                        // Extract the export body
                        val exportRegex = if(exportChar == "DEFAULT") Regex("""export\s*\{\s*(\w+)\s*\}""") 
                                          else Regex("""export\s*\{\s*(\w+)\s+as\s+$exportChar\s*\}""")
                        val internalMatch = exportRegex.find(fContent)
                        
                        if (internalMatch != null) {
                            val internalVar = internalMatch.groupValues[1]
                            // Remove imports/exports from dependency to clean it
                            val clean = cleanJs(fContent)
                            dependencies.append("var $name = (() => { $clean; return $internalVar; })();\n")
                            return
                        }
                    } catch (e: Exception) { logger("Link Err $name") }
                }
                dependencies.append("var $name = $defaultVal;\n")
            }

            // Link the specific files required by References7
            linkModule("PdfStyle", "P", "PdfStyle", "{}")
            linkModule("Signed", "S", "Signed", "\"\"")
            linkModule("LicenseYear", "L", "LicenseYear", "[]")
            linkModule("SpecialityLincense", "S", "SpecLic", "{}")
            linkModule("DocumentLink", "DEFAULT", "DocLink", "{}")
            linkModule("ru", "r", "RuLang", "{}")

            // 4. MOCK OTHER IMPORTS (Vue, etc.)
            val varsToMock = mutableSetOf<String>()
            // Find all: import { a, b as c } from ...
            val importRegex = Regex("""import\s*\{(.*?)\}\s*from""")
            importRegex.findAll(refContent).forEach { m ->
                m.groupValues[1].split(",").forEach { 
                    val parts = it.trim().split(Regex("""\s+as\s+"""))
                    val v = if (parts.size > 1) parts[1] else parts[0]
                    if (v.isNotBlank()) varsToMock.add(v.trim())
                }
            }
            // Don't mock what we linked, and DO NOT mock '$' (we provide it manually)
            varsToMock.removeAll(linkedVars)
            varsToMock.remove("$") 

            val dummyScript = StringBuilder()
            dummyScript.append("const UniversalDummy = new Proxy(function(){}, { get: () => UniversalDummy, apply: () => UniversalDummy });\n")
            if (varsToMock.isNotEmpty()) {
                dummyScript.append("var ${varsToMock.joinToString(",")} = UniversalDummy;\n")
            }

            // 5. DYNAMIC EXTRACTION of Key Logic
            // Extract the 'at' function (PDF Definition)
            // Look for: const at = (o,d,v) => ...
            // We assume it's a const function definition that eventually returns pageSize:"A4"
            var cleanMain = cleanJs(refContent)
            
            // Regex to capture the function name for PDF generation
            val genFuncRegex = Regex("""const\s+(\w+)\s*=\s*\(\w+,\w+,\w+\)\s*=>\s*\{[^}]*pageSize:["']A4["']""")
            val genMatch = genFuncRegex.find(cleanMain)
            val genFuncName = genMatch?.groupValues?.get(1) ?: "at" // Fallback to 'at'

            // Extract the 'h' array (Course Names: "первого", "второго"...)
            val courseArrayRegex = Regex("""const\s+(\w+)\s*=\s*\["первого","второго"[^\]]*\]""")
            val courseMatch = courseArrayRegex.find(cleanMain)
            val courseArrayName = courseMatch?.groupValues?.get(1) ?: "h"

            // Expose them to window
            val exposeCode = "\nwindow.RefDocGenerator = $genFuncName;\nwindow.RefCourseNames = $courseArrayName;"

            val finalScript = dummyScript.toString() + dependencies.toString() + "\n" + cleanMain + exposeCode

            return@withContext PdfResources(finalScript)

        } catch (e: Exception) {
            logger("Fetch Error: ${e.message}")
            e.printStackTrace()
            return@withContext PdfResources("")
        }
    }

    // Robust import stripper
    private fun cleanJs(content: String): String {
        return content
            .replace(Regex("""import\s*\{[^}]*\}\s*from\s*['"][^'"]+['"];?""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""import\s+[\w*]+\s+(?:as\s+\w+\s+)?from\s*['"][^'"]+['"];?"""), "")
            .replace(Regex("""import\s*['"][^'"]+['"];?"""), "")
            .replace(Regex("""export\s*\{[^}]*\}""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""export\s+default\s+"""), "")
    }

    private fun getName(path: String) = path.split('/').last()

    private fun fetchString(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            return response.body?.string() ?: ""
        }
    }

    private fun findMatch(content: String, regex: String): String? {
        return Regex(regex).find(content)?.groupValues?.get(1)
    }
}

class ReferencePdfGenerator(private val context: Context) {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun generatePdf(
        studentInfoJson: String,
        licenseInfoJson: String,
        univInfoJson: String,
        linkId: Long,
        qrUrl: String,
        resources: PdfResources,
        logCallback: (String) -> Unit
    ): ByteArray = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        
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
                // 1. Mock Data
                const studentInfo = $studentInfoJson;
                const licenseInfo = $licenseInfoJson;
                const univInfo = $univInfoJson;
                const qrCodeUrl = "$qrUrl";
                
                // 2. Mock Moment.js (referenced as '$' in script)
                // We declare it with 'var' so it doesn't conflict if the script tries to var-declare it too
                var ${'$'} = function(d) { 
                    return { format: (f) => (d ? new Date(d) : new Date()).toLocaleDateString("ru-RU") }; 
                };
                ${'$'}.locale = function() {};
            </script>

            <script>
                // 3. Load Fetched Scripts
                try {
                    ${resources.combinedScript}
                    AndroidBridge.log("Scripts loaded successfully.");
                } catch(e) { AndroidBridge.returnError("Script Load Error: " + e.message); }
            </script>

            <script>
                function startGeneration() {
                    try {
                        AndroidBridge.log("Starting PDF Generation...");
                        
                        if (typeof window.RefDocGenerator !== 'function') throw "Generator function not found";
                        
                        // 4. Dynamic Calculation (using extracted logic variables if available)
                        // Use extracted 'RefCourseNames' array for courses, fallback if missing
                        const courses = window.RefCourseNames || ["первого","второго","третьего","четвертого","пятого","шестого","седьмого"];
                        
                        const activeSem = studentInfo.active_semester || 1;
                        const totalSem = licenseInfo.total_semester || 8;
                        
                        const e = Math.floor((activeSem - 1) / 2);
                        const i = Math.floor((totalSem - 1) / 2);
                        const courseStr = courses[Math.min(e, i)] || "Unknown";

                        // Construct ID
                        const second = studentInfo.second || "24";
                        const studId = studentInfo.lastStudentMovement ? studentInfo.lastStudentMovement.id_student : "0";
                        const payId = studentInfo.payment_form_id || (studentInfo.lastStudentMovement ? studentInfo.lastStudentMovement.id_payment_form : "1");
                        const docIdStr = "№ 7-" + second + "-" + studId + "-" + payId;

                        // Data Object for Generator
                        const d = {
                            id: docIdStr,
                            edunum: courseStr,
                            date: new Date().toLocaleDateString("ru-RU"),
                            adress: univInfo.address_ru || "г. Ош, ул. Ленина 331"
                        };

                        // 5. Generate
                        const docDef = window.RefDocGenerator(studentInfo, d, qrCodeUrl);
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