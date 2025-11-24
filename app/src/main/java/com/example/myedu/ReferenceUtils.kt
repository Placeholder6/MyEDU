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
            
            val mainJsName = getName(mainJsPath)
            logger("Scanning $mainJsName...")
            val mainContent = fetchString("$baseUrl/assets/$mainJsName")
            
            // Find Reference Module (supporting standard or split chunks)
            var refJsPath = findMatch(mainContent, """["']([^"']*References\d*\.[^"']+\.js)["']""")
            
            if (refJsPath == null) {
                 val docsJsPath = findMatch(mainContent, """["']([^"']*StudentDocuments\.[^"']+\.js)["']""")
                 if (docsJsPath != null) {
                     val docsContent = fetchString("$baseUrl/assets/${getName(docsJsPath)}")
                     refJsPath = findMatch(docsContent, """["']([^"']*References\d*\.[^"']+\.js)["']""")
                 }
            }

            if (refJsPath == null) throw Exception("References module not found.")
            
            val entryFileName = getName(refJsPath)
            logger("Found Entry: $entryFileName")

            // Setup Global Environment
            scriptBuilder.append("""
                window.window_location = { origin: '$baseUrl' };
                window.__bundled_modules__ = {};
                
                // Polyfill Vue
                const Vue = window.Vue;
                const { ref, computed, reactive, unref, toRef, watch, onMounted, getCurrentInstance, defineComponent, resolveComponent, openBlock, createElementBlock, createBaseVNode, toDisplayString, createVNode, withCtx, createTextVNode, pushScopeId, popScopeId } = Vue;
                
                function defineBundledModule(name, factory) {
                    try {
                        // console.log("Init: " + name);
                        window.__bundled_modules__[name] = factory();
                    } catch (e) {
                        console.error("FAILED module: " + name, e);
                        if(window.AndroidBridge) window.AndroidBridge.returnError("Module " + name + ": " + e.message);
                    }
                }
            """.trimIndent())
            scriptBuilder.append("\n")

            // Start Recursion
            val entryVarName = fetchRecursive(logger, entryFileName, scriptBuilder, language, dictionary)

            // Expose Entry
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
        
        val varName = "Mod_" + cleanName.replace(Regex("[^a-zA-Z0-9]"), "_")
        moduleVarNames[cleanName] = varName
        processedFiles.add(cleanName)

        var content = try {
            fetchString("$baseUrl/assets/$cleanName")
        } catch (e: Exception) {
            logger("Network Error $cleanName")
            return "{}"
        }
        
        if (language == "en") {
            dictionary.forEach { (k, v) -> 
                if (k.length > 3) content = content.replace(k, v) 
            }
        }

        // --- Scan Imports ---
        // Regex handles: import A from "b"; import { A } from "b"; import "b";
        val importRegex = Regex("""import\s*(?:(?:\{[^}]*\}|[\w${'$'}*]+(?:\s+as\s+[\w${'$'}]+)?)(?:\s*from)?)?\s*["'](\.?\/?)([^"']+\.js)["'];?""")
        
        val deps = mutableListOf<String>()
        importRegex.findAll(content).forEach { match ->
            deps.add(getName(match.groupValues[2]))
        }
        
        for (dep in deps) {
            fetchRecursive(logger, dep, sb, language, dictionary)
        }

        // --- Rewriting ---
        var newContent = content

        // 1. Re-exports: export { a } from './b.js' 
        // Matches: export { a, b as c } from "./foo.js"
        newContent = newContent.replace(Regex("""export\s*\{\s*([^}]+)\s*\}\s*from\s*["']([^"']+)["'];?""")) { m ->
            val body = m.groupValues[1]
            val depName = getName(m.groupValues[2])
            val depVar = moduleVarNames[depName] ?: "{}"
            
            // Convert "a, b as c" -> "const { a, c: b } = Mod;" (Note: b as c means c is the exported name, b is internal)
            // Actually, simpler: Just extract them to locals, the later export logic will grab them.
            // "const { a, b: c } = Mod;" 
            // Wait, `export { b as c } from '...'` means we import `b` from '...' and export it as `c`.
            // So we need: `const { b: c } = Mod_...` -> then `export { c }` logic handles the rest? 
            // No, simpler to map directly to exports if possible, but we are in a function.
            
            // Let's just destruct them into local variables with the *exported* name to satisfy potential local usage or subsequent export.
            val destructuring = body.split(",").joinToString(", ") { part ->
                val kv = part.trim().split(Regex("""\s+as\s+"""))
                if (kv.size == 1) kv[0] // { a }
                else "${kv[0]}: ${kv[1]}" // { b as c } -> { b: c } in destructuring
            }
            "\nconst { $destructuring } = $depVar;\n"
        }
        
        // 2. Handle `export * from '...'`
        newContent = newContent.replace(Regex("""export\s*\*\s*from\s*["']([^"']+)["'];?""")) { m ->
            val depName = getName(m.groupValues[1])
            val depVar = moduleVarNames[depName] ?: "{}"
            "\nObject.assign(__exports__, $depVar);\n"
        }

        // 3. Regular Imports
        newContent = importRegex.replace(newContent) { match ->
            val fullStatement = match.value
            val fromIndex = fullStatement.indexOf("from")
            val depFile = getName(match.groupValues[2])
            val depVar = moduleVarNames[depFile] ?: "{}"

            if (fromIndex == -1) {
                // Side effect import
                "\n/* Side effect: $depFile */\n"
            } else {
                // Parse the clause: " { a, b as c } " or " * as ns " or " Def "
                val clause = fullStatement.substring(6, fromIndex).trim()
                
                if (clause.contains("* as")) {
                    // Namespace: import * as ns from ...
                    val alias = clause.split("as")[1].trim()
                    "\nconst $alias = $depVar;\n"
                } else if (clause.startsWith("{")) {
                    // Named: import { a, b as c } from ...
                    // FIX: Convert "b as c" to "b: c" for object destructuring
                    val inner = clause.trim().removeSurrounding("{", "}").trim()
                    if (inner.isEmpty()) {
                        "" 
                    } else {
                        val destructured = inner.split(",").joinToString(", ") { part ->
                            // "b as c" -> "b: c"
                            val kv = part.trim().split(Regex("""\s+as\s+"""))
                            if (kv.size > 1) "${kv[0]}: ${kv[1]}" else kv[0]
                        }
                        "\nconst { $destructured } = $depVar;\n"
                    }
                } else {
                    // Default: import A from ...
                    "\nconst $clause = $depVar.default || $depVar;\n"
                }
            }
        }

        // 4. Export Declarations: export const x = ...
        val exportedNames = mutableListOf<String>()
        val exportDeclRegex = Regex("""export\s+(?:async\s+)?(?:const|let|var|function|class)\s+([\w${'$'}]+)""")
        exportDeclRegex.findAll(content).forEach { exportedNames.add(it.groupValues[1]) }
        
        newContent = newContent.replace(Regex("""export\s+(?=(async\s+)?(?:const|let|var|function|class))"""), "")

        // 5. Export Default
        newContent = newContent.replace(Regex("""export\s+default\s+"""), "\n__exports__.default = ")

        // 6. Export List: export { a, b as c }
        newContent = newContent.replace(Regex("""export\s*\{\s*([^}]+)\s*\};?""")) { m ->
            val body = m.groupValues[1]
            val assigns = body.split(",").joinToString(";") { part ->
                val kv = part.trim().split(Regex("""\s+as\s+"""))
                if (kv.size == 1) "__exports__.${kv[0]} = ${kv[0]}"
                else "__exports__.${kv[1]} = ${kv[0]}"
            }
            "\n$assigns;\n"
        }

        // Cleanup
        newContent = newContent.replace("import.meta.url", "'$baseUrl'")
        newContent = newContent.replace(Regex("""import\s+["'][^"']+\.css["'];?"""), "")

        // Bundle
        sb.append("\n// --- Module: $cleanName ---\n")
        sb.append("defineBundledModule('$cleanName', () => {\n")
        sb.append("  const __exports__ = {};\n")
        sb.append(newContent)
        sb.append("\n")
        exportedNames.forEach { name ->
            sb.append("  try { __exports__.$name = $name; } catch(e){}\n")
        }
        sb.append("  return __exports__;\n")
        sb.append("});\n")
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
    
    private fun getName(path: String) = path.split('/').last().split('?').first()
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
                    if (continuation.isActive) continuation.resumeWithException(Exception("JS: $msg"))
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
                    AndroidBridge.returnError("Script Inject: " + e.message);
                }
            
                async function runGeneration() {
                    // Check for bundle errors
                    // We can inspect the global errors array if we defined one, 
                    // or just rely on the module returning empty.
                    
                    try {
                        let Generator = null;
                        const Entry = window.RefEntry;
                        
                        const isGen = (fn) => {
                            try {
                                if (typeof fn !== 'function') return false;
                                const res = fn(propsData.student, propsData.student, propsData.qr);
                                return res && (res.pageSize || res.content);
                            } catch(e) { return false; }
                        };

                        // 1. Check Entry Default
                        if (Entry) {
                            if (isGen(Entry.default)) Generator = Entry.default;
                            else if (isGen(Entry)) Generator = Entry;
                        }
                        
                        // 2. Fallback: Scan all modules
                        if (!Generator && window.__bundled_modules__) {
                            for (const key in window.__bundled_modules__) {
                                const mod = window.__bundled_modules__[key];
                                if (mod && isGen(mod.default)) { Generator = mod.default; break; }
                            }
                        }

                        // 3. Vue Setup Fallback
                        if (!Generator && Entry && (Entry.default || Entry).setup) {
                             const Comp = Entry.default || Entry;
                             const { reactive } = Vue;
                             try {
                                 Comp.setup(reactive(propsData), { attrs:{}, slots:{}, emit:()=>{} });
                             } catch(e) {}
                        }

                        if (!Generator) {
                             throw "PDF Generator function not found.";
                        }

                        const docDef = Generator(propsData.student, propsData.student, propsData.qr);
                        pdfMake.createPdf(docDef).getBase64(function(b64) {
                             AndroidBridge.returnPdf(b64);
                        });
                        
                    } catch(e) { 
                        AndroidBridge.returnError("Logic: " + e.toString()); 
                    }
                }
                
                setTimeout(runGeneration, 200);
            </script>
            </body>
            </html>
            """
            
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Logic via setTimeout
                }
            }
            webView.loadDataWithBaseURL("https://myedu.oshsu.kg/", html, "text/html", "UTF-8", null)
        }
    }
}