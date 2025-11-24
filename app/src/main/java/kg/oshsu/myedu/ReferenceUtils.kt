package kg.oshsu.myedu

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ReferenceJsFetcher {
    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"

    suspend fun fetchResources(logger: (String) -> Unit, language: String, dictionary: Map<String, String>): PdfResources = withContext(Dispatchers.IO) {
        try {
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""")
                ?: throw Exception("Main JS missing")
            
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            var refJsPath = findMatch(mainJsContent, """["']([^"']*References7\.[^"']+\.js)["']""")
            
            if (refJsPath == null) {
                val docsJsPath = findMatch(mainJsContent, """["']([^"']*StudentDocuments\.[^"']+\.js)["']""")
                if (docsJsPath != null) {
                    val docsContent = fetchString("$baseUrl/assets/" + getName(docsJsPath))
                    refJsPath = findMatch(docsContent, """["']([^"']*References7\.[^"']+\.js)["']""")
                }
            }
            
            if (refJsPath == null) throw Exception("References7 script path not found.")
            
            val scriptName = getName(refJsPath)
            logger("Target Script: $scriptName")
            val scriptContent = fetchString("$baseUrl/assets/$scriptName")
            
            return@withContext PdfResources(scriptContent)
        } catch (e: Exception) {
            logger("Fetch Error: ${e.message}")
            throw e
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
        return Regex(regex).find(content)?.groupValues?.get(1)
    }

    private fun getName(path: String) = path.split('/').last()
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
        bearerToken: String,
        language: String = "ru",
        dictionary: Map<String, String> = emptyMap(),
        logCallback: (String) -> Unit
    ): ByteArray = suspendCancellableCoroutine { continuation ->
        
        Handler(Looper.getMainLooper()).post {
            try {
                val webView = WebView(context)
                val settings = webView.settings
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                
                // Cookie Mocking
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setCookie("https://myedu.oshsu.kg", "myedu-jwt-token=$bearerToken; Domain=myedu.oshsu.kg; Path=/")
                cookieManager.flush()

                webView.webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                        logCallback("[JS] ${cm.message()} (L${cm.lineNumber()})")
                        return true
                    }
                }

                webView.addJavascriptInterface(object : Any() {
                    @JavascriptInterface
                    fun returnPdf(base64: String) {
                        try {
                            val clean = if (base64.contains(",")) base64.split(",")[1] else base64
                            val bytes = Base64.decode(clean, Base64.DEFAULT)
                            if (continuation.isActive) continuation.resume(bytes)
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }
                    }
                    @JavascriptInterface
                    fun returnError(msg: String) {
                        if (continuation.isActive) continuation.resumeWithException(Exception(msg))
                    }
                    @JavascriptInterface
                    fun log(msg: String) = logCallback(msg)
                }, "AndroidBridge")

                val dictionaryJson = JSONObject(dictionary).toString()
                val dateLocale = if (language == "en") "en-US" else "ru-RU"

                val jsContent = resources.combinedScript
                
                val generatorRegex = Regex("""const\s+(\w+)\s*=\s*\(\w+,\w+,\w+\)\s*=>\s*\{[\s\S]*?return\s*\{[\s\S]*?pageSize\s*:\s*["']A4["'][\s\S]*?\}\s*\}""")
                val match = generatorRegex.find(jsContent)
                
                val extractedFunction: String
                if (match != null) {
                    val originalCode = match.value
                    val originalName = match.groupValues[1]
                    extractedFunction = originalCode.replaceFirst("const $originalName", "const generateDocDef")
                } else {
                    val startIndex = jsContent.indexOf("const at=(")
                    val endIndex = jsContent.indexOf("const nt={")
                    if (startIndex != -1 && endIndex != -1) {
                        var code = jsContent.substring(startIndex, endIndex)
                        code = code.replaceFirst("const at", "const generateDocDef")
                        if (code.trim().endsWith(",")) code = code.substring(0, code.lastIndexOf(","))
                        extractedFunction = code
                    } else {
                        if (continuation.isActive) continuation.resumeWithException(Exception("Could not extract PDF logic from script."))
                        return@post
                    }
                }
                
                val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <script src="https://cdnjs.cloudflare.com/ajax/libs/pdfmake/0.2.7/pdfmake.min.js"></script>
                    <script src="https://cdnjs.cloudflare.com/ajax/libs/pdfmake/0.2.7/vfs_fonts.js"></script>
                </head>
                <body>
                <script>
                    window.onerror = function(msg, url, line) { 
                        AndroidBridge.returnError(msg + " @ " + line); 
                    };

                    const studentInfo = $studentInfoJson;
                    const licenseInfo = $licenseInfoJson;
                    const univInfo = $univInfoJson;
                    const qrCodeUrl = "$qrUrl";
                    const lang = "$language";
                    const dictionary = $dictionaryJson;

                    const tt = {
                        f10: { fontSize: 10 },
                        f11: { fontSize: 11 },
                        f12: { fontSize: 12 },
                        f13: { fontSize: 13 },
                        f14: { fontSize: 14 },
                        f16: { fontSize: 16 },
                        fb: { bold: true },
                        textCenter: { alignment: 'center' },
                        textRight: { alignment: 'right' }
                    };
                    
                    const et = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+P+/HgAFhAJ/wlseKgAAAABJRU5ErkJggg==";

                    $extractedFunction

                    function translateString(str) {
                        if (!str || typeof str !== 'string') return str;
                        if (dictionary[str]) return dictionary[str];
                        let s = str;
                        for (const [key, value] of Object.entries(dictionary)) {
                            if (key.length > 2 && s.includes(key)) { s = s.split(key).join(value); }
                        }
                        return s;
                    }

                    function prepareAndGenerate() {
                        try {
                            if (lang === "en") {
                                 const tr = (obj, key) => { if (obj && obj[key]) obj[key] = translateString(obj[key]); };
                                 tr(studentInfo, "faculty_ru");
                                 tr(studentInfo, "speciality_ru");
                                 tr(studentInfo, "edu_form_ru");
                                 tr(studentInfo, "payment_form_name_ru");
                                 
                                 if (studentInfo.lastStudentMovement) {
                                     const lsm = studentInfo.lastStudentMovement;
                                     if (lsm.speciality && lsm.speciality.direction) tr(lsm.speciality.direction, "name_ru");
                                     if (lsm.edu_form) tr(lsm.edu_form, "name_ru");
                                     if (lsm.payment_form) tr(lsm.payment_form, "name_ru");
                                 }
                                 tr(univInfo, "address_ru");
                            }

                            let courses = ["первого","второго","третьего","четвертого","пятого","шестого","седьмого","восьмого"];
                            if (lang === "en") courses = ["First", "Second", "Third", "Fourth", "Fifth", "Sixth", "Seventh", "Eighth"];

                            const activeSem = studentInfo.active_semester || 1;
                            const totalSem = licenseInfo.total_semester || 8;
                            const e = Math.floor((activeSem - 1) / 2);
                            const i = Math.floor((totalSem - 1) / 2);
                            const suffix = lang === "en" ? " Course" : "-го"; 
                            const courseStr = courses[Math.min(e, i)] || (Math.min(e, i) + 1) + suffix;
                            
                            const second = studentInfo.second || "24";
                            const studId = studentInfo.lastStudentMovement ? studentInfo.lastStudentMovement.id_student : "0";
                            const payId = studentInfo.payment_form_id || (studentInfo.lastStudentMovement ? studentInfo.lastStudentMovement.id_payment_form : "1");
                            const docIdStr = "№ 7-" + second + "-" + studId + "-" + payId;
                            
                            let address = univInfo.address_ru || "г. Ош, ул. Ленина 331";
                            if(lang === "en") address = translateString(address);
                            
                            const extraData = { 
                                id: docIdStr, 
                                edunum: courseStr, 
                                date: new Date().toLocaleDateString("$dateLocale"), 
                                adress: address 
                            };

                            AndroidBridge.log("Generating PDF...");
                            const docDef = generateDocDef(studentInfo, extraData, qrCodeUrl);
                            pdfMake.createPdf(docDef).getBase64(function(b64) {
                                AndroidBridge.returnPdf(b64);
                            });

                        } catch(e) {
                            AndroidBridge.returnError("Logic Error: " + e.message);
                        }
                    }

                    prepareAndGenerate();
                </script>
                </body>
                </html>
                """

                webView.loadDataWithBaseURL("https://myedu.oshsu.kg/", html, "text/html", "UTF-8", null)

            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }
    }
}