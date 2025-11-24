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
import org.json.JSONObject

class ReferenceJsFetcher {

    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"
    
    private val fetchedModules = mutableMapOf<String, String>()
    private val moduleVarNames = mutableMapOf<String, String>()

    suspend fun fetchResources(logger: (String) -> Unit, language: String, dictionary: Map<String, String>): PdfResources = withContext(Dispatchers.IO) {
        fetchedModules.clear()
        moduleVarNames.clear()
        
        try {
            logger("Finding entry point...")
            val indexHtml = fetchString("$baseUrl/")
            
            // Heuristic to find the main JS file from index.html
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""") 
                ?: throw Exception("Main JS missing in index.html")
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            
            // 1. Identify References7.js
            var refJsPath = findMatch(mainJsContent, """["']([^"']*References7\.[^"']+\.js)["']""")
            if (refJsPath == null) {
                // Fallback: Check StudentDocuments which often links to References
                val docsJsPath = findMatch(mainJsContent, """["']([^"']*StudentDocuments\.[^"']+\.js)["']""")
                if (docsJsPath != null) {
                    val docsContent = fetchString("$baseUrl/assets/${getName(docsJsPath)}")
                    refJsPath = findMatch(docsContent, """["']([^"']*References7\.[^"']+\.js)["']""")
                }
            }
            
            if (refJsPath == null) throw Exception("References7 JS missing in dependency chain")
            
            val entryFileName = getName(refJsPath)
            
            // Build the monolithic script
            val scriptBuilder = StringBuilder()

            // 2. Mock Browser/Vue environment
            scriptBuilder.append("const Vue = window.Vue;\n")
            // Destructure common Vue functions to global scope for minified scripts to find them
            scriptBuilder.append("const { ref, computed, reactive, unref, toRef, watch, onMounted, getCurrentInstance, defineComponent, resolveComponent, openBlock, createElementBlock, createBaseVNode, toDisplayString, createVNode, withCtx, createTextVNode } = Vue;\n")
            
            // 3. Recursively fetch modules
            logger("Fetching module tree starting from $entryFileName...")
            val entryVarName = fetchModuleRecursive(logger, entryFileName, scriptBuilder, dictionary, language)

            // 4. Dynamic PDF Generator Discovery
            var generatorVarName: String? = null
            
            // Search for the module defining the PDF content (looks for "pageSize" and "A4")
            for ((fileName, varName) in moduleVarNames) {
                val content = fetchedModules[fileName] ?: ""
                if (content.contains("pageSize") && (content.contains("A4") || content.contains("portrait"))) {
                    generatorVarName = varName
                    logger("Identified PDF Generator in: $fileName")
                    break
                }
            }
            
            val targetGenerator = generatorVarName ?: entryVarName
            
            scriptBuilder.append("\n// Expose to Android\n")
            scriptBuilder.append("window.RefDocGenerator = $targetGenerator.default || $targetGenerator;\n")
            scriptBuilder.append("window.RefComponent = $entryVarName.default || $entryVarName;\n")

            return@withContext PdfResources(scriptBuilder.toString())
        } catch (e: Exception) {
            logger("Fetch Error: ${e.message}")
            e.printStackTrace()
            return@withContext PdfResources("")
        }
    }

    private suspend fun fetchModuleRecursive(
        logger: (String) -> Unit, 
        fileName: String, 
        sb: StringBuilder,
        dictionary: Map<String, String>,
        language: String
    ): String {
        if (moduleVarNames.containsKey(fileName)) {
            return moduleVarNames[fileName]!!
        }

        // Generate a safe unique variable name for this module
        val uniqueVarName = "Mod_" + fileName.replace(Regex("[^a-zA-Z0-9]"), "_") + "_" + fileName.hashCode().toString().replace("-", "N")
        moduleVarNames[fileName] = uniqueVarName 

        // Skip non-JS files (CSS/Images)
        if (!fileName.endsWith(".js")) {
            sb.append("const $uniqueVarName = { default: {} }; // Mocked non-JS file\n")
            return uniqueVarName
        }

        var content = try {
            fetchString("$baseUrl/assets/$fileName")
        } catch (e: Exception) {
            logger("Failed fetch $fileName: ${e.message}")
            "export default {};"
        }

        // CRITICAL FIX: Check if we got HTML instead of JS (e.g. 404 fallback page)
        if (content.trim().startsWith("<")) {
            logger("Warning: $fileName returned HTML. Mocking as empty module.")
            sb.append("const $uniqueVarName = { default: {} }; // Mocked HTML response\n")
            return uniqueVarName
        }

        if (language == "en" && dictionary.isNotEmpty()) {
             dictionary.forEach { (ru, en) -> 
                 if (ru.length > 3) content = content.replace(ru, en) 
             }
        }
        
        fetchedModules[fileName] = content

        // --- Import Resolution ---
        // Capture: import ... from "path"; OR import "path";
        // Group 1: imports content (e.g. "{ a, b }" or "Default"). Null if side-effect only.
        // Group 2: path
        val importRegex = Regex("""import\s*(?:([\w\s{},*${'$'}]+)\s+from\s*)?["']([^"']+)["'];?""")
        val matches = importRegex.findAll(content).toList()
        
        val replacements = mutableListOf<Pair<String, String>>()
        
        for (match in matches) {
            val fullMatch = match.value
            val importClause = match.groups[1]?.value
            val path = match.groups[2]?.value ?: continue
            
            val depFileName = getName(path)
            val depVarName = fetchModuleRecursive(logger, depFileName, sb, dictionary, language)
            
            val replacementLine = StringBuilder()
            
            if (importClause == null) {
                // Side effect import: import "./foo.js"
                replacementLine.append("// Imported side-effect: $depFileName")
            } else if (importClause.contains("{")) {
                // Named imports: import { a, b as c } from ...
                val cleanClause = importClause.replace("{", "").replace("}", "").trim()
                val parts = cleanClause.split(",")
                val destructuring = parts.joinToString(",") { 
                    it.trim().replace(Regex("""\s+as\s+"""), ": ") 
                }
                replacementLine.append("const { $destructuring } = $depVarName;")
            } else {
                // Default import: import A from ...
                replacementLine.append("const ${importClause.trim()} = $depVarName.default || $depVarName;")
            }
            replacementLine.append("\n") // Safety newline
            
            replacements.add(fullMatch to replacementLine.toString())
        }

        var modifiedContent = content

        // Apply replacements
        for ((target, replacement) in replacements) {
            modifiedContent = modifiedContent.replace(target, replacement)
        }
        
        // Remove any remaining CSS imports or static assets that regex missed
        modifiedContent = modifiedContent.replace(Regex("""import\s+["'][^"']+\.(css|svg|png|jpg)["'];?"""), "// skipped asset\n")

        // --- Export Handling ---
        val namedExports = mutableListOf<String>()
        var hasDefaultExport = false

        if (modifiedContent.contains("export default")) {
            hasDefaultExport = true
            modifiedContent = modifiedContent.replaceFirst("export default", "const __default_export__ =")
        }

        // Handle: export { a, b as c }
        val exportRegex = Regex("""export\s*\{\s*([^}]+)\s*\};?""")
        modifiedContent = exportRegex.replace(modifiedContent) { matchRes ->
            val inner = matchRes.groupValues[1]
            inner.split(",").forEach { 
                val parts = it.trim().split(Regex("""\s+as\s+"""))
                if (parts.size == 2) {
                    namedExports.add("${parts[1]}: ${parts[0]}")
                } else {
                    namedExports.add(parts[0])
                }
            }
            "// exports extracted\n"
        }

        // Handle inline exports: export const x = ...
        modifiedContent = modifiedContent.replace(Regex("""export\s+(const|var|let|function|class)\s+"""), "$1 ")

        // Wrap in IIFE
        sb.append("\n// --- Module: $fileName ---\n")
        sb.append("const $uniqueVarName = (() => {\n")
        sb.append(modifiedContent)
        sb.append("\n\nreturn { ")
        if (hasDefaultExport) {
            sb.append("default: __default_export__, ")
        }
        if (namedExports.isNotEmpty()) {
            sb.append(namedExports.joinToString(", "))
        }
        sb.append(" };\n")
        sb.append("})();\n")

        return uniqueVarName
    }

    private fun fetchString(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            return response.body?.string() ?: ""
        }
    }

    private fun getName(path: String) = path.split('/').last().split('?').first()
    
    private fun findMatch(content: String, regex: String): String? {
        val match = Regex(regex).find(content)
        return if (match != null && match.groups.size > 1) match.groupValues[1] else null
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
        dictionary: Map<String, String> = emptyMap(),
        logCallback: (String) -> Unit
    ): ByteArray = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true // Often needed for robust JS execution
            
            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                    logCallback("[JS Ref] ${cm.message()} (Line ${cm.lineNumber()})")
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

            // HTML Shell
            val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <script src="https://unpkg.com/vue@3/dist/vue.global.prod.js"></script>
                <script src="https://cdnjs.cloudflare.com/ajax/libs/pdfmake/0.2.7/pdfmake.min.js"></script>
                <script src="https://cdnjs.cloudflare.com/ajax/libs/pdfmake/0.2.7/vfs_fonts.js"></script>
                <style>body { background: #fff; }</style>
            </head>
            <body>
            <div id="app"></div>
            <script>
                window.onerror = function(msg, url, line) { 
                    AndroidBridge.returnError(msg + " @ " + line); 
                };
                
                const propsData = {
                    student: $studentInfoJson,
                    info: $studentInfoJson,
                    license: $licenseInfoJson,
                    university: $univInfoJson,
                    qr: "$qrUrl"
                };
            </script>

            <script>
                try {
                    ${resources.combinedScript}
                } catch (e) {
                    AndroidBridge.returnError("Script Injection Error: " + e.message);
                    console.error(e);
                }
            </script>

            <script>
                function runGeneration() {
                    try {
                        AndroidBridge.log("JS: Starting generation...");
                        
                        if (typeof window.RefDocGenerator !== 'function') {
                             // Try to find it if auto-discovery failed
                             console.log("Auto-discovery failed, scanning global scope...");
                             // This is a last-ditch effort code if needed, usually auto-discovery works
                        }
                        
                        if (typeof window.RefDocGenerator !== 'function') {
                            throw "Generator function (RefDocGenerator) not found. Check if module with 'pageSize' was loaded.";
                        }

                        const Comp = window.RefComponent;
                        if (!Comp || !Comp.setup) {
                            throw "Vue Component not found.";
                        }
                        
                        const { reactive, unref } = Vue;
                        const props = reactive(propsData);
                        const context = { attrs: {}, slots: {}, emit: () => {} };
                        
                        // Run setup() to get derived data (like course number)
                        const state = Comp.setup(props, context);
                        
                        // Wait for any async watchers/promises in setup
                        setTimeout(() => {
                             try {
                                 let dataObj = null;
                                 
                                 // Smart scan for the data object in the component state
                                 // It usually contains 'edunum' or 'id'
                                 if (state && typeof state === 'object') {
                                     // 1. Check if state itself is the data
                                     if (unref(state.edunum)) {
                                         dataObj = {};
                                         for(const key in state) dataObj[key] = unref(state[key]);
                                     } 
                                     // 2. Check properties
                                     else {
                                         for (const key in state) {
                                             const val = unref(state[key]);
                                             if (val && typeof val === 'object' && (val.edunum || val.id)) {
                                                 dataObj = val;
                                                 break;
                                             }
                                         }
                                     }
                                 }

                                 // Fallback to props if logic failed (prevents crash, generates partial PDF)
                                 if (!dataObj) {
                                     AndroidBridge.log("JS: Warning - State scan failed, using raw props.");
                                     dataObj = propsData.student;
                                 }
                                 
                                 const finalData = JSON.parse(JSON.stringify(dataObj));
                                 
                                 AndroidBridge.log("JS: Generating PDF...");
                                 const docDef = window.RefDocGenerator(propsData.student, finalData, propsData.qr);
                                 
                                 const pdfDocGenerator = pdfMake.createPdf(docDef);
                                 pdfDocGenerator.getBase64(function(b64) {
                                     AndroidBridge.returnPdf(b64);
                                 });
                                 
                             } catch(err) {
                                 AndroidBridge.returnError("Logic Error: " + err.toString());
                             }
                        }, 1000); // Increased timeout slightly for safety
                        
                    } catch(e) { 
                        AndroidBridge.returnError("Setup Error: " + e.toString()); 
                    }
                }
                
                // Start
                runGeneration();
            </script>
            </body>
            </html>
            """
            
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Script runs inline
                }
            }
            webView.loadDataWithBaseURL("https://myedu.oshsu.kg/", html, "text/html", "UTF-8", null)
        }
    }
}