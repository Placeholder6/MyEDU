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

class ReferenceJsFetcher {

    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"

    suspend fun fetchResources(logger: (String) -> Unit, language: String = "ru"): PdfResources = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch Main Script
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""") 
                ?: throw Exception("Main JS missing")
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            
            // 2. Fetch References7.js
            var refJsPath = findMatch(mainJsContent, """["']([^"']*References7\.[^"']+\.js)["']""")
            if (refJsPath == null) {
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

            // 3. LINK DEPENDENCIES
            val dependencies = StringBuilder()
            val linkedVars = mutableSetOf<String>()

            suspend fun linkModule(keyword: String, exportChar: String, fallback: String, defaultVal: String) {
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

            linkModule("PdfStyle", "P", "PdfStyle_Fallback", "{}")          
            linkModule("Signed", "S", "Signed_Fallback", "\"\"")            
            linkModule("LicenseYear", "L", "LicenseYear_Fallback", "[]")    
            linkModule("SpecialityLincense", "S", "SpecLic_Fallback", "{}") 
            linkModule("DocumentLink", "DEFAULT_OR_NAMED", "DocLink_Fallback", "{}") 
            linkModule("ru", "r", "Ru_Fallback", "{}")                      

            // 4. MOCK OTHERS
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

            // 5. TRANSLATE & CLEAN
            var cleanRef = cleanJs(refContent)
            
            if (language == "en") {
                logger("Translating Reference Script to English...")
                cleanRef = translateScript(cleanRef)
            }

            // 6. DYNAMIC EXTRACTION
            // Extract 'at' Function (PDF Generator)
            val generatorRegex = Regex("""const\s+(\w+)\s*=\s*\([a-zA-Z0-9,]*\)\s*=>\s*\{[^}]*pageSize:["']A4["']""")
            val generatorMatch = generatorRegex.find(cleanRef)
            val genFuncName = generatorMatch?.groupValues?.get(1) ?: "at" 

            // Extract Course Names Array (Handling potential translation)
            val arrayRegex = if (language == "en") {
                Regex("""const\s+(\w+)\s*=\s*\["1st","2nd"[^\]]*\]""")
            } else {
                Regex("""const\s+(\w+)\s*=\s*\["первого","второго"[^\]]*\]""")
            }
            val arrayMatch = arrayRegex.find(cleanRef)
            // If translation replaced it, the regex might need to match the new values, or we just trust the variable extraction
            // Ideally, we find the var name that holds the array. In the original it's 'h'.
            val courseArrayName = arrayMatch?.groupValues?.get(1) ?: "h"

            val exposeCode = "\nwindow.RefDocGenerator = $genFuncName;\nwindow.RefCourseNames = $courseArrayName;"

            val finalScript = dummyScript.toString() + dependencies.toString() + "\n(() => {\n" + cleanRef + exposeCode + "\n})();"
            
            return@withContext PdfResources(finalScript)

        } catch (e: Exception) {
            logger("Fetch Error: ${e.message}")
            e.printStackTrace()
            return@withContext PdfResources("")
        }
    }

    private fun translateScript(script: String): String {
        var s = script
        val replacements = mapOf(
            "МИНИСТЕРСТВО НАУКИ, ВЫСШЕГО ОБРАЗОВАНИЯ И ИННОВАЦИЙ КЫРГЫЗСКОЙ РЕСПУБЛИКИ" to "MINISTRY OF EDUCATION AND SCIENCE OF THE KYRGYZ REPUBLIC",
            "ОШСКИЙ ГОСУДАРСТВЕННЫЙ УНИВЕРСИТЕТ" to "OSH STATE UNIVERSITY",
            "СПРАВКА" to "CERTIFICATE",
            "Настоящая справка подтверждает, что" to "This certificate confirms that",
            "действительно является студентом (-кой)" to "is a student of the",
            "года обучения" to "year of study",
            "специальности/направление" to "specialty/direction",
            "профиль:" to "profile:",
            "Справка выдана по месту требования." to "Issued for submission to the place of demand.",
            "Достоверность данного документа можно проверить отсканировав QR-код" to "Authenticity can be verified by scanning the QR code",
            // Array replacement
            """["первого","второго","третьего","четвертого","пятого","шестого","седьмого"]""" to """["1st","2nd","3rd","4th","5th","6th","7th"]"""
        )
        
        replacements.forEach { (ru, en) -> 
            s = s.replace(ru, en) 
        }
        return s
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
                window.onerror = function(msg, url, line) { AndroidBridge.returnError(msg + " @ Line " + line); };
                const studentInfo = $studentInfoJson;
                const licenseInfo = $licenseInfoJson;
                const univInfo = $univInfoJson;
                const qrCodeUrl = "$qrUrl";
                const lang = "$language";

                // Mock moment.js
                var ${'$'} = function(d) { 
                    return { format: (f) => (d ? new Date(d) : new Date()).toLocaleDateString("$dateLocale") }; 
                };
                ${'$'}.locale = function() {};

                // Helper to translate Data
                function translateData() {
                    if (lang !== "en") return;
                    
                    const map = {
                        "Международный медицинский факультет": "International Medical Faculty",
                        "Лечебное дело": "General Medicine",
                        "Очное (специалитет)": "Full-time (Specialist)",
                        "Контракт": "Contract",
                        "Бюджет": "Budget"
                    };

                    function t(str) { return map[str] || str; }

                    // Translate Student Info fields used in Reference
                    if (studentInfo.faculty_ru) studentInfo.faculty_ru = t(studentInfo.faculty_ru);
                    if (studentInfo.edu_form_ru) studentInfo.edu_form_ru = t(studentInfo.edu_form_ru);
                    if (studentInfo.speciality_ru) studentInfo.speciality_ru = t(studentInfo.speciality_ru);
                    
                    if (studentInfo.lastStudentMovement) {
                        if (studentInfo.lastStudentMovement.speciality) {
                           if (studentInfo.lastStudentMovement.speciality.direction) {
                               studentInfo.lastStudentMovement.speciality.direction.name_ru = 
                                   t(studentInfo.lastStudentMovement.speciality.direction.name_ru);
                           }
                        }
                        if (studentInfo.lastStudentMovement.payment_form) {
                             studentInfo.lastStudentMovement.payment_form.name_ru = 
                                 t(studentInfo.lastStudentMovement.payment_form.name_ru);
                        }
                    }
                }
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
                        
                        if (typeof window.RefDocGenerator !== 'function') throw "Generator missing";
                        
                        // Translate Data if EN
                        translateData();

                        // Logic from References7.js (Replicated)
                        const courses = window.RefCourseNames || ["1st","2nd","3rd","4th","5th","6th","7th"];
                        const activeSem = studentInfo.active_semester || 1;
                        const totalSem = licenseInfo.total_semester || 8;
                        const e = Math.floor((activeSem - 1) / 2);
                        const i = Math.floor((totalSem - 1) / 2);
                        const courseStr = courses[Math.min(e, i)] || (Math.min(e, i)+1) + "-th";

                        const second = studentInfo.second || "24";
                        const studId = studentInfo.lastStudentMovement ? studentInfo.lastStudentMovement.id_student : "0";
                        const payId = studentInfo.payment_form_id || (studentInfo.lastStudentMovement ? studentInfo.lastStudentMovement.id_payment_form : "1");
                        const docIdStr = "№ 7-" + second + "-" + studId + "-" + payId;

                        const dataObj = {
                            id: docIdStr,
                            edunum: courseStr,
                            date: new Date().toLocaleDateString("$dateLocale"),
                            adress: univInfo.address_ru || "Osh city, Lenin st. 331"
                        };

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