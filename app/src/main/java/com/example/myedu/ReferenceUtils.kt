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
            logger("Fetching index...")
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""") ?: throw Exception("Main JS missing")
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            
            var refJsPath = findMatch(mainJsContent, """["']([^"']*References7\.[^"']+\.js)["']""")
            if (refJsPath == null) {
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

            // --- LINK DEPENDENCIES ---
            val dependencies = StringBuilder()
            val linkedVars = mutableSetOf<String>()

            suspend fun linkModule(keyword: String, exportChar: String, fallback: String, defaultVal: String) {
                var varName: String? = null
                if (exportChar == "DEFAULT_OR_NAMED") {
                    val r = Regex("""import\s*\{\s*(\w+)\s*\}\s*from\s*['"][^'"]*$keyword[^'"]*['"]""")
                    varName = findMatch(refContent, r.pattern)
                } else {
                    val r = Regex("""import\s*\{\s*$exportChar\s+as\s+(\w+)\s*\}\s*from\s*['"][^'"]*$keyword[^'"]*['"]""")
                    varName = findMatch(refContent, r.pattern)
                }
                
                val name = varName ?: fallback
                linkedVars.add(name)

                val fileRegex = Regex("""["']([^"']*$keyword\.[^"']+\.js)["']""")
                val pathMatch = fileRegex.find(refContent) ?: fileRegex.find(mainJsContent)
                
                if (pathMatch != null) {
                    try {
                        val fName = getName(pathMatch.groupValues[1])
                        val fContent = fetchString("$baseUrl/assets/$fName")
                        val exportRegex = if(exportChar == "DEFAULT_OR_NAMED") Regex("""export\s*\{\s*(\w+)\s*\}""") 
                                          else Regex("""export\s*\{\s*(\w+)\s+as\s+$exportChar\s*\}""")
                        val internalMatch = exportRegex.find(fContent)
                        
                        if (internalMatch != null) {
                            val internalVar = internalMatch.groupValues[1]
                            val clean = cleanJs(fContent)
                            dependencies.append("var $name = (() => { $clean; return $internalVar; })();\n")
                            return
                        }
                    } catch (e: Exception) { logger("Link Err $name") }
                }
                dependencies.append("var $name = $defaultVal;\n")
            }

            linkModule("PdfStyle", "P", "PdfStyle", "{}")
            linkModule("Signed", "S", "Signed", "\"\"")
            linkModule("LicenseYear", "L", "LicenseYear", "[]")
            linkModule("SpecialityLincense", "S", "SpecLic", "{}")
            linkModule("DocumentLink", "DEFAULT_OR_NAMED", "DocLink", "{}")
            linkModule("ru", "r", "RuLang", "{}")

            // --- MOCK OTHERS ---
            val varsToMock = mutableSetOf<String>()
            val importRegex = Regex("""import\s*\{(.*?)\}\s*from""")
            importRegex.findAll(refContent).forEach { m ->
                m.groupValues[1].split(",").forEach { 
                    val parts = it.trim().split(Regex("""\s+as\s+"""))
                    val v = if (parts.size > 1) parts[1] else parts[0]
                    if (v.isNotBlank()) varsToMock.add(v.trim())
                }
            }
            varsToMock.removeAll(linkedVars)
            varsToMock.remove("$") 

            val dummyScript = StringBuilder()
            dummyScript.append("const UniversalDummy = new Proxy(function(){}, { get: () => UniversalDummy, apply: () => UniversalDummy });\n")
            if (varsToMock.isNotEmpty()) {
                dummyScript.append("var ${varsToMock.joinToString(",")} = UniversalDummy;\n")
            }

            // --- DYNAMIC EXTRACTION ---
            var cleanMain = cleanJs(refContent)
            
            // Extract 'at' Function
            val generatorRegex = Regex("""const\s+(\w+)\s*=\s*\([a-zA-Z0-9,]*\)\s*=>\s*\{[^}]*pageSize:["']A4["']""")
            val generatorMatch = generatorRegex.find(cleanMain)
            val genFuncName = generatorMatch?.groupValues?.get(1) ?: "at" 

            // Extract Course Names Array
            val arrayRegex = Regex("""\[\s*["']первого["']\s*,\s*["']второго["'][^\]]*\]""")
            val arrayMatch = arrayRegex.find(cleanMain)
            val courseArrayLiteral = arrayMatch?.value ?: "['первого','второго','третьего','четвертого','пятого','шестого','седьмого']"

            val exposeCode = "\nwindow.RefDocGenerator = $genFuncName;\nwindow.RefCourseNames = $courseArrayLiteral;"

            val finalScript = dummyScript.toString() + dependencies.toString() + "\n" + cleanMain + exposeCode

            return@withContext PdfResources(finalScript)

        } catch (e: Exception) {
            logger("Fetch Error: ${e.message}")
            e.printStackTrace()
            return@withContext PdfResources("")
        }
    }

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
                const studentInfo = $studentInfoJson;
                const licenseInfo = $licenseInfoJson;
                const univInfo = $univInfoJson;
                const qrCodeUrl = "$qrUrl";
                const lang = "$language";

                var ${'$'} = function(d) { 
                    return { format: (f) => (d ? new Date(d) : new Date()).toLocaleDateString("$dateLocale") }; 
                };
                ${'$'}.locale = function() {};
            </script>

            <script>
                try {
                    ${resources.combinedScript}
                    AndroidBridge.log("JS: Scripts loaded.");
                } catch(e) { AndroidBridge.returnError("Script Error: " + e.message); }
            </script>

            <script>
                // --- Translation Helpers ---
                
                // 1. Recursive Data Translator: Swaps _ru values with _en values
                function translateObjectData(obj) {
                    if (!obj || typeof obj !== 'object') return;
                    
                    for (const key in obj) {
                        if (key.endsWith('_ru')) {
                            const enKey = key.replace('_ru', '_en');
                            if (obj.hasOwnProperty(enKey) && obj[enKey]) {
                                // Overwrite RU value with EN value
                                obj[key] = obj[enKey];
                            }
                        }
                        if (typeof obj[key] === 'object') {
                            translateObjectData(obj[key]); // Recurse
                        }
                    }
                }

                // 2. Static Text Translator: Replaces hardcoded Russian strings
                const dictionary = {
                    "МИНИСТЕРСТВО НАУКИ, ВЫСШЕГО ОБРАЗОВАНИЯ И ИННОВАЦИЙ КЫРГЫЗСКОЙ РЕСПУБЛИКИ": "MINISTRY OF EDUCATION AND SCIENCE OF THE KYRGYZ REPUBLIC",
                    "ОШСКИЙ ГОСУДАРСТВЕННЫЙ УНИВЕРСИТЕТ": "OSH STATE UNIVERSITY",
                    "Настоящая справка подтверждает, что": "This certificate confirms that",
                    "действительно является студентом (-кой)": "is a student of the",
                    "года обучения": "year of study",
                    "специальности/направление": "specialty/direction",
                    "Справка выдана по месту требования.": "Issued for submission to demanding authority.",
                    "Достоверность данного документа можно проверить отсканировав QR-код": "The authenticity of this document can be verified by scanning the QR code",
                    "профиль": "profile",
                    "первого": "1st", "второго": "2nd", "третьего": "3rd", "четвертого": "4th", 
                    "пятого": "5th", "шестого": "6th", "седьмого": "7th"
                };

                function translateDocDef(node) {
                    if (!node) return;
                    if (Array.isArray(node)) {
                        node.forEach(translateDocDef);
                    } else if (typeof node === 'object') {
                        if (node.text && typeof node.text === 'string') {
                            // Check full match first
                            if (dictionary[node.text]) {
                                node.text = dictionary[node.text];
                            } else {
                                // Partial replacements
                                for (const [ru, en] of Object.entries(dictionary)) {
                                    if (node.text.includes(ru)) {
                                        node.text = node.text.replace(ru, en);
                                    }
                                }
                            }
                            // Fix header "СПРАВКА №..." -> "REFERENCE №..."
                            if (node.text.startsWith && node.text.startsWith("СПРАВКА")) {
                                node.text = node.text.replace("СПРАВКА", "REFERENCE");
                            }
                        }
                        // Recurse into columns, stack, etc.
                        ["columns", "stack", "table", "body"].forEach(k => {
                            if (node[k]) translateDocDef(node[k]);
                        });
                    }
                }

                function startGeneration() {
                    try {
                        AndroidBridge.log("JS: Gen Start (" + lang + ")...");
                        
                        if (typeof window.RefDocGenerator !== 'function') throw "RefDocGenerator missing";

                        // A. Translate Data if needed
                        if (lang === 'en') {
                            translateObjectData(studentInfo);
                            translateObjectData(licenseInfo);
                            translateObjectData(univInfo);
                        }

                        // B. Calc Dynamic Fields
                        const courses = window.RefCourseNames || ["первого","второго","третьего","четвертого","пятого","шестого","седьмого"];
                        const activeSem = studentInfo.active_semester || 1;
                        const totalSem = licenseInfo.total_semester || 8;
                        const e = Math.floor((activeSem - 1) / 2);
                        const i = Math.floor((totalSem - 1) / 2);
                        let courseStr = courses[Math.min(e, i)] || (Math.min(e, i) + 1);
                        
                        // Translate Course String manually if needed
                        if (lang === 'en' && dictionary[courseStr]) {
                            courseStr = dictionary[courseStr];
                        }

                        const second = studentInfo.second || "24";
                        const studId = studentInfo.lastStudentMovement ? studentInfo.lastStudentMovement.id_student : "0";
                        const payId = studentInfo.payment_form_id || (studentInfo.lastStudentMovement ? studentInfo.lastStudentMovement.id_payment_form : "1");
                        const docIdStr = "№ 7-" + second + "-" + studId + "-" + payId;
                        
                        // Address translation logic
                        let address = univInfo.address_ru || "г. Ош, ул. Ленина 331";
                        if (lang === 'en' && univInfo.address_en) address = univInfo.address_en;

                        const dataObj = {
                            id: docIdStr,
                            edunum: courseStr,
                            date: new Date().toLocaleDateString("$dateLocale"),
                            adress: address
                        };

                        // C. Generate Document Definition
                        const docDef = window.RefDocGenerator(studentInfo, dataObj, qrCodeUrl);
                        
                        // D. Translate Static Text in DocDef
                        if (lang === 'en') {
                            translateDocDef(docDef.content);
                        }

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