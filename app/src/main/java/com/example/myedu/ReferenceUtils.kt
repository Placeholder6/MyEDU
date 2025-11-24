package com.example.myedu

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
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

/**
 * Fetches the URL of the dynamic JavaScript file required for PDF generation.
 */
class ReferenceJsFetcher {

    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"

    suspend fun fetchResources(logger: (String) -> Unit): PdfResources = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch Index HTML to find the main entry point (index.js)
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""")
                ?: throw Exception("Main JS missing in index.html")
            
            // 2. Fetch Main JS content to find the reference to References7 script
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            
            // 3. Find References7 script path (e.g., assets/References7.abcdef.js)
            // It usually appears in the routing or lazy import section
            var refJsPath = findMatch(mainJsContent, """["']([^"']*References7\.[^"']+\.js)["']""")
            
            // Fallback: If not in main, look inside StudentDocuments chunk
            if (refJsPath == null) {
                val docsJsPath = findMatch(mainJsContent, """["']([^"']*StudentDocuments\.[^"']+\.js)["']""")
                if (docsJsPath != null) {
                    val docsContent = fetchString("$baseUrl/assets/" + getName(docsJsPath))
                    refJsPath = findMatch(docsContent, """["']([^"']*References7\.[^"']+\.js)["']""")
                }
            }
            
            if (refJsPath == null) throw Exception("References7 script path could not be found.")
            
            // Construct full URL. Regex usually returns relative path (e.g. ./References7.js or assets/References7.js)
            val scriptName = getName(refJsPath)
            val fullScriptUrl = "$baseUrl/assets/$scriptName"
            
            logger("Resolved Script URL: $fullScriptUrl")
            
            return@withContext PdfResources(fullScriptUrl)
        } catch (e: Exception) {
            logger("Fetch Error: ${e.message}")
            e.printStackTrace()
            // Return empty resources or rethrow depending on flow preference
            throw e
        }
    }

    private fun fetchString(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code} for $url")
            return response.body?.string() ?: ""
        }
    }

    private fun findMatch(content: String, regex: String): String? {
        return Regex(regex).find(content)?.groupValues?.get(1)
    }

    private fun getName(path: String) = path.split('/').last()
}

/**
 * Holds the URL to the script.
 */
