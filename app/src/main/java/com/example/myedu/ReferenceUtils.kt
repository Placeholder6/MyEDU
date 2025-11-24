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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.json.JSONObject

class ReferenceJsFetcher {

    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"
    
    // Map: FileName -> UniqueVariableName (e.g. "helpers.js" -> "Mod_helpers_HASH")
    private val moduleVarNames = mutableMapOf<String, String>()
    // Set of processed files to prevent infinite recursion
    private val processedFiles = mutableSetOf<String>()

    suspend fun fetchResources(logger: (String) -> Unit, language: String, dictionary: Map<String, String>): PdfResources = withContext(Dispatchers.IO) {
        moduleVarNames.clear()
        processedFiles.clear()
        
        val scriptBuilder = StringBuilder()
        
        try {
            // 1. Find Entry Point
            logger("Fetching index...")
            val indexHtml = fetchString("$baseUrl/")
            val mainJsPath = findMatch(indexHtml, """src=["'](/assets/index\.[^"']+\.js)["']""") 
                ?: throw Exception("Main JS not found in index.html")
            val mainJsName = getName(mainJsPath)
            
            // 2. Find Reference Module
            logger("Scanning $mainJsName...")
            val mainContent = fetchString("$baseUrl/assets/$mainJsName")
            
            // Look for the Reference chunk (dynamically, not hardcoded hash)
            var refJsName = findMatch(mainContent, """["']([^"']*References\d*\.[^"']+\.js)["']""")
            
            // Fallback: Check StudentDocuments if not found directly
            if (refJsName == null) {
                 val docsJsName = findMatch(mainContent, """["']([^"']*StudentDocuments\.[^"']+\.js)["']""")
                 if (docsJsName != null) {
                     val docsContent = fetchString("$baseUrl/assets/${getName(docsJsName)}")
                     refJsName = findMatch(docsContent, """["']([^"']*References\d*\.[^"']+\.js)["']""")
                 }
            }

            if (refJsName == null) throw Exception("References module not found in dependency tree")
            refJsName = getName(refJsName)
            logger("Found Reference Module: $refJsName")

            // 3. Setup Environment / Polyfills
            scriptBuilder.append("""
                const window_location = { origin: '$baseUrl' };
                const Vue = window.Vue;
                // Destructure common Vue functions globally for compatibility
                const { ref, computed, reactive, unref, toRef, watch, onMounted, getCurrentInstance, defineComponent, resolveComponent, openBlock, createElementBlock, createBaseVNode, toDisplayString, createVNode, withCtx, createTextVNode, pushScopeId, popScopeId } = Vue;
                
                const __modules__ = {}; // Registry for debugging
            """.trimIndent())
            scriptBuilder.append("\n")

            // 4. Recursively Fetch and Bundle
            val entryVarName = fetchRecursive(logger, refJsName, scriptBuilder, language, dictionary)
            
            // 5. Expose the Entry Module
            scriptBuilder.append("\nwindow.RefEntry = $entryVarName;\n")

            return@withContext PdfResources(scriptBuilder.toString())
            
        } catch (e: Exception) {
            logger("Fetch Error: ${e.message}")
            e.printStackTrace()
            return@withContext PdfResources("console.error('Fetch failed: ${e.message}');")
        }
    }

