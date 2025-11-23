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

            suspend fun linkModule(fileKeyword: String, exportChar: String, fallbackName: String, fallbackValue: String) {
                // Detect variable name used in References7.js (e.g., import { S as et } -> "et")
                var varName: String? = null
                if (exportChar == "DEFAULT_OR_NAMED") {
                     // Named import: import { D } from ...
                     val regex = Regex("""import\s*\{\s*(\w+)\s*\}\s*from\s*['"][^'"]*$fileKeyword[^'"]*['"]""")
                     varName = findMatch(refContent, regex.pattern)
                } else {
                    // Aliased import: import { S as et } from ...
                    val regex = Regex("""import\s*\{\s*$exportChar\s+as\s+(\w+)\s*\}\s*from\s*['"][^'"]*$fileKeyword[^'"]*['"]""")
                    varName = findMatch(refContent, regex.pattern)
                }

                if (varName == null) varName = fallbackName
                linkedVars.add(varName)

                // Find File URL from imports
                val fileUrlRegex = Regex("""["']([^"']*$fileKeyword\.[^"']+\.js)["']""")
                val fileNameMatch = fileUrlRegex.find(refContent) ?: fileUrlRegex.find(mainJsContent)
                
                var success = false
                if (fileNameMatch != null) {
                    val fileName = getName(fileNameMatch.groupValues[1])
                    try {
                        val fileContent = fetchString("$baseUrl/assets/$fileName")
                        // Extract export body
                        val exportRegex = if (exportChar == "DEFAULT_OR_NAMED") 
                             Regex("""export\s*\{\s*(\w+)\s*\}""") 
                        else 
                             Regex("""export\s*\{\s*(\w+)\s+as\s+$exportChar\s*\}""")
                        
                        val internalVarMatch = exportRegex.find(fileContent)
                        if (internalVarMatch != null) {
                            val internalVar = internalVarMatch.groupValues[1]
                            val cleanContent = cleanJsContent(fileContent)
                            // Use 'var' to avoid "Identifier has already been declared" conflicts with minified code
                            dependencies.append("var $varName = (() => {\n$cleanContent\nreturn $internalVar;\n})();\n")
                            success = true
                        }
                    } catch (e: Exception) { logger("Link Warn: $fileName ${e.message}") }
                }
                if (!success) dependencies.append("var $varName = $fallbackValue;\n")
            }

            // Link Dependencies (Keywords must match website filenames)
            linkModule("PdfStyle", "P", "PdfStyle_Fallback", "{}")          
            linkModule("Signed", "S", "Signed_Fallback", "\"\"")            
            linkModule("LicenseYear", "L", "LicenseYear_Fallback", "[]")    
            linkModule("SpecialityLincense", "S", "SpecLic_Fallback", "{}") 
            linkModule("DocumentLink", "DEFAULT_OR_NAMED", "DocLink_Fallback", "{}") 
            linkModule("ru", "r", "Ru_Fallback", "{}")                      

            // 4. MOCK OTHERS
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
            varsToMock.remove("$") // CRITICAL: Don't mock $ (conflict with manual definition)

            val dummyScript = StringBuilder()
            dummyScript.append("const UniversalDummy = new Proxy(function(){}, { get: () => UniversalDummy, apply: () => UniversalDummy, construct: () => UniversalDummy });\n")
            if (varsToMock.isNotEmpty()) {
                dummyScript.append("var ")
                dummyScript.append(varsToMock.joinToString(",") { "$it = UniversalDummy" })
                dummyScript.append(";\n")
            }

            // 5. DYNAMIC EXTRACTION
            var cleanRef = cleanJsContent(refContent)

            // Extract 'at' Function (PDF Generator)
            // Looks for: const at=(o,d,v)=>{ ... pageSize:"A4" ... }
            val generatorRegex = Regex("""const\s+(\w+)\s*=\s*\([a-zA-Z0-9,]*\)\s*=>\s*\{[^}]*pageSize:["']A4["']""")
            val generatorMatch = generatorRegex.find(cleanRef)
            val genFuncName = generatorMatch?.groupValues?.get(1) ?: "at" // default to 'at' if regex fails

            // Extract Course Names Array (Dynamic content)
            // Looks for: ["первого","второго", ... ]
            val arrayRegex = Regex("""\[\s*["']первого["']\s*,\s*["']второго["'][^\]]*\]""")
            val arrayMatch = arrayRegex.find(cleanRef)
            val courseArrayLiteral = arrayMatch?.value ?: "['первого','второго','третьего','четвертого','пятого','шестого','седьмого']"

            // NEW: TRANSLATE IF ENGLISH
            if (language == "en") {
                logger("Translating Reference Template...")
                cleanRef = translateToEnglish(cleanRef)
            }

            val exposeCode = "\nwindow.RefDocGenerator = $genFuncName;\nwindow.RefCourseNames = $courseArrayLiteral;"

            // Assemble Script
            val finalScript = dummyScript.toString() + dependencies.toString() + "\n(() => {\n" + cleanRef + exposeCode + "\n})();"
            
            return@withContext PdfResources(finalScript)

        } catch (e: Exception) {
            logger("Ref Fetch Error: ${e.message}")
            e.printStackTrace()
            return@withContext PdfResources("")
        }
    }

    private fun translateToEnglish(script: String): String {
        var s = script
        val map = mapOf(
            "СПРАВКА" to "REFERENCE",
            "Дана" to "Given to",
            "в том, что он(а)" to "certifying that he/she",
            "в том, что он" to "certifying that he",
            "в том, что она" to "certifying that she",
            "действительно обучается" to "is currently studying",
            "в Ошском государственном университете" to "at Osh State University",
            "ОШСКИЙ ГОСУДАРСТВЕННЫЙ УНИВЕРСИТЕТ" to "OSH STATE UNIVERSITY",
            "МИНИСТЕРСТВО ОБРАЗОВАНИЯ И НАУКИ" to "MINISTRY OF EDUCATION AND SCIENCE",
            "КЫРГЫЗСКОЙ РЕСПУБЛИКИ" to "OF THE KYRGYZ REPUBLIC",
            "Форма обучения" to "Form of Study",
            "Специальность" to "Specialty",
            "Направление" to "Direction",
            "Курс" to "Year",
            "по настоящее время" to "to the present time",
            "Приказ о зачислении" to "Enrollment Order",
            "Примечание" to "Note",
            "Ректор" to "Rector",
            "Гербовая печать" to "Official Seal",
            "Подпись" to "Signature",
            "Адрес" to "Address",
            "Телефон" to "Phone"
        )
        map.forEach { (ru, en) -> s = s.replace(ru, en) }
        return s
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
                const linkId = $linkId;
                const qrCodeUrl = "$qrUrl";
                const lang = "$language";

                // Mock moment.js (variable '$')
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
                // Helper to replace Russian phrases within strings (preserving codes)
                function translateString(str) {
                    if (!str) return str;
                    let s = str;
                    const replacements = {
                        "Лечебное дело": "General Medicine",
                        "Очное (специалитет)": "Full-time (Specialist)",
                        "Международный медицинский факультет": "International Medical Faculty",
                        "Очное": "Full-time",
                        "Заочное": "Part-time",
                        "Ошский государственный университет": "Osh State University",
                        "г. Ош, ул. Ленина 331": "Osh city, Lenin st. 331"
                    };
                    for (const [key, value] of Object.entries(replacements)) {
                        s = s.split(key).join(value);
                    }
                    return s;
                }
                
                function translateData() {
                     if (lang !== "en") return;
                     
                     // Translate Dynamic Student Fields
                     ["faculty_ru", "direction_ru", "speciality_ru"].forEach(field => {
                        if (studentInfo[field]) studentInfo[field] = translateString(studentInfo[field]);
                     });
                     
                     if (studentInfo.lastStudentMovement && studentInfo.lastStudentMovement.edu_form && studentInfo.lastStudentMovement.edu_form.name_ru) {
                        studentInfo.lastStudentMovement.edu_form.name_ru = translateString(studentInfo.lastStudentMovement.edu_form.name_ru);
                     }
                }

                function startGeneration() {
                    try {
                        AndroidBridge.log("JS: Ref Driver started (" + lang + ")...");
                        
                        if (typeof window.RefDocGenerator !== 'function') {
                             throw "Generator function not found. Extraction failed.";
                        }
                        
                        translateData();
                        
                        // 1. Calculate Dynamic Fields
                        let courses = window.RefCourseNames;
                        if (lang === "en") {
                            courses = ['1st', '2nd', '3rd', '4th', '5th', '6th', '7th', '8th'];
                        }

                        if (!courses) throw "Course names array not found.";
                        
                        const activeSem = studentInfo.active_semester || 1;
                        const totalSem = licenseInfo.total_semester || 8;
                        const e = Math.floor((activeSem - 1) / 2);
                        const i = Math.floor((totalSem - 1) / 2);
                        const courseStr = courses[Math.min(e, i)] || (Math.min(e, i) + 1) + (lang === "en" ? "th" : "-го");

                        const second = studentInfo.second || "24";
                        const studId = studentInfo.lastStudentMovement ? studentInfo.lastStudentMovement.id_student : "0";
                        const payId = studentInfo.payment_form_id || (studentInfo.lastStudentMovement ? studentInfo.lastStudentMovement.id_payment_form : "1");
                        const docIdStr = "№ 7-" + second + "-" + studId + "-" + payId;
                        
                        // Address Handling
                        let address = univInfo.address_ru || "г. Ош, ул. Ленина 331";
                        if (lang === "en" && univInfo.address_en) {
                             address = univInfo.address_en;
                        } else if (lang === "en") {
                             address = translateString(address);
                        }

                        // 2. Construct Data Object
                        const d = {
                            id: docIdStr,
                            edunum: courseStr,
                            date: new Date().toLocaleDateString("$dateLocale"),
                            adress: address
                        };

                        // 3. Call Generator
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