data class PdfResources(val scriptUrl: String)

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
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                
                // IMPORTANT: Set Auth Cookie. 
                // The script might fetch images (logos) relative to the server.
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                // Note: Ensure domain matches the BASE_URL
                cookieManager.setCookie("https://myedu.oshsu.kg", "myedu-jwt-token=$bearerToken; Domain=myedu.oshsu.kg; Path=/")
                cookieManager.flush()

                webView.webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                        logCallback("[JS] ${cm.message()} (Line ${cm.lineNumber()})")
                        return true
                    }
                }

                webView.addJavascriptInterface(object : Any() {
                    @JavascriptInterface
                    fun returnPdf(base64: String) {
                        try {
                            // Strip prefix if present
                            val clean = if (base64.contains(",")) base64.split(",")[1] else base64
                            val bytes = Base64.decode(clean, Base64.DEFAULT)
                            if (continuation.isActive) continuation.resume(bytes)
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }
                    }

                    @JavascriptInterface
                    fun returnError(msg: String) {
                        if (continuation.isActive) continuation.resumeWithException(Exception("JS Error: $msg"))
                    }

                    @JavascriptInterface
                    fun log(msg: String) = logCallback(msg)
                }, "AndroidBridge")

                val dictionaryJson = JSONObject(dictionary).toString()
                val dateLocale = if (language == "en") "en-US" else "ru-RU"

                // We construct an HTML page that imports the remote script as a module.
                // <base href> is critical for relative imports inside the remote script to work.
                val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <base href="https://myedu.oshsu.kg/">
                    <script src="https://cdnjs.cloudflare.com/ajax/libs/pdfmake/0.2.7/pdfmake.min.js"></script>
                    <script src="https://cdnjs.cloudflare.com/ajax/libs/pdfmake/0.2.7/vfs_fonts.js"></script>
                </head>
                <body>
                <script>
                    // Global Error Handler
                    window.onerror = function(msg, url, line) { 
                        AndroidBridge.returnError(msg + " @ " + line); 
                    };

                    // 1. Setup Data Environment
                    const studentInfo = $studentInfoJson;
                    const licenseInfo = $licenseInfoJson;
                    const univInfo = $univInfoJson;
                    const qrCodeUrl = "$qrUrl";
                    const lang = "$language";
                    const dictionary = $dictionaryJson;

                    // 2. Mock libraries/utilities that the Vue app might expect globally or via simple import
                    // Provide a simple date formatter compatible with what the script likely expects
                    var ${'$'} = function(d) { 
                        return { format: (f) => (d ? new Date(d) : new Date()).toLocaleDateString("$dateLocale") }; 
                    };
                    ${'$'}.locale = function() {};

                    // 3. Intercept PDFMake
                    // The remote script will generate a docDefinition object and call pdfMake.createPdf(docDef)
                    const realPdfMake = window.pdfMake;
                    window.pdfMake = {
                        createPdf: function(docDef) {
                            console.log('Captured PDF Document Definition');
                            try {
                                // Use the real library to generate the binary
                                realPdfMake.createPdf(docDef).getBase64(function(b64) {
                                    AndroidBridge.returnPdf(b64);
                                });
                            } catch(e) {
                                AndroidBridge.returnError("PDF Generation Failed: " + e.message);
                            }
                            // Return dummy object to prevent script crash
                            return { open:()=>{}, print:()=>{}, download:()=>{} };
                        },
                        fonts: realPdfMake.fonts,
                        vfs: realPdfMake.vfs
                    };
                    
                    // helper for translation
                    function translateString(str) {
                        if (!str || typeof str !== 'string') return str;
                        if (dictionary[str]) return dictionary[str];
                        let s = str;
                        // Simple replacement for known dictionary keys inside sentences
                        for (const [key, value] of Object.entries(dictionary)) {
                            if (key.length > 2 && s.includes(key)) { s = s.split(key).join(value); }
                        }
                        return s;
                    }

                    function prepareData() {
                         // Perform translations on the data objects if language is English
                         if (lang !== "en") return;
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
                </script>

                <script type="module">
                    // 4. Import the Remote Script
                    // This handles all complex imports (referencesUtils, etc) natively by the browser
                    import * as RefModule from "${resources.scriptUrl}";
                    
                    AndroidBridge.log("Remote module imported.");

                    // 5. Detect the generator function
                    // Inspect the module exports to find the function that takes (studentInfo, data, qr)
                    let generatorFn = null;
                    
                    // Usually it's the default export, or a named export like 'a' or 'at' (minified)
                    if (typeof RefModule.default === 'function') {
                        generatorFn = RefModule.default;
                    } else {
                        // Iterate exports to find a function
                        for (const key in RefModule) {
                            if (typeof RefModule[key] === 'function') {
                                generatorFn = RefModule[key];
                                break; 
                            }
                        }
                    }

                    if (!generatorFn) {
                        AndroidBridge.returnError("Could not find generator function in script exports.");
                    } else {
                        executeGeneration(generatorFn);
                    }

                    function executeGeneration(genFn) {
                        try {
                            prepareData();

                            // Prepare dynamic data (Course number, document ID)
                            // This logic mimics the Vue component's preparation logic
                            let courses = ["первого","второго","третьего","четвертого","пятого","шестого","седьмого","восьмого"];
                            if (lang === "en") {
                                courses = ["First", "Second", "Third", "Fourth", "Fifth", "Sixth", "Seventh", "Eighth"];
                            }

                            const activeSem = studentInfo.active_semester || 1;
                            const totalSem = licenseInfo.total_semester || 8;
                            
                            // Calculate Course (e.g. Semester 3 -> Course 2)
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
                            
                            const additionalData = { 
                                id: docIdStr, 
                                edunum: courseStr, 
                                date: new Date().toLocaleDateString("$dateLocale"), 
                                adress: address 
                            };
                            
                            AndroidBridge.log("Calling Generator Function...");
                            
                            // EXECUTE 
                            // The script returns the Doc Definition object
                            const docDef = genFn(studentInfo, additionalData, qrCodeUrl);
                            
                            // Pass to Mocked PDFMake to generate bytes
                            window.pdfMake.createPdf(docDef);
                            
                        } catch(e) {
                            AndroidBridge.returnError("Execution Error: " + e.toString());
                        }
                    }
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