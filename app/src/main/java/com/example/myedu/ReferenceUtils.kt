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
                ?: throw Exception("Main JS missing in index.html")
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            
            // 1. Identify the logic file for Reference
            var refJsPath = findMatch(mainJsContent, """["']([^"']*References7\.[^"']+\.js)["']""")
            if (refJsPath == null) {
                val docsJsPath = findMatch(mainJsContent, """["']([^"']*StudentDocuments\.[^"']+\.js)["']""")
                if (docsJsPath != null) {
                    val docsContent = fetchString("$baseUrl/assets/${getName(docsJsPath)}")
                    refJsPath = findMatch(docsContent, """["']([^"']*References7\.[^"']+\.js)["']""")
                }
            }
            if (refJsPath == null) throw Exception("References7 JS missing in dependency chain")
            
            val entryFileName = getName(refJsPath)
            val scriptBuilder = StringBuilder()

            // 2. Polyfill Environment
            scriptBuilder.append("const Vue = window.Vue;\n")
            scriptBuilder.append("const { ref, computed, reactive, unref, toRef, watch, onMounted, getCurrentInstance, defineComponent, resolveComponent, openBlock, createElementBlock, createBaseVNode, toDisplayString, createVNode, withCtx, createTextVNode } = Vue;\n")
            scriptBuilder.append("const window_location = { origin: '$baseUrl' };\n")

            // 3. Recursively fetch modules
            logger("Fetching module tree starting from $entryFileName...")
            val entryVarName = fetchModuleRecursive(logger, entryFileName, scriptBuilder, dictionary, language)

            // 4. Find and Expose the PDF Generator
            var generatorVarName: String? = null
            for ((fileName, varName) in moduleVarNames) {
                val content = fetchedModules[fileName] ?: ""
                // Heuristic: pdfMake definitions use pageSize and A4/portrait
                if (content.contains("pageSize") && (content.contains("A4") || content.contains("portrait")) && content.contains("content")) {
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
            logger("Ref Fetch Error: ${e.message}")
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

        val uniqueVarName = "Mod_" + fileName.replace(Regex("[^a-zA-Z0-9]"), "_") + "_" + fileName.hashCode().toString().replace("-", "N")
        moduleVarNames[fileName] = uniqueVarName 

        // SAFETY CHECK 1: Skip non-JS files (CSS, etc) to prevent "Unexpected token" errors
        if (!fileName.endsWith(".js")) {
            sb.append("const $uniqueVarName = { default: {} }; // Skipped non-JS file\n")
            return uniqueVarName
        }

        var content = try {
            fetchString("$baseUrl/assets/$fileName")
        } catch (e: Exception) {
            logger("Failed to fetch $fileName: ${e.message}")
            "export default {};"
        }

        // SAFETY CHECK 2: Detect HTML 404 pages masquerading as JS
        if (content.trim().startsWith("<")) {
             logger("Warning: $fileName returned HTML (likely 404). Mocking empty.")
             sb.append("const $uniqueVarName = { default: {} }; // Mocked HTML response\n")
             return uniqueVarName
        }

        // Translate if needed
        if (language == "en" && dictionary.isNotEmpty()) {
             dictionary.forEach { (ru, en) -> 
                 if (ru.length > 3) content = content.replace(ru, en) 
             }
        }
        
        fetchedModules[fileName] = content

        // --- Pre-process Aggregation Exports ---
        // Convert `export { a } from 'b'` -> `import { a } from 'b'; export { a };`
        content = content.replace(Regex("""export\s*\{\s*([^}]+)\s*\}\s*from\s*["']([^"']+)["'];?""")) {
            val imports = it.groupValues[1]
            val path = it.groupValues[2]
            "import { $imports } from \"$path\"; export { $imports };"
        }

        // Handle `export * from 'b'` by converting to import and marking for spread later
        val starDependencies = mutableListOf<String>()
        content = content.replace(Regex("""export\s*\*\s*from\s*["']([^"']+)["'];?""")) {
            val path = it.groupValues[1]
            val starDepName = "StarDep_" + path.hashCode().toString().replace("-", "_")
            // Inject a special import we can catch in the main loop
            // Using "import * as Name" syntax which we will handle
            "import * as $starDepName from \"$path\"; /*STAR_EXPORT*/"
        }

        // --- Main Import Resolution ---
        // Regex handles:
        // 1. import Ident from "path"
        // 2. import { a, b } from "path"
        // 3. import * as ns from "path"
        // 4. import "path" (side effect)
        // It allows optional spaces around matching groups
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
                // Side effect: import "foo.css" -> replaced by comments in recursion if non-js
                replacementLine.append("// Side-effect import: $depFileName")
            } else if (importClause.contains("* as")) {
                // Namespace import: import * as NS from ...
                val varName = importClause.split("as")[1].trim()
                replacementLine.append("const $varName = $depVarName;")
                if (fullMatch.contains("/*STAR_EXPORT*/")) {
                    starDependencies.add(varName)
                }
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
            replacementLine.append("\n")
            
            replacements.add(fullMatch to replacementLine.toString())
        }

        var modifiedContent = content
        for ((target, replacement) in replacements) {
            modifiedContent = modifiedContent.replace(target, replacement)
        }
        
        // Cleanup remaining import artifacts (like CSS imports that Regex missed or malformed)
        modifiedContent = modifiedContent.replace(Regex("""import\s+["'][^"']+\.(css|svg|png|jpg|jpeg)["'];?"""), "// asset removed\n")

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
        
        // Handle `import.meta` which causes syntax errors in some webviews
        modifiedContent = modifiedContent.replace("import.meta", "({url: ''})")

        // --- Final Assembly ---
        sb.append("\n// --- Module: $fileName ---\n")
        sb.append("const $uniqueVarName = (() => {\n")
        sb.append(modifiedContent)
        sb.append("\n\nreturn { ")
        
        // Spread star exports first
        starDependencies.forEach { starVar ->
            sb.append("...$starVar, ")
        }
        
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