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
    
    // Cache: FileName -> BundleVariableName
    private val moduleVarNames = mutableMapOf<String, String>()
    private val processedFiles = mutableSetOf<String>()

    suspend fun fetchResources(logger: (String) -> Unit, language: String, dictionary: Map<String, String>): PdfResources = withContext(Dispatchers.IO) {
        moduleVarNames.clear()
        processedFiles.clear()
        
        val scriptBuilder = StringBuilder()
        
        try {
            logger("Finding entry point...")
            val indexHtml = fetchString("$baseUrl/")
            val mainJsPath = findMatch(indexHtml, """src=["'](/assets/index\.[^"']+\.js)["']""") 
                ?: throw Exception("Main JS not found in index.html")
            
            // 2. Scan Main JS to find Reference Module
            val mainJsName = getName(mainJsPath)
            logger("Scanning $mainJsName...")
            val mainContent = fetchString("$baseUrl/assets/$mainJsName")
            
            // Try to find Reference module
            var refJsPath = findMatch(mainContent, """["']([^"']*References\d*\.[^"']+\.js)["']""")
            
            // Fallback: Check StudentDocuments
            if (refJsPath == null) {
                 val docsJsPath = findMatch(mainContent, """["']([^"']*StudentDocuments\.[^"']+\.js)["']""")
                 if (docsJsPath != null) {
                     val docsContent = fetchString("$baseUrl/assets/${getName(docsJsPath)}")
                     refJsPath = findMatch(docsContent, """["']([^"']*References\d*\.[^"']+\.js)["']""")
                 }
            }

            if (refJsPath == null) throw Exception("References module not found. Regex failed on dependency tree.")
            
            val entryFileName = getName(refJsPath)
            logger("Found Entry: $entryFileName")

            // 3. Setup Environment
            scriptBuilder.append("""
                window.window_location = { origin: '$baseUrl' };
                window.__bundled_modules__ = {};
                window.__module_errors__ = [];
                
                // Polyfill Vue
                const Vue = window.Vue;
                const { ref, computed, reactive, unref, toRef, watch, onMounted, getCurrentInstance, defineComponent, resolveComponent, openBlock, createElementBlock, createBaseVNode, toDisplayString, createVNode, withCtx, createTextVNode, pushScopeId, popScopeId } = Vue;
                
                // Module definition helper with error tracking
                function defineBundledModule(name, factory) {
                    try {
                        console.log("Init module: " + name);
                        window.__bundled_modules__[name] = factory();
                    } catch (e) {
                        console.error("FAILED module: " + name, e);
                        window.__module_errors__.push({name: name, error: e.toString()});
                        // AndroidBridge.returnError("Module " + name + ": " + e.message);
                    }
                }
            """.trimIndent())
            scriptBuilder.append("\n")

            // 4. Fetch and Bundle
            val entryVarName = fetchRecursive(logger, entryFileName, scriptBuilder, language, dictionary)

            // 5. Expose Entry
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
        
        if (moduleVarNames.containsKey(cleanName)) {
            return moduleVarNames[cleanName]!!
        }
        
        // Unique variable name for the module
        val varName = "Mod_" + cleanName.replace(Regex("[^a-zA-Z0-9]"), "_")
        moduleVarNames[cleanName] = varName
        processedFiles.add(cleanName)

        var content = try {
            fetchString("$baseUrl/assets/$cleanName")
        } catch (e: Exception) {
            logger("Network Error $cleanName: ${e.message}")
            return "{}"
        }
        
        if (language == "en") {
            dictionary.forEach { (k, v) -> 
                if (k.length > 3) content = content.replace(k, v) 
            }
        }

        // --- Recursive Dependency Fetching ---
        // Matches: import ... from "./file.js"
        val importRegex = Regex("""import\s*(?:(?:\{[^}]*\}|[\w${'$'}*]+(?:\s+as\s+[\w${'$'}]+)?)(?:\s*from)?)?\s*["'](\.?\/?)([^"']+\.js)["'];?""")
        
        val deps = mutableListOf<String>()
        importRegex.findAll(content).forEach { match ->
            deps.add(getName(match.groupValues[2]))
        }
        
        for (dep in deps) {
            // Recursively fetch before writing current module (Topological-ish sort)
            fetchRecursive(logger, dep, sb, language, dictionary)
        }

        // --- Rewriting Code for No-Module Environment ---
        var newContent = content

        // 1. Re-exports: export { a } from './b.js' -> const { a } = Mod_B;
        newContent = newContent.replace(Regex("""export\s*\{\s*([^}]+)\s*\}\s*from\s*["']([^"']+)["'];?""")) { m ->
            val clauses = m.groupValues[1]
            val depName = getName(m.groupValues[2])
            val depVar = moduleVarNames[depName] ?: "{}"
            "\nconst { $clauses } = $depVar;\n"
        }

        // 2. Imports: import ... from './b.js' -> const ... = Mod_B;
        newContent = importRegex.replace(newContent) { match ->
            val fullStatement = match.value
            // Extract the clause (between 'import' and 'from')
            // We re-parse specifically to handle the 'from' part reliably
            val fromIndex = fullStatement.indexOf("from")
            val depFile = getName(match.groupValues[2])
            val depVar = moduleVarNames[depFile] ?: "{}"

            if (fromIndex == -1) {
                // Side effect: import "./foo.js"
                "\n/* Side effect: $depFile */\n"
            } else {
                val clause = fullStatement.substring(6, fromIndex).trim()
                if (clause.contains("* as")) {
                    val alias = clause.split("as")[1].trim()
                    "\nconst $alias = $depVar;\n"
                } else if (clause.startsWith("{")) {
                    // Named: import { a, b } from ...
                    "\nconst $clause = $depVar;\n"
                } else {
                    // Default: import A from ...
                    // Fallback to default or self
                    "\nconst $clause = $depVar.default || $depVar;\n"
                }
            }
        }

        // 3. Export Declarations: export const x = ... -> const x = ...
        val exportedNames = mutableListOf<String>()
        // Regex to catch: export const/let/var/function/class/async function NAME
        val exportDeclRegex = Regex("""export\s+(?:async\s+)?(?:const|let|var|function|class)\s+([\w${'$'}]+)""")
        exportDeclRegex.findAll(content).forEach { exportedNames.add(it.groupValues[1]) }
        
        newContent = newContent.replace(Regex("""export\s+(?=(async\s+)?(?:const|let|var|function|class))"""), "")

        // 4. Export Default: export default ... -> __exports__.default = ...
        newContent = newContent.replace(Regex("""export\s+default\s+"""), "\n__exports__.default = ")

        // 5. Named Exports: export { a, b as c }
        newContent = newContent.replace(Regex("""export\s*\{\s*([^}]+)\s*\};?""")) { m ->
            val body = m.groupValues[1]
            val assigns = body.split(",").joinToString(";") { part ->
                val kv = part.trim().split(Regex("""\s+as\s+"""))
                if (kv.size == 1) "__exports__.${kv[0]} = ${kv[0]}"
                else "__exports__.${kv[1]} = ${kv[0]}"
            }
            "\n$assigns;\n"
        }

        // 6. Misc Cleanup
        newContent = newContent.replace("import.meta.url", "'$baseUrl'")
        // Remove CSS imports entirely
        newContent = newContent.replace(Regex("""import\s+["'][^"']+\.css["'];?"""), "")

        // Wrap in IIFE and define
        sb.append("\n// --- Module: $cleanName ---\n")
        // We use 'defineBundledModule' to wrap execution in try-catch for granular error reporting
        sb.append("defineBundledModule('$cleanName', () => {\n")
        sb.append("  const __exports__ = {};\n")
        // Inject content inside
        sb.append(newContent)
        sb.append("\n")
        // Manual exports mapping
        exportedNames.forEach { name ->
            sb.append("  try { __exports__.$name = $name; } catch(e){}\n")
        }
        sb.append("  return __exports__;\n")
        sb.append("});\n")
        
        // Assign the result to the varName so other modules can use it
        sb.append("const $varName = window.__bundled_modules__['$cleanName'] || {};\n")

        return varName
    }
    
    private fun fetchString(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            return response.body?.string() ?: ""
        }
    }
    
    private fun findMatch(content: String, regex: String): String? {
        val match = Regex(regex).find(content)
        return if (match != null && match.groups.size > 1) match.groupValues[1] else null
    }
    
    private fun getName(path: String) = path.substringAfterLast("/").substringBefore("?")
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
                    // IMPORTANT: Log everything to help debug specific file issues
                    logCallback("[JS] ${cm.message()} (Line ${cm.lineNumber()})")
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
                    console.error("Global Error: " + msg + " @ " + line);
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
                // Inject Bundled Code
                try {
                    ${resources.combinedScript}
                } catch (e) {
                    console.error("Bundle Injection Error", e);
                    AndroidBridge.returnError("Bundle Syntax Error: " + e.message);
                }
            
                async function runGeneration() {
                    // Check for module initialization errors first
                    if (window.__module_errors__ && window.__module_errors__.length > 0) {
                        const firstErr = window.__module_errors__[0];
                        AndroidBridge.returnError("Module Failed: " + firstErr.name + " - " + firstErr.error);
                        return;
                    }

                    try {
                        console.log("Starting PDF Generation...");
                        
                        // Find the Generator function dynamically
                        let Generator = null;
                        const Entry = window.RefEntry;
                        
                        const isGen = (fn) => {
                            try {
                                if (typeof fn !== 'function') return false;
                                const res = fn(propsData.student, propsData.student, propsData.qr);
                                return res && (res.pageSize || res.content);
                            } catch(e) { return false; }
                        };

                        if (Entry) {
                            if (isGen(Entry)) Generator = Entry;
                            else if (isGen(Entry.default)) Generator = Entry.default;
                        }
                        
                        // Fallback: Scan all bundled modules
                        if (!Generator && window.__bundled_modules__) {
                            for (const key in window.__bundled_modules__) {
                                const mod = window.__bundled_modules__[key];
                                if (isGen(mod)) { Generator = mod; break; }
                                if (mod.default && isGen(mod.default)) { Generator = mod.default; break; }
                            }
                        }

                        if (!Generator && Entry && (Entry.default || Entry).setup) {
                             // Try to extract from Vue component setup
                             const Comp = Entry.default || Entry;
                             const { reactive } = Vue;
                             // This might trigger side effects that register the generator globally or similar
                             Comp.setup(reactive(propsData), { attrs:{}, slots:{}, emit:()=>{} });
                             // Re-scan logic... (simplified here)
                        }

                        if (!Generator) {
                             throw "PDF Generator function not found in any loaded module.";
                        }

                        const docDef = Generator(propsData.student, propsData.student, propsData.qr);
                        pdfMake.createPdf(docDef).getBase64(function(b64) {
                             AndroidBridge.returnPdf(b64);
                        });
                        
                    } catch(e) { 
                        AndroidBridge.returnError("Runtime Logic: " + e.toString()); 
                    }
                }
                
                // Delay slightly to ensure scripts parsed
                setTimeout(runGeneration, 100);
            </script>
            </body>
            </html>
            """
            
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Logic runs via setTimeout in HTML
                }
            }
            webView.loadDataWithBaseURL("https://myedu.oshsu.kg/", html, "text/html", "UTF-8", null)
        }
    }
}