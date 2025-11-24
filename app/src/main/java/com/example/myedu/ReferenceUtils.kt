package com.example.myedu

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64 // ADDED: Fixes Unresolved reference: Base64
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
    
    private val fetchedModules = mutableMapOf<String, String>()
    // Key: FileName, Value: Assigned JS variable name
    private val moduleVarNames = mutableMapOf<String, String>() 

    suspend fun fetchResources(logger: (String) -> Unit, language: String, dictionary: Map<String, String>): PdfResources = withContext(Dispatchers.IO) {
        fetchedModules.clear()
        moduleVarNames.clear()
        
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

            // 2. Polyfill/Mock Vue global imports (index.hash.js imports)
            scriptBuilder.append("const Vue = window.Vue;\n")
            scriptBuilder.append("const { ref, computed, reactive, unref, toRef, watch, onMounted, getCurrentInstance } = Vue;\n")

            // 3. Recursively fetch and bundle all JS dependencies
            logger("Fetching $entryFileName and dependencies...")
            fetchModuleRecursive(logger, entryFileName, entryModuleName, scriptBuilder, dictionary, language)

            // 4. Find and Expose the PDF Generator Function
            // Look for a module that contains the pdfmake document definition keywords
            var generatorVarName: String? = null
            
            for ((fileName, content) in fetchedModules) {
                // Heuristic: Check for pdfmake structure markers
                if (content.contains("pageSize") && (content.contains("A4") || content.contains("portrait"))) {
                    val varName = moduleVarNames[fileName]
                    // If a module explicitly exports the document generator (which usually returns an object that fits this heuristic)
                    if (varName != null) {
                        generatorVarName = varName
                        logger("Identified PDF Generator in module: $fileName")
                        break
                    }
                }
            }
            
            if (generatorVarName != null) {
                // Expose the identified module's default export as the global generator
                scriptBuilder.append("\nwindow.RefDocGenerator = $generatorVarName.default || $generatorVarName;\n")
            } else {
                logger("Warning: Generator function not definitively found. Trying default export of entry module.")
                scriptBuilder.append("\nwindow.RefDocGenerator = $entryModuleName.default || $entryModuleName;\n")
            }

            // 5. Expose the Vue Component Logic (References7.js exports the Vue component)
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
        if (fetchedModules.containsKey(fileName)) return
        
        // --- Step 1: Fetch and Translate ---
        var content = try {
            fetchString("$baseUrl/assets/$fileName")
        } catch (e: Exception) {
            logger("Failed to fetch $fileName: ${e.message}")
            return
        }

        if (language == "en" && dictionary.isNotEmpty()) {
             dictionary.forEach { (ru, en) -> 
                 if (ru.length > 3) content = content.replace(ru, en) 
             }
        }
        fetchedModules[fileName] = content
        moduleVarNames[fileName] = varName // Store the variable name for later lookup

        // --- Step 2: Recursively fetch dependencies ---
        // Matches: import Name from './file.js' OR import { A, B } from './file.js'
        val importRegex = Regex("""import\s*(?:(\w+)\s+from\s*|(?:\s*\{([^}]+)\}\s*from\s*))?["']\./([^"']+\.js)["']""")
        val imports = importRegex.findAll(content).toList()

        for (match in imports) {
            val depPath = match.groupValues[3]
            val depFileName = getName(depPath)
            // Ensure unique var name: fileName + part of hash
            val depVarName = depFileName.replace(Regex("[^a-zA-Z0-9]"), "_") + "_" + depFileName.hashCode().toString().takeLast(4)
            fetchModuleRecursive(logger, depFileName, depVarName, sb, dictionary, language)
        }

        // --- Step 3: Parse and Construct Exports/Return Statement ---
        val exportMap = mutableMapOf<String, String>() 
        
        // 3a. Handle Default Export: captures 'export default Variable;'
        val defaultExportMatch = Regex("""export\s+default\s+([a-zA-Z0-9_$]+)""").find(content)
        if (defaultExportMatch != null) {
            exportMap["default"] = defaultExportMatch.groupValues[1].trim()
        }
        
        // 3b. Handle Named Exports: captures 'export { A, B as C }'
        val namedExportsMatches = Regex("""export\s*\{([^}]+)\}""").findAll(content).toList()
        namedExportsMatches.forEach { match ->
            match.groupValues[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { exportItem ->
                val parts = exportItem.split(Regex("""\s+as\s+"""))
                if (parts.size == 2) {
                    // { local as export } -> export: local
                    exportMap[parts[1].trim()] = parts[0].trim()
                } else {
                    // { local } -> local: local
                    exportMap[parts[0].trim()] = parts[0].trim()
                }
            }
        }
        
        // 3c. Create the Return Statement
        val exportsList = exportMap.entries.joinToString(", ") { 
            if (it.key == it.value) it.key else "${it.key}: ${it.value}"
        }
        val returnStatement = if (exportsList.isEmpty()) "return {};" else "return { $exportsList };"

        // --- Step 4: Clean Content & Assemble IIFE ---
        
        // Create header with local imports
        val header = StringBuilder()
        imports.forEach { match ->
            val defaultImport = match.groupValues[1]
            val namedImports = match.groupValues[2]
            val depPath = match.groupValues[3]
            val depFileName = getName(depPath)
            val depVarName = moduleVarNames[depFileName] // Retrieve the stored variable name

            if (depVarName != null) {
                 if (defaultImport.isNotEmpty()) {
                     // import Name from './file.js' -> const Name = depVarName.default || depVarName
                     header.append("const $defaultImport = $depVarName.default || $depVarName;\n")
                 }
                 if (namedImports.isNotEmpty()) {
                     // import { A, B } from './file.js' -> const { A, B } = depVarName
                     header.append("const { $namedImports } = $depVarName;\n")
                 }
            }
        }

        // Strip out all imports, exports, and comments/garbage from the content body
        var strippedContent = content
            .replace(Regex("""import\s*.*?\s*from\s*["'].*?["'];?"""), "") 
            .replace(Regex("""export\s+default\s+.*?;?"""), "") 
            .replace(Regex("""export\s*\{[^}]*\}"""), "")
            .replace(Regex("""/\*![\s\S]*?\*/"""), "") // Remove multiline comments (including license blocks)
            .replace(Regex("""\/\/.*?\n"""), "") // Remove single-line comments
            // Remove common module boilerplate left by minifiers 
            .replace(Regex("""["']\.\/index\.[^"']+\.js["']"""), "") 
            .replace(Regex("""Vue\.global\.prod\.js"""), "") 
            // Handle the likely source of the '--' error: an abandoned Vue template/HTML comment 
            .replace(Regex(""""""), "")


        // Assemble the module IIFE (Immediately Invoked Function Expression)
        sb.append("const $varName = (() => {\n$header\n$strippedContent\n$returnStatement\n})();\n")
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

// The ReferencePdfGenerator class remains the same as its logic was already updated 
// to use the dynamically extracted JavaScript functions (window.RefComponent and window.RefDocGenerator)
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
                        
                        const { reactive, unref } = Vue;
                        const props = reactive(propsData);
                        const context = { attrs: {}, slots: {}, emit: () => {} };
                        
                        // Run setup
                        const state = Comp.setup(props, context);
                        
                        // Wait briefly for any watchers/computeds to settle
                        setTimeout(() => {
                             try {
                                 
                                 let dataObj = null;
                                 
                                 // Heuristic: Find the object that contains 'edunum' and 'id'
                                 for (const key in state) {
                                     const val = unref(state[key]);
                                     if (val && typeof val === 'object' && val.edunum && val.id) {
                                         dataObj = val;
                                         break;
                                     }
                                 }
                                 
                                 // Fallback: Construct from common variable names in the state
                                 if (!dataObj) {
                                     dataObj = {
                                         id: unref(state.docId || state.id || state.doc_id),
                                         edunum: unref(state.course || state.edunum || state.courseStr),
                                         date: unref(state.date || state.currentDate),
                                         adress: unref(state.address || state.adress || state.univAddress)
                                     };
                                 }
                                 
                                 if (!dataObj || !dataObj.id) {
                                     // Final check: check if state itself has the props
                                     if (unref(state.id) && unref(state.edunum)) dataObj = state;
                                     else throw "Could not calculate document data. State keys: " + Object.keys(state).join(",");
                                 }
                                 
                                 // Unref all properties before passing them to the generator
                                 const finalData = {};
                                 for(const key in dataObj) { finalData[key] = unref(dataObj[key]); }
                                 
                                 AndroidBridge.log("JS: Calculated: " + JSON.stringify(finalData));
                                 
                                 // Call the extracted PDF Generator function
                                 if (typeof window.RefDocGenerator !== 'function') throw "RefDocGenerator function not found in window object.";
                                 
                                 const docDef = window.RefDocGenerator(propsData.student, finalData, propsData.qr);
                                 pdfMake.createPdf(docDef).getBase64(b64 => AndroidBridge.returnPdf(b64));
                                 
                             } catch(err) {
                                 AndroidBridge.returnError(err.toString());
                             }
                        }, 200);
                        
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