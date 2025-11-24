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
import java.util.regex.Pattern

class ReferenceJsFetcher {

    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"
    
    // Cache to store processed content: FileName -> content
    private val fetchedModules = mutableMapOf<String, String>()
    // Map: FileName -> UniqueVariableName
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
            // It might be imported by main or by StudentDocuments
            var refJsPath = findMatch(mainJsContent, """["']([^"']*References7\.[^"']+\.js)["']""")
            if (refJsPath == null) {
                val docsJsPath = findMatch(mainJsContent, """["']([^"']*StudentDocuments\.[^"']+\.js)["']""")
                if (docsJsPath != null) {
                    val docsContent = fetchString("$baseUrl/assets/${getName(docsJsPath)}")
                    refJsPath = findMatch(docsContent, """["']([^"']*References7\.[^"']+\.js)["']""")
                }
            }
            
            if (refJsPath == null) throw Exception("References7 JS missing in chain")
            
            val entryFileName = getName(refJsPath)
            
            // We will build a single JS string containing all dependencies
            val scriptBuilder = StringBuilder()

            // 2. Polyfill/Mock Vue global imports so the scripts can run
            scriptBuilder.append("const Vue = window.Vue;\n")
            scriptBuilder.append("const { ref, computed, reactive, unref, toRef, watch, onMounted, getCurrentInstance, defineComponent } = Vue;\n")
            
            // 3. Recursively fetch and bundle
            logger("Fetching module tree starting from $entryFileName...")
            val entryVarName = fetchModuleRecursive(logger, entryFileName, scriptBuilder, dictionary, language)

            // 4. Find and Expose the PDF Generator Function
            // We look for a module that mentions 'pageSize' and 'A4'/ 'portrait', typical for pdfMake definitions
            var generatorVarName: String? = null
            
            for ((fileName, content) in fetchedModules) {
                if (content.contains("pageSize") && (content.contains("A4") || content.contains("portrait"))) {
                    val varName = moduleVarNames[fileName]
                    if (varName != null) {
                        generatorVarName = varName
                        logger("Identified PDF Generator in module: $fileName")
                        break
                    }
                }
            }
            
            // Fallback: use the entry module if no specific generator found (unlikely if structure holds)
            val targetGenerator = generatorVarName ?: entryVarName
            
            scriptBuilder.append("\n// Expose for AndroidBridge\n")
            scriptBuilder.append("window.RefDocGenerator = $targetGenerator.default || $targetGenerator;\n")
            scriptBuilder.append("window.RefComponent = $entryVarName.default || $entryVarName;\n")

