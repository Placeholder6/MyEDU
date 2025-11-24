package com.example.myedu

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64 // Added missing import
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ReferenceJsFetcher {

    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"
    // Cache to prevent re-fetching and infinite loops in recursion
    private val fetchedModules = mutableMapOf<String, String>()

    suspend fun fetchResources(logger: (String) -> Unit, language: String, dictionary: Map<String, String>): PdfResources = withContext(Dispatchers.IO) {
        fetchedModules.clear()
        try {
            logger("Finding entry point...")
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""") 
                ?: throw Exception("Main JS missing")
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            
            // 1. Identify the logic file for Reference (Form 8)
            var refJsPath = findMatch(mainJsContent, """["']([^"']*References7\.[^"']+\.js)["']""")
            if (refJsPath == null) {
                val docsJsPath = findMatch(mainJsContent, """["']([^"']*StudentDocuments\.[^"']+\.js)["']""")
                if (docsJsPath != null) {
                    val docsContent = fetchString("$baseUrl/assets/${getName(docsJsPath)}")
                    refJsPath = findMatch(docsContent, """["']([^"']*References7\.[^"']+\.js)["']""")
                }
            }
            if (refJsPath == null) throw Exception("References7 JS missing")
            
            val entryFileName = getName(refJsPath)
            val entryModuleName = "RefModule"
            val scriptBuilder = StringBuilder()

            // 2. Polyfill/Mock Vue global from index.js imports
            // We will load Vue from CDN in the WebView, so we map the imports to window.Vue
            scriptBuilder.append("const Vue = window.Vue;\n")
            scriptBuilder.append("const { ref, computed, reactive, unref, toRef, watch, onMounted, getCurrentInstance } = Vue;\n")

            // 3. Recursively fetch and bundle all JS dependencies
            logger("Fetching $entryFileName and dependencies...")
            fetchModuleRecursive(logger, entryFileName, entryModuleName, scriptBuilder, dictionary, language)

            // 4. Find and Expose the PDF Generator Function
            // It might be in the entry module or one of its dependencies. We search all fetched content.
            var generatorFound = false
            val generatorRegex = Regex("""const\s+(\w+)\s*=\s*\([a-zA-Z0-9,]*\)\s*=>\s*\{[^}]*pageSize:["']A4["']""")
            
            for ((name, content) in fetchedModules) {
                val match = generatorRegex.find(content)
                if (match != null) {
                    val funcName = match.groupValues[1]
                    // Expose it globally so we can call it
                    scriptBuilder.append("\nwindow.RefDocGenerator = $funcName;\n")
                    generatorFound = true
                    logger("Found Generator in $name")
                    break
                }
            }
            if (!generatorFound) logger("Warning: Generator function not found in fetched modules.")

            // 5. Expose the Component Logic
            // References7.js exports the Vue component. We need it to calculate the data (course, date, etc.)
            scriptBuilder.append("\nwindow.RefComponent = $entryModuleName.default || $entryModuleName;\n")

            return@withContext PdfResources(scriptBuilder.toString())
        } catch (e: Exception) {
            logger("Ref Fetch Error: ${e.message}")
            e.printStackTrace()
            return@withContext PdfResources("")
        }
    }

    private suspend fun fetchModuleRecursive(
        logger: (String) -> Unit, 
        fileName: String, 
        varName: String, 
        sb: StringBuilder,
        dictionary: Map<String, String>,
        language: String
    ) {
        if (fetchedModules.containsKey(fileName)) return // Already fetched
        
        var content = try {
            fetchString("$baseUrl/assets/$fileName")
        } catch (e: Exception) {
            logger("Failed to fetch $fileName: ${e.message}")
            return
        }

        // Translate strings in the raw JS if English is requested
        if (language == "en" && dictionary.isNotEmpty()) {
             dictionary.forEach { (ru, en) -> 
                 if (ru.length > 2) content = content.replace(ru, en) 
             }
        }
        fetchedModules[fileName] = content

        // Analyze imports to fetch children
        // Matches: import { X } from "./file.js" OR import X from "./file.js"
        val importRegex = Regex("""import\s*(?:\{([^}]+)\}|([a-zA-Z0-9_]+))?\s*from\s*["']\./([^"']+\.js)["']""")
        val imports = importRegex.findAll(content).toList()

        // Recurse first (bottom-up loading)
        for (match in imports) {
            val depPath = match.groupValues[3]
            val depFileName = getName(depPath)
            val depVarName = depFileName.replace(Regex("[^a-zA-Z0-9]"), "_") // Safe JS variable name
            fetchModuleRecursive(logger, depFileName, depVarName, sb, dictionary, language)
        }

        // Rewrite the current module to be a variable
        // 1. Remove the import statements
        var cleanContent = content.replace(Regex("""import\s*.*?\s*from\s*["'].*?["'];?"""), "")

        // 2. Handle Vue/Vendor imports (imports from index.js usually)
        // We replace `import ... from "./index.hash.js"` with destructuring from window.Vue
        val vendorImportRegex = Regex("""import\s*\{([^}]+)\}\s*from\s*["']\./index\.[^"']+\.js["']""")
        cleanContent = cleanContent.replace(vendorImportRegex) { 
            val importedProps = it.groupValues[1]
            "const { $importedProps } = Vue;"
        }

        // 3. Convert exports to a return object
        // `export default X` -> `return X`
        // `export { A as B }` -> `return { B: A }`
        val exportDefaultRegex = Regex("""export\s+default\s+""")
        if (exportDefaultRegex.containsMatchIn(cleanContent)) {
             cleanContent = cleanContent.replace(exportDefaultRegex, "return ")
        } else {
             cleanContent = cleanContent.replace(Regex("""export\s*\{"""), "return {")
        }

        // 4. Inject dependencies at the top of the module closure
        val header = StringBuilder()
        for (match in imports) {
            val namedImports = match.groupValues[1] // "a, b as c"
            val defaultImport = match.groupValues[2] // "MyModule"
            val depPath = match.groupValues[3]
            val depVarName = getName(depPath).replace(Regex("[^a-zA-Z0-9]"), "_")

            if (defaultImport.isNotEmpty()) {
                header.append("const $defaultImport = $depVarName.default || $depVarName;\n")
            } else if (namedImports.isNotEmpty()) {
                // Destructure named imports
                header.append("const { $namedImports } = $depVarName;\n")
            }
        }

        // Wrap in IIFE and assign to unique variable
        sb.append("const $varName = (() => {\n$header\n$cleanContent\n})();\n")
    }

    private fun fetchString(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            return response.body?.string() ?: ""
        }
    }

    private fun getName(path: String) = path.split('/').last()
    private fun findMatch(content: String, regex: String): String? = Regex(regex).find(content)?.groupValues?.get(1)
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
        dictionary: Map<String, String> = emptyMap(),
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
                    if (continuation.isActive) continuation.resumeWith(Result.failure(Exception("JS Error: $msg")))
                }
                @JavascriptInterface
                fun log(msg: String) = logCallback(msg)
            }, "AndroidBridge")

            val dateLocale = if (language == "en") "en-US" else "ru-RU"

            // We use Vue CDN to handle the component logic
            val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <script src="https://unpkg.com/vue@3/dist/vue.global.prod.js"></script>
                <script src="https://cdnjs.cloudflare.com/ajax/libs/pdfmake/0.2.7/pdfmake.min.js"></script>
                <script src="https://cdnjs.cloudflare.com/ajax/libs/pdfmake/0.2.7/vfs_fonts.js"></script>
            </head>
            <body>
            <div id="app"></div>
            <script>
                window.onerror = function(msg, url, line) { AndroidBridge.returnError(msg + " @ " + line); };
                
                const propsData = {
                    student: $studentInfoJson,
                    info: $studentInfoJson,
                    license: $licenseInfoJson,
                    university: $univInfoJson,
                    qr: "$qrUrl"
                };
            </script>

            <script>
                // Load the bundled scripts (References7.js and its dependencies)
                ${resources.combinedScript}
            </script>

            <script>
                // Helper to extract computed data from the Vue Component
                function runGeneration() {
                    try {
                        AndroidBridge.log("JS: Starting generation...");
                        const Comp = window.RefComponent;
                        
                        if (!Comp || !Comp.setup) {
                            throw "Component setup not found. Check if RefModule loaded correctly.";
                        }
                        
                        // We manually invoke the setup() function of the component to get its reactive state.
                        // This avoids needing a full DOM mount, although mounting is also an option.
                        const { reactive, unref } = Vue;
                        const props = reactive(propsData);
                        const context = { attrs: {}, slots: {}, emit: () => {} };
                        
                        // Run setup
                        const state = Comp.setup(props, context);
                        
                        // Wait briefly for any watchers/computeds to settle
                        setTimeout(() => {
                             try {
                                 // The component calculates 'course' (edunum), 'docId' (id), etc.
                                 // We scan the returned state to find the object matching { id, edunum, date, adress }
                                 // or construct it from individual fields.
                                 
                                 let dataObj = null;
                                 
                                 // Heuristic: Find the object that contains 'edunum' and 'id'
                                 for (const key in state) {
                                     const val = unref(state[key]);
                                     if (val && typeof val === 'object' && val.edunum && val.id) {
                                         dataObj = val;
                                         break;
                                     }
                                 }
                                 
                                 // Fallback: Construct from common variable names in the minified code
                                 if (!dataObj) {
                                     dataObj = {
                                         id: unref(state.docId || state.id || state.doc_id),
                                         edunum: unref(state.course || state.edunum || state.courseStr),
                                         date: unref(state.date || state.currentDate),
                                         adress: unref(state.address || state.adress || state.univAddress)
                                     };
                                 }
                                 
                                 if (!dataObj.edunum) throw "Could not calculate document data. State keys: " + Object.keys(state).join(",");
                                 
                                 AndroidBridge.log("JS: Calculated: " + JSON.stringify(dataObj));
                                 
                                 // Call the extracted PDF Generator function
                                 if (typeof window.RefDocGenerator !== 'function') throw "RefDocGenerator not found.";
                                 
                                 const docDef = window.RefDocGenerator(propsData.student, dataObj, propsData.qr);
                                 pdfMake.createPdf(docDef).getBase64(b64 => AndroidBridge.returnPdf(b64));
                                 
                             } catch(err) {
                                 AndroidBridge.returnError(err.toString());
                             }
                        }, 100);
                        
                    } catch(e) { AndroidBridge.returnError(e.toString()); }
                }
                
                runGeneration();
            </script>
            </body>
            </html>
            """
            
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Generation starts automatically via runGeneration() call in HTML
                }
            }
            webView.loadDataWithBaseURL("https://myedu.oshsu.kg/", html, "text/html", "UTF-8", null)
        }
    }
}