    private suspend fun fetchRecursive(
        logger: (String) -> Unit, 
        fileName: String, 
        sb: StringBuilder,
        language: String,
        dictionary: Map<String, String>
    ): String {
        val cleanName = fileName.split('?')[0]
        
        // Return existing var if already processed
        if (moduleVarNames.containsKey(cleanName)) {
            return moduleVarNames[cleanName]!!
        }
        
        val varName = "Mod_" + cleanName.replace(Regex("[^a-zA-Z0-9]"), "_")
        moduleVarNames[cleanName] = varName
        processedFiles.add(cleanName)

        var content = try {
            fetchString("$baseUrl/assets/$cleanName")
        } catch (e: Exception) {
            logger("Failed to fetch $cleanName")
            return "{}"
        }
        
        // Apply Dictionary Translation
        if (language == "en") {
            dictionary.forEach { (k, v) -> 
                if (k.length > 2) content = content.replace(k, v) 
            }
        }

        // --- Dependency Resolution ---
        // Regex to find imports: import ... from "./file.js"
        // Captures: 1=clause, 2=path
        val importRegex = Regex("""import\s*(?:(\{[^}]*\}|[\w${'$'}*]+(?:\s+as\s+[\w${'$'}]+)?)(?:\s*from)?\s*)?["'](\.?\/?)([^"']+\.js)["'];?""")
        
        val depsToProcess = mutableListOf<String>()
        
        // Pass 1: Identify all dependencies first
        importRegex.findAll(content).forEach { match ->
            val depFile = match.groupValues[3]
            if (!processedFiles.contains(depFile)) {
                depsToProcess.add(depFile)
            }
        }
        
        // Recursively fetch dependencies (Topological sort: dependencies defined before use)
        for (dep in depsToProcess) {
            fetchRecursive(logger, dep, sb, language, dictionary)
        }

        // --- Code Rewriting ---
        var newContent = content

        // 1. Handle Re-exports: export { a } from './b.js'
        // We convert this to: const { a } = Mod_B; (and let the export logic below handle the rest)
        newContent = newContent.replace(Regex("""export\s*\{\s*([^}]+)\s*\}\s*from\s*["']([^"']+)["'];?""")) { m ->
            val clauses = m.groupValues[1]
            val file = getName(m.groupValues[2])
            val modVar = moduleVarNames[file] ?: "{}"
            // We extract to local scope, the export logic at bottom will capture them
            "const { $clauses } = $modVar;"
        }

        // 2. Replace Imports with Variable assignments
        newContent = importRegex.replace(newContent) { match ->
            val clause = match.groupValues[1]
            val depFile = getName(match.groupValues[3])
            val depVar = moduleVarNames[depFile] ?: "{}"
            
            if (clause.isBlank()) {
                "// Side effect: $depFile"
            } else if (clause.contains("* as")) {
                val alias = clause.split("as")[1].trim()
                "const $alias = $depVar;"
            } else if (clause.trim().startsWith("{")) {
                // Named imports: const { a, b: c } = Mod;
                "const $clause = $depVar;"
            } else {
                // Default import: const A = Mod.default || Mod;
                "const $clause = $depVar.default || $depVar;"
            }
        }

        // 3. Capture Export Declarations
        // We look for `export const x`, `export function y` etc.
        val exportedNames = mutableListOf<String>()
        val exportDeclRegex = Regex("""export\s+(?:const|let|var|function|class|async\s+function)\s+([\w${'$'}]+)""")
        exportDeclRegex.findAll(content).forEach { exportedNames.add(it.groupValues[1]) }
        
        // Remove 'export' keyword from declarations so they become valid local JS
        newContent = newContent.replace(Regex("""export\s+(?=(const|let|var|function|class|async\s+function))"""), "")

        // 4. Handle `export default`
        newContent = newContent.replace(Regex("""export\s+default\s+"""), "__exports__.default = ")
        
        // 5. Handle `export { a, b as c }`
        newContent = newContent.replace(Regex("""export\s*\{\s*([^}]+)\s*\};?""")) { m ->
            val body = m.groupValues[1]
            val parts = body.split(",")
            val assigns = parts.joinToString(";") { part ->
                val kv = part.trim().split(Regex("""\s+as\s+"""))
                if (kv.size == 1) "__exports__.${kv[0]} = ${kv[0]}"
                else "__exports__.${kv[1]} = ${kv[0]}"
            }
            assigns + ";"
        }

        // 6. Cleanup
        newContent = newContent.replace(Regex("""import\s+["'][^"']+\.css["'];?"""), "") // Remove CSS imports
        newContent = newContent.replace("import.meta.url", "'$baseUrl'") // Mock import.meta

        // 7. Wrap in IIFE
        sb.append("\n// --- Module: $cleanName ---\n")
        sb.append("const $varName = (() => {\n")
        sb.append("  const __exports__ = {};\n")
        sb.append(newContent)
        sb.append("\n")
        // Manually assign discovered declarations to exports
        exportedNames.forEach { name ->
            sb.append("  try { __exports__.$name = $name; } catch(e){}\n")
        }
        sb.append("  return __exports__;\n")
        sb.append("})();\n")
        sb.append("__modules__['$cleanName'] = $varName;\n")

        return varName
    }
    