            return@withContext PdfResources(scriptBuilder.toString())
        } catch (e: Exception) {
            logger("Ref Fetch Error: ${e.message}")
            e.printStackTrace()
            return@withContext PdfResources("")
        }
    }

    // Returns the variable name of the fetched module
    private suspend fun fetchModuleRecursive(
        logger: (String) -> Unit, 
        fileName: String, 
        sb: StringBuilder,
        dictionary: Map<String, String>,
        language: String
    ): String {
        // If already fetched, return its existing variable name
        if (moduleVarNames.containsKey(fileName)) {
            return moduleVarNames[fileName]!!
        }

        val uniqueVarName = "Mod_" + fileName.replace(Regex("[^a-zA-Z0-9]"), "_") + "_" + fileName.hashCode().toString().replace("-", "N")
        moduleVarNames[fileName] = uniqueVarName // Reserve name to prevent cycles

        var content = try {
            fetchString("$baseUrl/assets/$fileName")
        } catch (e: Exception) {
            logger("Failed to fetch $fileName: ${e.message}")
            "export default {};" // Return empty module on fail to keep going
        }

        // Apply dictionary replacements for translations if needed
        if (language == "en" && dictionary.isNotEmpty()) {
             dictionary.forEach { (ru, en) -> 
                 if (ru.length > 3) content = content.replace(ru, en) 
             }
        }
        
        fetchedModules[fileName] = content

        // --- dependency resolution ---
        // We need to parse imports BEFORE writing this module's content to the StringBuilder
        // because dependencies must be defined before they are used.
        
        // Regex matches: import { a, b as c } from "./foo.js"; OR import Foo from "./foo.js";
        val importRegex = Regex("""import\s*(?:(\w+)|\{\s*([^}]+)\s*\})?\s*from\s*["']([^"']+\.js)["'];?""")
        val matches = importRegex.findAll(content).toList()
        
        // We will build a map of replacements for the content string
        val replacements = mutableListOf<Pair<String, String>>()
        
        for (match in matches) {
            val fullMatch = match.value
            val defaultImport = match.groups[1]?.value
            val namedImportsStr = match.groups[2]?.value
            val path = match.groups[3]?.value ?: continue
            
            val depFileName = getName(path)
            
            // Recursively fetch dependency
            val depVarName = fetchModuleRecursive(logger, depFileName, sb, dictionary, language)
            
            // Create the code to replace the import statement
            // "const { a, b: c } = Mod_foo_123;" or "const Foo = Mod_foo_123.default;"
            val replacementLine = StringBuilder()
            
            if (defaultImport != null) {
                replacementLine.append("const $defaultImport = $depVarName.default || $depVarName;")
            } else if (namedImportsStr != null) {
                // Fix "as" syntax: "x as y" -> "x: y" for destructuring
                val fixedDestructure = namedImportsStr.split(",").joinToString(",") { part ->
                    part.trim().replace(Regex("""\s+as\s+"""), ": ")
                }
                replacementLine.append("const { $fixedDestructure } = $depVarName;")
            } else {
                // Side effect import only
                replacementLine.append("// Imported $depFileName")
            }
            
            replacements.add(fullMatch to replacementLine.toString())
        }

        // --- content transformation ---
        var modifiedContent = content

        // 1. Apply import replacements
        for ((target, replacement) in replacements) {
            modifiedContent = modifiedContent.replace(target, replacement)
        }

        // 2. Handle Exports
        // We strip "export" keywords and capture what they exported to return it at the end of IIFE
        
        val namedExports = mutableListOf<String>()
        var hasDefaultExport = false

        // Handle: export default ...
        if (modifiedContent.contains("export default")) {
            hasDefaultExport = true
            // Replace "export default" with "const __default_export__ ="
            // Note: standardizing to a const allows it to handle objects, functions, classes etc.
            modifiedContent = modifiedContent.replaceFirst("export default", "const __default_export__ =")
        }

        // Handle: export { a, b as c }
        val exportRegex = Regex("""export\s*\{\s*([^}]+)\s*\};?""")
        modifiedContent = exportRegex.replace(modifiedContent) { matchRes ->
            val inner = matchRes.groupValues[1]
            inner.split(",").forEach { 
                // "a as b" -> export b (value is a)
                // For the return object we need "b: a"
                val parts = it.trim().split(Regex("""\s+as\s+"""))
                if (parts.size == 2) {
                    namedExports.add("${parts[1]}: ${parts[0]}")
                } else {
                    namedExports.add(parts[0])
                }
            }
            "// exports handled" // Remove the line
        }

        // Handle: export const x = ... (Inline exports) - stripping 'export' keyword
        // This regex removes 'export ' but keeps 'const x = ...'
        modifiedContent = modifiedContent.replace(Regex("""export\s+(const|var|let|function|class)\s+"""), "$1 ")

        // --- Final Assembly ---
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
                ${resources.combinedScript}
            </script>

            <script>
                function runGeneration() {
                    try {
                        AndroidBridge.log("JS: Starting generation...");
                        
                        if (typeof window.RefDocGenerator !== 'function') {
                             throw "RefDocGenerator not found. Check loaded modules.";
                        }

                        const Comp = window.RefComponent;
                        if (!Comp || !Comp.setup) {
                            throw "Component setup not found.";
                        }
                        
                        const { reactive, unref } = Vue;
                        const props = reactive(propsData);
                        const context = { attrs: {}, slots: {}, emit: () => {} };
                        
                        // Execute component logic to calculate derived fields (course number, etc.)
                        const state = Comp.setup(props, context);
                        
                        setTimeout(() => {
                             try {
                                 let dataObj = null;
                                 
                                 // Scan state to find the data object (contains course, date, docId)
                                 for (const key in state) {
                                     const val = unref(state[key]);
                                     if (val && typeof val === 'object' && val.edunum && val.id) {
                                         dataObj = val;
                                         break;
                                     }
                                 }
                                 
                                 if (!dataObj) {
                                     if (unref(state.id) && unref(state.edunum)) {
                                         dataObj = {};
                                         for(const key in state) dataObj[key] = unref(state[key]);
                                     }
                                 }

                                 if (!dataObj) throw "Could not locate document data in component state.";
                                 
                                 const finalData = JSON.parse(JSON.stringify(dataObj));
                                 
                                 AndroidBridge.log("JS: Generating PDF...");
                                 const docDef = window.RefDocGenerator(propsData.student, finalData, propsData.qr);
                                 pdfMake.createPdf(docDef).getBase64(b64 => AndroidBridge.returnPdf(b64));
                                 
                             } catch(err) {
                                 AndroidBridge.returnError("Logic Error: " + err.toString());
                             }
                        }, 500); 
                        
                    } catch(e) { AndroidBridge.returnError("Setup Error: " + e.toString()); }
                }
                
                runGeneration();
            </script>
            </body>
            </html>
            """
            
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Generation starts via script
                }
            }
            webView.loadDataWithBaseURL("https://myedu.oshsu.kg/", html, "text/html", "UTF-8", null)
        }
    }
}