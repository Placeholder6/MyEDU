//
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

            // 2. Polyfill/Mock Vue global imports
            scriptBuilder.append("const Vue = window.Vue;\n")
            scriptBuilder.append("const { ref, computed, reactive, unref, toRef, watch, onMounted, getCurrentInstance } = Vue;\n")

            logger("Fetching $entryFileName and dependencies...")
            fetchModuleRecursive(logger, entryFileName, entryModuleName, scriptBuilder, dictionary, language)

            // 4. Find and Expose the PDF Generator Function
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
            
            if (generatorVarName != null) {
                scriptBuilder.append("\nwindow.RefDocGenerator = $generatorVarName.default || $generatorVarName;\n")
            } else {
                scriptBuilder.append("\nwindow.RefDocGenerator = $entryModuleName.default || $entryModuleName;\n")
            }

            // 5. Expose the Vue Component Logic
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
        val depVarName = fileName.replace(Regex("[^a-zA-Z0-9]"), "_") + "_" + fileName.hashCode().toString().takeLast(4)
        moduleVarNames[fileName] = depVarName

        // List of ranges to remove from content (imports/exports)
        // Using index ranges avoids the "Unexpected token" error caused by replacing identical strings
        val removalRanges = mutableListOf<IntRange>()
        val headerImports = StringBuilder()

        // --- Handle Imports ---
        // Regex to capture: import ... from '...'
        // Use getOrNull via manual group indexing in loop to fix "No group 1"
        val importRegex = Regex("""import\s*(?:(\w+)\s+from\s*|(?:\s*\{([^}]+)\}\s*from\s*))?["']([^"']+\.js)["']""")
        val imports = importRegex.findAll(content).toList()

        for (match in imports) {
            removalRanges.add(match.range) 

            // Group 3 is the path. Check strictly.
            val depPath = if (match.groups.size > 3) match.groupValues[3] else continue
            val depFileName = getName(depPath)
            val nextVarName = depFileName.replace(Regex("[^a-zA-Z0-9]"), "_") + "_" + depFileName.hashCode().toString().takeLast(4)
            
            // Recurse first
            fetchModuleRecursive(logger, depFileName, nextVarName, sb, dictionary, language)

            // Generate header mapping
            val defaultImport = if (match.groups.size > 1) match.groupValues[1] else ""
            val namedImports = if (match.groups.size > 2) match.groupValues[2] else ""
            
            val targetVar = moduleVarNames[depFileName] ?: nextVarName

            if (defaultImport.isNotEmpty()) {
                headerImports.append("const $defaultImport = $targetVar.default || $targetVar;\n")
            }
            if (namedImports.isNotEmpty()) {
                // Handle "as" aliasing: "a as b" -> "a: b"
                val fixedDestructuring = namedImports.split(",").joinToString(",") { 
                    it.replace(Regex("""\s+as\s+"""), ": ") 
                }
                headerImports.append("const { $fixedDestructuring } = $targetVar;\n")
            }
        }

        // --- Handle Exports ---
        val exportMap = mutableMapOf<String, String>()
        
        // Named exports: export { a, b as c }
        val namedExportRegex = Regex("""export\s*\{([\s\S]*?)\}\s*;?""")
        namedExportRegex.findAll(content).forEach { match ->
            removalRanges.add(match.range)
            val block = if (match.groups.size > 1) match.groupValues[1] else ""
            block.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { 
                val parts = it.split(Regex("""\s+as\s+"""))
                val local = parts[0].trim()
                val exported = if (parts.size > 1) parts[1].trim() else local
                exportMap[exported] = local
            }
        }

        // Default export: export default X
        val defaultExportRegex = Regex("""export\s+default\s+([a-zA-Z0-9_$.]+)\s*;?""")
        val defaultMatch = defaultExportRegex.find(content)
        if (defaultMatch != null) {
            removalRanges.add(defaultMatch.range)
            if (defaultMatch.groups.size > 1) {
                exportMap["default"] = defaultMatch.groupValues[1].trim()
            }
        }

        // --- Reconstruction ---
        // Remove marked ranges in descending order to keep indices valid
        val sbContent = StringBuilder(content)
        removalRanges.sortByDescending { it.first }
        for (range in removalRanges) {
            if (range.first >= 0 && range.last < sbContent.length) {
                // Replace with space to assume we removed a statement but kept token boundaries
                sbContent.replace(range.first, range.last + 1, " ") 
            }
        }

        val returnObj = exportMap.entries.joinToString(", ") { 
            if (it.key == it.value) it.key else "${it.key}: ${it.value}"
        }
        
        // Wrap in IIFE
        sb.append("const $varName = (() => {\n")
        sb.append(headerImports)
        sb.append("\n")
        sb.append(sbContent)
        sb.append("\nreturn { $returnObj };\n")
        sb.append("})();\n")
    }

    private fun fetchString(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            return response.body?.string() ?: ""
        }
    }

    private fun getName(path: String) = path.split('/').last()
    
    private fun findMatch(content: String, regex: String): String? {
        val match = Regex(regex).find(content)
        // Correct null check on groups to avoid "No group 1" error
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