    private fun fetchString(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code} for $url")
            return response.body?.string() ?: ""
        }
    }
    
    private fun findMatch(content: String, regex: String): String? {
        val match = Regex(regex).find(content)
        return if (match != null && match.groups.size > 1) match.groupValues[1] else null
    }
    
    private fun getName(path: String): String {
        return path.substringAfterLast("/").substringBefore("?")
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
    ): ByteArray = suspendCancellableCoroutine { continuation ->
        
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            
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
                // Inject bundled script
                try {
                    ${resources.combinedScript}
                } catch (e) {
                    AndroidBridge.returnError("Script Injection Error: " + e.message);
                    console.error(e);
                }
            
                async function runGeneration() {
                    try {
                        AndroidBridge.log("JS: Starting generation...");
                        
                        // Use the entry module discovered by fetcher
                        const Entry = window.RefEntry;
                        if (!Entry) throw "RefEntry module not found";
                        
                        // The entry usually exports the component as default, OR a setup function
                        let Comp = Entry.default || Entry;
                        
                        // Smart discovery of the Generator function
                        // Sometimes the logic is exported as 'generatePdf' or similar, or inside the component setup
                        // If Comp is the Vue component, we mount it to trigger setup, then check state
                        
                        // Heuristic: Check if Entry exports a generator directly
                        // Look for a function that isn't the component
                        let Generator = null;
                        
                        // 1. Check named exports for a function
                        for (const key in Entry) {
                            if (typeof Entry[key] === 'function' && key !== 'default' && key.length > 2) {
                                // Candidates?
                            }
                        }
                        
                        // If we have a Vue component, we can try to instantiate it to get data
                        if (Comp.setup || Comp.render) {
                             const { reactive, unref } = Vue;
                             const props = reactive(propsData);
                             
                             // Mock context
                             const ctx = { attrs: {}, slots: {}, emit: ()=>{} };
                             
                             // Execute setup
                             let state = {};
                             if (typeof Comp.setup === 'function') {
                                 state = Comp.setup(props, ctx);
                             }
                             
                             // The setup usually returns the data needed for PDF
                             // But the actual PDF definition creation might be a separate function imported in that file.
                             // Since we bundled everything, if the generator was imported, it exists in a Mod_... var.
                             // We can try to find the generator in the global scope if we exposed it, but we didn't.
                             
                             // Fallback: Iterate all bundled modules to find one that looks like a PDF generator
                             // (Contains { pageSize: 'A4' } structure or similar)
                             
                             for (const modName in __modules__) {
                                 const mod = __modules__[modName];
                                 // If module exports a function that returns an object with pageSize
                                 if (mod.default && typeof mod.default === 'function') {
                                     try {
                                         const result = mod.default(propsData.student, propsData.student, propsData.qr);
                                         if (result && result.pageSize) {
                                             Generator = mod.default;
                                             AndroidBridge.log("Found Generator in: " + modName);
                                             break;
                                         }
                                     } catch(e) {}
                                 }
                             }
                        }
                        
                        if (!Generator) {
                            // Last ditch: check Entry default
                             try {
                                 const res = Comp(propsData.student, propsData.student, propsData.qr);
                                 if (res && res.pageSize) Generator = Comp;
                             } catch(e){}
                        }
                        
                        if (!Generator) throw "PDF Generator function could not be located.";

                        AndroidBridge.log("JS: Generating PDF...");
                        
                        // IMPORTANT: The generator usually expects (student, data, qr)
                        // We need to construct 'data' correctly. Usually it matches student info.
                        
                        const docDef = Generator(propsData.student, propsData.student, propsData.qr);
                        
                        const pdfDocGenerator = pdfMake.createPdf(docDef);
                        pdfDocGenerator.getBase64(function(b64) {
                             AndroidBridge.returnPdf(b64);
                        });
                        
                    } catch(e) { 
                        AndroidBridge.returnError("Setup Error: " + e.toString()); 
                    }
                }
                
                runGeneration();
            </script>
            </body>
            </html>
            """
            
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Logic runs in module script
                }
            }
            webView.loadDataWithBaseURL("https://myedu.oshsu.kg/", html, "text/html", "UTF-8", null)
        }
    }
}