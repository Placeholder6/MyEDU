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
    
    // Map to store fetched content: FileName -> BundleVariableName
    private val moduleVarNames = mutableMapOf<String, String>()
    private val processedFiles = mutableSetOf<String>()

    suspend fun fetchResources(logger: (String) -> Unit, language: String, dictionary: Map<String, String>): PdfResources = withContext(Dispatchers.IO) {
        moduleVarNames.clear()
        processedFiles.clear()
        val scriptBuilder = StringBuilder()
        
        try {
            logger("Finding entry point...")
            // 1. Fetch Index to find Main JS
            val indexHtml = fetchString("$baseUrl/")
            val mainJsPath = findMatch(indexHtml, """src=["'](/assets/index\.[^"']+\.js)["']""") 
                ?: throw Exception("Main JS missing in index.html")
            
            // 2. Scan Main JS to find Reference Module
            logger("Scanning ${getName(mainJsPath)}...")
            val mainContent = fetchString("$baseUrl${mainJsPath}")
            
            // Try to find the References module import (dynamically)
            var refJsPath = findMatch(mainContent, """["']([^"']*References\d*\.[^"']+\.js)["']""")
            
            // Fallback: Check StudentDocuments if not found directly (it might be a chunk loaded by it)
            if (refJsPath == null) {
                 val docsJsPath = findMatch(mainContent, """["']([^"']*StudentDocuments\.[^"']+\.js)["']""")
                 if (docsJsPath != null) {
                     val docsContent = fetchString("$baseUrl/assets/${getName(docsJsPath)}")
                     refJsPath = findMatch(docsContent, """["']([^"']*References\d*\.[^"']+\.js)["']""")
                 }
            }

            if (refJsPath == null) throw Exception("References JS module not found in dependency tree")
            
            val entryFileName = getName(refJsPath)
            logger("Found Reference Module: $entryFileName")

            // 3. Polyfill Environment
            // We mock the window.location and expose Vue globally
            scriptBuilder.append("""
                const window_location = { origin: '$baseUrl' };
                const Vue = window.Vue;
                const { ref, computed, reactive, unref, toRef, watch, onMounted, getCurrentInstance, defineComponent, resolveComponent, openBlock, createElementBlock, createBaseVNode, toDisplayString, createVNode, withCtx, createTextVNode, pushScopeId, popScopeId } = Vue;
                const __modules__ = {}; // Debug registry
            """.trimIndent())
            scriptBuilder.append("\n")

            // 4. Recursively Fetch and Bundle
            // This process converts imports/exports to variable assignments in IIFEs
            val entryVarName = fetchRecursive(logger, entryFileName, scriptBuilder, language, dictionary)

            // 5. Expose the Entry point
            scriptBuilder.append("\nwindow.RefEntry = $entryVarName;\n")

            return@withContext PdfResources(scriptBuilder.toString())
        } catch (e: Exception) {
            logger("Ref Fetch Error: ${e.message}")
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
        
        // Avoid cycles and re-fetching
        if (moduleVarNames.containsKey(cleanName)) {
            return moduleVarNames[cleanName]!!
        }

        // Create a safe variable name for this module
        val varName = "Mod_" + cleanName.replace(Regex("[^a-zA-Z0-9]"), "_")
        moduleVarNames[cleanName] = varName
        processedFiles.add(cleanName)

        var content = try {
            fetchString("$baseUrl/assets/$cleanName")
        } catch (e: Exception) {
            logger("Failed to fetch $cleanName: ${e.message}")
            return "{}"
        }

        // Apply Dictionary Translation (if English)
        if (language == "en" && dictionary.isNotEmpty()) {
             dictionary.forEach { (ru, en) -> 
                 if (ru.length > 3) content = content.replace(ru, en) 
             }
        }

        // --- Regex to find Imports ---
        // Matches: import { x } from "./file.js" OR import X from "./file.js" OR import "./file.js"
        val importRegex = Regex("""import\s*(?:(\{[^}]*\}|[\w${'$'}*]+(?:\s+as\s+[\w${'$'}]+)?)(?:\s*from)?\s*)?["'](\.?\/?)([^"']+\.js)["'];?""")
        
        val dependencies = mutableListOf<String>()
        
        // 1. Scan dependencies first
        importRegex.findAll(content).forEach { match ->
            val depFile = getName(match.groupValues[3])
            if (!processedFiles.contains(depFile)) {
                dependencies.add(depFile)
            }
        }
        
        // 2. Recursively fetch dependencies (Depth-First)
        for (dep in dependencies) {
            fetchRecursive(logger, dep, sb, language, dictionary)
        }

        // --- Code Rewriting ---
        var newContent = content

        // Rewrite: export { a } from './b.js' -> const { a } = Mod_B;
        newContent = newContent.replace(Regex("""export\s*\{\s*([^}]+)\s*\}\s*from\s*["']([^"']+)["'];?""")) { m ->
            val clauses = m.groupValues[1]
            val file = getName(m.groupValues[2])
            val modVar = moduleVarNames[file] ?: "{}"
            "const { $clauses } = $modVar;"
        }

        // Rewrite Imports to Variable Assignments
        newContent = importRegex.replace(newContent) { match ->
            val clause = match.groupValues[1]
            val depFile = getName(match.groupValues[3])
            val depVar = moduleVarNames[depFile] ?: "{}"
            
            if (clause.isBlank()) {
                // Side effect import: import "./style.js"
                // Ensure newline to avoid commenting out next line in minified code
                "\n// Side effect: $depFile\n" 
            } else if (clause.contains("* as")) {
                // Namespace: import * as NS
                val alias = clause.split("as")[1].trim()
                "const $alias = $depVar;"
            } else if (clause.trim().startsWith("{")) {
                // Named: import { a, b }
                "const $clause = $depVar;"
            } else {
                // Default: import A
                // We handle default exports by assigning to .default
                "const $clause = $depVar.default || $depVar;"
            }
        }

        // Capture Exports
        val exportedNames = mutableListOf<String>()
        
        // Find 'export const/function/class x' and strip 'export' keyword
        val exportDeclRegex = Regex("""export\s+(?:const|let|var|function|class|async\s+function)\s+([\w${'$'}]+)""")
        exportDeclRegex.findAll(content).forEach { exportedNames.add(it.groupValues[1]) }
        
        // Remove 'export' keyword to make them local variables
        newContent = newContent.replace(Regex("""export\s+(?=(const|let|var|function|class|async\s+function))"""), "")

        // Handle 'export default X' -> '__exports__.default = X'
        newContent = newContent.replace(Regex("""export\s+default\s+"""), "__exports__.default = ")
        
        // Handle 'export { a, b as c }' -> manual assignment
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

        // Cleanup
        newContent = newContent.replace("import.meta.url", "'$baseUrl'") // Mock meta url
        newContent = newContent.replace(Regex("""import\s+["'][^"']+\.css["'];?"""), "") // Kill CSS

        // Wrap in IIFE (Module Pattern)
        sb.append("\n// --- Module: $cleanName ---\n")
        sb.append("const $varName = (() => {\n")
        sb.append("  const __exports__ = {};\n")
        sb.append(newContent)
        sb.append("\n")
        // Manually map captured exports
        exportedNames.forEach { name ->
            sb.append("  try { __exports__.$name = $name; } catch(e){}\n")
        }
        sb.append("  return __exports__;\n")
        sb.append("})();\n")
        
        return varName
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
                // 1. Inject Bundled Scripts
                try {
                    ${resources.combinedScript}
                } catch (e) {
                    AndroidBridge.returnError("Script Injection Error: " + e.message);
                    console.error(e);
                }
            
                async function runGeneration() {
                    try {
                        AndroidBridge.log("JS: Starting generation...");
                        
                        // 2. Resolve the Generator Function dynamically
                        // We look for a function inside the Entry module or any bundled module 
                        // that returns a PDF Definition (object with pageSize)
                        
                        let Generator = null;
                        const Entry = window.RefEntry;
                        
                        // Helper to test if a function is the generator
                        const isGenerator = (fn) => {
                            try {
                                if (typeof fn !== 'function') return false;
                                // Call with dummy data to see if it returns a doc definition
                                const res = fn(propsData.student, propsData.student, propsData.qr);
                                return res && (res.pageSize || res.content);
                            } catch(e) { return false; }
                        };

                        // Check Entry default export
                        if (Entry && isGenerator(Entry.default)) Generator = Entry.default;
                        else if (Entry && isGenerator(Entry)) Generator = Entry;
                        
                        // If not found, check the Vue Component's setup
                        if (!Generator && Entry && (Entry.default || Entry).setup) {
                             const Comp = Entry.default || Entry;
                             const { reactive } = Vue;
                             // Run setup to trigger any internal logic
                             Comp.setup(reactive(propsData), { attrs:{}, slots:{}, emit:()=>{} });
                             
                             // The actual generator might be a dependency that was bundled.
                             // We scan all bundled modules for one that looks like a PDF generator.
                             // (Heuristic: returns object with pageSize)
                             /* Note: The fetcher puts modules in const variables, but we can't iterate scopes.
                                However, if we attached them to a global registry in step 3, we could. 
                                But typically the Entry module imports the generator. */
                        }
                        
                        // If still null, we might need to check the global window if it leaked, 
                        // or rely on the 'RefEntry' actually being the generator module itself 
                        // if the regex logic targeted the logic file directly.

                        if (!Generator) {
                             throw "Could not locate PDF Generator function. Entry module loaded but signature mismatch.";
                        }

                        AndroidBridge.log("JS: Generating PDF...");
                        const docDef = Generator(propsData.student, propsData.student, propsData.qr);
                        
                        const pdfDocGenerator = pdfMake.createPdf(docDef);
                        pdfDocGenerator.getBase64(function(b64) {
                             AndroidBridge.returnPdf(b64);
                        });
                        
                    } catch(e) { 
                        AndroidBridge.returnError("Logic Error: " + e.toString()); 
                    }
                }
                
                // Give a small tick for scripts to settle
                setTimeout(runGeneration, 100);
            </script>
            </body>
            </html>
            """
            
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Main logic runs in the script block above
                }
            }
            webView.loadDataWithBaseURL("https://myedu.oshsu.kg/", html, "text/html", "UTF-8", null)
        }
    }
}