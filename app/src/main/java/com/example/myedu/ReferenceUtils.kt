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

data class PdfResources(val combinedScript: String)

class ReferenceJsFetcher {

    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"

    suspend fun fetchResources(logger: (String) -> Unit): PdfResources = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch Main Script to find References7 path
            logger("Fetching index...")
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""") 
                ?: throw Exception("Main JS missing")
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            
            // 2. Locate References7.js
            var refJsPath = findMatch(mainJsContent, """["']([^"']*References7\.[^"']+\.js)["']""")
            if (refJsPath == null) {
                // Fallback: Check StudentDocuments if lazy loaded
                val docsJsPath = findMatch(mainJsContent, """["']([^"']*StudentDocuments\.[^"']+\.js)["']""")
                if (docsJsPath != null) {
                    val docsContent = fetchString("$baseUrl/assets/${getName(docsJsPath)}")
                    refJsPath = findMatch(docsContent, """["']([^"']*References7\.[^"']+\.js)["']""")
                }
            }
            if (refJsPath == null) throw Exception("References7 JS missing")
            val refJsName = getName(refJsPath)
            logger("Fetching $refJsName...")
            val refContent = fetchString("$baseUrl/assets/$refJsName")

            // 3. LINK DEPENDENCIES (Dynamic Name Resolution)
            val dependencies = StringBuilder()
            val linkedVars = mutableSetOf<String>()

            suspend fun linkModule(keyword: String, exportChar: String, fallback: String, defaultVal: String) {
                // Detect variable name used in References7.js (e.g. import { S as et } -> "et")
                var varName: String? = null
                if (exportChar == "DEFAULT_OR_NAMED") {
                     val regex = Regex("""import\s*\{\s*(\w+)\s*\}\s*from\s*['"][^'"]*$keyword[^'"]*['"]""")
                     varName = findMatch(refContent, regex.pattern)
                } else {
                    val regex = Regex("""import\s*\{\s*$exportChar\s+as\s+(\w+)\s*\}\s*from\s*['"][^'"]*$keyword[^'"]*['"]""")
                    varName = findMatch(refContent, regex.pattern)
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
                        
                        // Extract the exported variable inside the file
                        val exportRegex = if(exportChar == "DEFAULT_OR_NAMED") Regex("""export\s*\{\s*(\w+)\s*\}""") 
                                          else Regex("""export\s*\{\s*(\w+)\s+as\s+$exportChar\s*\}""")
                        val internalMatch = exportRegex.find(fContent)
                        
                        if (internalMatch != null) {
                            val internalVar = internalMatch.groupValues[1]
                            val clean = cleanJsContent(fContent)
                            // Use 'var' to prevent "Identifier already declared" errors if minification reuses names
                            dependencies.append("var $name = (() => { $clean; return $internalVar; })();\n")
                            return
                        }
                    } catch (e: Exception) { logger("Link Err $name: ${e.message}") }
                }
                dependencies.append("var $name = $defaultVal;\n")
            }

            // Link specific files required by References7
            linkModule("PdfStyle", "P", "PdfStyle_Fallback", "{}")          
            linkModule("Signed", "S", "Signed_Fallback", "\"\"")            
            linkModule("LicenseYear", "L", "LicenseYear_Fallback", "[]")    
            linkModule("SpecialityLincense", "S", "SpecLic_Fallback", "{}") 
            linkModule("DocumentLink", "DEFAULT_OR_NAMED", "DocLink_Fallback", "{}") 
            linkModule("ru", "r", "Ru_Fallback", "{}")                      

            // 4. MOCK OTHER IMPORTS
            val varsToMock = mutableSetOf<String>()
            // Robust regex to catch multiline imports: import { a, b } from ...
            val importRegex = Regex("""import\s*\{([\s\S]*?)\}\s*from""")
            importRegex.findAll(refContent).forEach { m ->
                m.groupValues[1].split(",").forEach { 
                    val parts = it.trim().split(Regex("""\s+as\s+"""))
                    val v = if (parts.size > 1) parts[1] else parts[0]
                    if (v.isNotBlank()) varsToMock.add(v.trim())
                }
            }
            varsToMock.removeAll(linkedVars)
            varsToMock.remove("$") // CRITICAL: Do NOT mock '$' (Moment.js), we define it manually

            val dummyScript = StringBuilder()
            dummyScript.append("const UniversalDummy = new Proxy(function(){}, { get: () => UniversalDummy, apply: () => UniversalDummy });\n")
            if (varsToMock.isNotEmpty()) {
                dummyScript.append("var ${varsToMock.joinToString(",")} = UniversalDummy;\n")
            }

            // 5. EXTRACT CORE LOGIC
            // Instead of running the whole script (which crashes on Vue), we extract the generator function.
            var cleanRef = cleanJsContent(refContent)
            
            // A. Extract 'at' Function (PDF Generator)
            // Matches: const at=(o,d,v)=>{...pageSize:"A4"...}
            val generatorRegex = Regex("""const\s+(\w+)\s*=\s*\([a-zA-Z0-9,]*\)\s*=>\s*\{[^}]*pageSize:["']A4["']""")
            val generatorMatch = generatorRegex.find(cleanRef)
            val genFuncName = generatorMatch?.groupValues?.get(1) ?: "at" // fallback

            // B. Extract 'h' Array (Course Names)
            // Matches: const h=["первого","второго"...]
            val arrayRegex = Regex("""const\s+(\w+)\s*=\s*\["первого","второго"[^\]]*\]""")
            val arrayMatch = arrayRegex.find(cleanRef)
            val courseArrayName = arrayMatch?.groupValues?.get(1) ?: "h" // fallback

            // Expose them globally
            val exposeCode = "\nwindow.RefDocGenerator = $genFuncName;\nwindow.RefCourseNames = $courseArrayName;"

            // Wrap in IIFE to execute definitions but avoid global pollution/syntax errors
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
                        if (continuation.isActive) continuation.resumeWith(Result.success(bytes))
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                    }
                }

                @JavascriptInterface
                fun returnError(msg: String) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(Exception("JS: $msg")))
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
                const licenseInfo = $licenseInfoJson;
                const univInfo = $univInfoJson;
                const linkId = $linkId;
                const qrCodeUrl = "$qrUrl";

                // Manual Mock for Moment.js ($)
                // Declared as var to be robust against redeclaration attempts
                var ${'$'} = function(d) { 
                    return { format: (f) => (d ? new Date(d) : new Date()).toLocaleDateString("ru-RU") }; 
                };
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
                        
                        if (typeof window.RefDocGenerator !== 'function') {
                             throw "RefDocGenerator function not found. Extraction failed.";
                        }
                        if (!window.RefCourseNames) {
                             throw "RefCourseNames array not found. Extraction failed.";
                        }

                        // --- REPLICATE LOGIC from References7.js (Data Preparation) ---
                        
                        // 1. Calculate Course Number
                        const courses = window.RefCourseNames; 
                        const activeSem = studentInfo.active_semester || 1;
                        const totalSem = licenseInfo.total_semester || 8;
                        const e = Math.floor((activeSem - 1) / 2);
                        const i = Math.floor((totalSem - 1) / 2);
                        const courseStr = courses[Math.min(e, i)] || (Math.min(e, i) + 1) + "-го";

                        // 2. Calculate ID String
                        const second = studentInfo.second || "24";
                        const studId = studentInfo.lastStudentMovement ? studentInfo.lastStudentMovement.id_student : "0";
                        const payId = studentInfo.payment_form_id || (studentInfo.lastStudentMovement ? studentInfo.lastStudentMovement.id_payment_form : "1");
                        const docIdStr = "№ 7-" + second + "-" + studId + "-" + payId;

                        // 3. Prepare Data Object (d)
                        const dataObj = {
                            id: docIdStr,
                            edunum: courseStr,
                            date: new Date().toLocaleDateString("ru-RU"),
                            adress: univInfo.address_ru || "г. Ош, ул. Ленина 331"
                        };

                        // 4. Generate
                        // Calls 'at(o, d, v)' -> 'at(student, data, qrCode)'
                        const docDef = window.RefDocGenerator(studentInfo, dataObj, qrCodeUrl);
                        
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