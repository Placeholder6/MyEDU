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
 * Fetches the dynamic URL of the References7 script.
 * Does NOT download the content to avoid syntax errors during string manipulation.
 */
class ReferenceJsFetcher {

    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"

    suspend fun fetchResources(logger: (String) -> Unit): PdfResources = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch Index HTML to find the main entry point
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""")
                ?: throw Exception("Main JS missing in index.html")
            
            // 2. Fetch Main JS content to find references to the target script
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            
            // 3. Find References7 script path (e.g. "References7.95b45fbc.12345.js")
            // The regex handles optional timestamp suffixes seen in your logs.
            var refJsPath = findMatch(mainJsContent, """["']([^"']*References7\.[^"']+\.js)["']""")
            
            // Fallback: Check inside StudentDocuments if not found in Main (lazy loaded chunks)
            if (refJsPath == null) {
                val docsJsPath = findMatch(mainJsContent, """["']([^"']*StudentDocuments\.[^"']+\.js)["']""")
                if (docsJsPath != null) {
                    val docsContent = fetchString("$baseUrl/assets/" + getName(docsJsPath))
                    refJsPath = findMatch(docsContent, """["']([^"']*References7\.[^"']+\.js)["']""")
                }
            }
            
            if (refJsPath == null) throw Exception("References7 script path could not be found.")
            
            // Construct full URL. The Regex returns relative path (e.g. ./References7.js or assets/References7.js)
            // We ensure it maps to https://myedu.oshsu.kg/assets/Filename.js
            val scriptName = getName(refJsPath)
            val fullScriptUrl = "$baseUrl/assets/$scriptName"
            
            logger("Resolved Script URL: $fullScriptUrl")
            
            return@withContext PdfResources(fullScriptUrl)
        } catch (e: Exception) {
            logger("Fetch Error: ${e.message}")
            e.printStackTrace()
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
 * Holds the URL to the script instead of the raw string content.
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
                // The remote script might make internal fetch calls (e.g. for images/logos) 
                // which require the auth token.
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                // Ensure domain matches the BASE_URL
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
                            // Clean up data URI prefix if present
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

                // We construct an HTML page that imports the remote script as a MODULE.
                // <base href> is critical: it makes relative imports inside the remote script 
                // (like import {x} from './vendor.js') resolve correctly to the server.
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
                    // These are global variables that our glue-code will use.
                    const studentInfo = $studentInfoJson;
                    const licenseInfo = $licenseInfoJson;
                    const univInfo = $univInfoJson;
                    const qrCodeUrl = "$qrUrl";
                    const lang = "$language";
                    const dictionary = $dictionaryJson;

                    // 2. Mocks for environment expected by some scripts
                    // Provide a simple date formatter if the script relies on a global formatter
                    var ${'$'} = function(d) { 
                        return { format: (f) => (d ? new Date(d) : new Date()).toLocaleDateString("$dateLocale") }; 
                    };
                    ${'$'}.locale = function() {};

                    // 3. Intercept PDFMake
                    // The remote script will eventually generate a docDefinition and call pdfMake.createPdf(docDef).
                    // We intercept this call to get the data back to Android.
                    const realPdfMake = window.pdfMake;
                    window.pdfMake = {
                        createPdf: function(docDef) {
                            console.log('Captured PDF Document Definition');
                            try {
                                // Use real pdfMake to generate Base64
                                realPdfMake.createPdf(docDef).getBase64(function(b64) {
                                    AndroidBridge.returnPdf(b64);
                                });
                            } catch(e) {
                                AndroidBridge.returnError("PDF Generation Failed: " + e.message);
                            }
                            // Return dummy object to prevent script crashes if it chains calls
                            return { open:()=>{}, print:()=>{}, download:()=>{} };
                        },
                        fonts: realPdfMake.fonts,
                        vfs: realPdfMake.vfs
                    };
                </script>

                <script type="module">
                    // 4. Import the Remote Script Dynamically
                    // This resolves 'import outside module' errors because we are inside type="module".
                    import * as RefModule from "${resources.scriptUrl}";
                    
                    AndroidBridge.log("Remote module imported.");

                    // 5. Inspect Exports to Find Logic
                    // We need to find the Vue component or the Generator function.
                    // Usually it is the 'default' export.
                    
                    let targetComponent = RefModule.default;
                    
                    // Fallback: iterate exports if default is missing
                    if (!targetComponent) {
                        for (const key in RefModule) {
                            // Look for something that looks like a Vue component (has setup function or methods)
                            if (RefModule[key] && (RefModule[key].setup || RefModule[key].methods)) {
                                targetComponent = RefModule[key];
                                break;
                            }
                        }
                    }

                    if (!targetComponent) {
                        AndroidBridge.returnError("Could not find Vue Component in script exports.");
                    } else {
                        startGeneration(targetComponent);
                    }

                    // 6. "Glue" Logic
                    // We try to run the component's logic without mounting the full Vue app.
                    // This fulfills "No Hardcoded Logic" by using the component's own internal math.
                    
                    function startGeneration(Component) {
                        try {
                            AndroidBridge.log("Attempting to run component logic...");
                            
                            // Mock Props
                            const props = {
                                student: studentInfo,
                                license: licenseInfo,
                                university: univInfo
                            };

                            // If using Vue Composition API (setup function)
                            if (typeof Component.setup === 'function') {
                                // We invoke setup() with mock props and context
                                const context = { attrs: {}, slots: {}, emit: () => {} };
                                const setupResult = Component.setup(props, context);
                                
                                // The result should contain the data/functions used in the template
                                // Look for the function that triggers PDF generation (often named 'print', 'generate', 'openPdf')
                                
                                // If the script calculates data (semester, course) in setup, it's in setupResult.
                                // We might need to call the generation function explicitly.
                                
                                if (setupResult.print) setupResult.print();
                                else if (setupResult.generatePdf) setupResult.generatePdf();
                                else if (setupResult.createPdf) setupResult.createPdf();
                                else {
                                    // If no explicit function, assume it runs on mount or data readiness.
                                    // We might need to look for the doc definition builder manually in the result.
                                    // OR: The component might expect us to trigger it.
                                    
                                    // Fallback: If we can't find the trigger, we might have to instantiate logic manually
                                    // using the code we found in logs, but using data derived from setupResult.
                                    
                                    // For now, let's try to find ANY function in the result and call it if it looks like a generator.
                                    // Or just log available keys to debug.
                                    AndroidBridge.log("Setup keys: " + Object.keys(setupResult).join(", "));
                                }
                            } 
                            // If using Vue Options API (methods)
                            else if (Component.methods) {
                                // Create a mock 'this' context combining props and data
                                const ctx = { 
                                    ...props, 
                                    ...studentInfo, // flatten for easier access if script expects it
                                    $t: (k) => dictionary[k] || k, // Mock translation function
                                    qrCodeUrl: qrCodeUrl
                                };
                                
                                // Bind methods to context
                                for (let m in Component.methods) {
                                    Component.methods[m] = Component.methods[m].bind(ctx);
                                }
                                
                                // Try to call the print method
                                if (Component.methods.print) Component.methods.print();
                                else if (Component.methods.generate) Component.methods.generate();
                                else {
                                    // If specific method name is unknown, we can't guess easily.
                                    // However, standard vue logic usually has a method triggered by the UI.
                                    AndroidBridge.returnError("No print method found in component options.");
                                }
                            } else {
                                // Logic is neither setup nor methods? 
                                // It might be a direct function export (not a component).
                                if (typeof Component === 'function') {
                                    // It might be the generator function itself!
                                    // Try calling it with our data
                                    const d = { id: "Generated", edunum: "Dynamic", date: new Date().toLocaleDateString() };
                                    const docDef = Component(studentInfo, d, qrCodeUrl);
                                    window.pdfMake.createPdf(docDef);
                                }
                            }
                            
                        } catch(e) {
                            AndroidBridge.returnError("Glue Logic Error: " + e.toString());
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