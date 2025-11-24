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

            logger("Fetching $entryFileName and dependencies...")
            fetchModuleRecursive(logger, entryFileName, entryModuleName, scriptBuilder, dictionary, language)

            // 4. Find and Expose the PDF Generator Function
            var generatorVarName: String? = null
            
            for ((fileName, content) in fetchedModules) {
                // Heuristic: Check for pdfmake structure markers
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
                logger("Warning: Generator function not definitively found. Trying default export of entry module.")
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

        // --- Step 2: Identify Imports and Recurse ---
        // Match imports ending in .js, regardless of path prefix. (FIX for Fetch Error / "as" error)
        val importRegex = Regex("""import\s*(?:(\w+)\s+from\s*|(?:\s*\{([^}]+)\}\s*from\s*))?["']([^"']+\.js)["']""")
        val imports = importRegex.findAll(content).toList()

        for (match in imports) {
            val depPath = match.groupValues[3]
            val depFileName = getName(depPath)
            val nextVarName = depFileName.replace(Regex("[^a-zA-Z0-9]"), "_") + "_" + depFileName.hashCode().toString().takeLast(4)
            fetchModuleRecursive(logger, depFileName, nextVarName, sb, dictionary, language)
        }

        // --- Step 3: Parse Exports and Construct Return Statement (Fix for Syntax Errors) ---
        val exportMap = mutableMapOf<String, String>() 
        val contentBuilder = StringBuilder(content)
        
        // 1. Capture and remove named exports: export { A, B as C };
        val namedExportsRegex = Regex("""export\s*\{[\s\S]*?\}\s*;?\s*""")
        namedExportsRegex.findAll(contentBuilder.toString()).toList().forEach { match ->
            match.groupValues[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { exportItem ->
                val parts = exportItem.split(Regex("""\s+as\s+"""))
                val localName = parts[0].trim()
                val exportedName = if (parts.size == 2) parts[1].trim() else localName
                exportMap[exportedName] = localName
            }
            // Remove the statement from the content
            contentBuilder.replace(0, contentBuilder.length, contentBuilder.toString().replace(match.value, ""))
        }
        
        // 2. Capture and remove default export: export default X;
        val defaultExportRegex = Regex("""export\s+default\s+([a-zA-Z0-9_$]+)(?:;)?\s*""")
        val defaultExportMatch = defaultExportRegex.find(contentBuilder.toString())
        if (defaultExportMatch != null) {
            exportMap["default"] = defaultExportMatch.groupValues[1].trim()
            // Remove the statement from the content
            contentBuilder.replace(0, contentBuilder.length, contentBuilder.toString().replace(defaultExportMatch.value, ""))
        }
        
        // 3. Create the Return Statement
        val exportsList = exportMap.entries.joinToString(", ") { 
            if (it.key == it.value) it.key else "${it.key}: ${it.value}"
        }
        val returnStatement = "return { $exportsList };"
        
        // --- Step 4: Clean Imports & Assemble IIFE ---

        // 1. Remove remaining import declarations from the content body
        imports.forEach { match ->
            // Use original match value to remove it
            contentBuilder.replace(0, contentBuilder.length, contentBuilder.toString().replace(match.value, ""))
        }
        
        // 2. Remove HTML and license comments (Source of '--' error)
        var finalContent = contentBuilder.toString()
            .replace(Regex(""""""), "")
            .replace(Regex("""/\*![\s\S]*?\*/"""), "")

        // 3. Create header with local imports
        val header = StringBuilder()
        imports.forEach { match ->
            val defaultImport = match.groupValues[1]
            val namedImports = match.groupValues[2]
            val depFileName = getName(match.groupValues[3])
            val depVarNameInMap = moduleVarNames[depFileName]

            if (depVarNameInMap != null) {
                 if (defaultImport.isNotEmpty()) {
                     header.append("const $defaultImport = $depVarNameInMap.default || $depVarNameInMap;\n")
                 }
                 if (namedImports.isNotEmpty()) {
                     header.append("const { $namedImports } = $depVarNameInMap;\n")
                 }
            }
        }

        // 4. Assemble the module IIFE
        sb.append("const $depVarName = (() => {\n$header\n$finalContent\n$returnStatement\n})();\n")
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
                        const Comp = window.RefComponent;
                        
                        if (!Comp || !Comp.setup) {
                            throw "Component setup not found. Check if RefModule loaded correctly.";
                        }
                        
                        const { reactive, unref } = Vue;
                        const props = reactive(propsData);
                        const context = { attrs: {}, slots: {}, emit: () => {} };
                        
                        const state = Comp.setup(props, context);
                        
                        setTimeout(() => {
                             try {
                                 
                                 let dataObj = null;
                                 
                                 for (const key in state) {
                                     const val = unref(state[key]);
                                     if (val && typeof val === 'object' && val.edunum && val.id) {
                                         dataObj = val;
                                         break;
                                     }
                                 }
                                 
                                 if (!dataObj) {
                                     dataObj = {
                                         id: unref(state.docId || state.id || state.doc_id),
                                         edunum: unref(state.course || state.edunum || state.courseStr),
                                         date: unref(state.date || state.currentDate),
                                         adress: unref(state.address || state.adress || state.univAddress)
                                     };
                                 }
                                 
                                 if (!dataObj || !dataObj.id) {
                                     if (unref(state.id) && unref(state.edunum)) dataObj = state;
                                     else throw "Could not calculate document data. State keys: " + Object.keys(unref(state)).join(",");
                                 }
                                 
                                 const finalData = {};
                                 for(const key in dataObj) { finalData[key] = unref(dataObj[key]); }
                                 
                                 AndroidBridge.log("JS: Calculated: " + JSON.stringify(finalData));
                                 
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