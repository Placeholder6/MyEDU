package com.example.myedu

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PdfResources(
    val combinedScript: String
)

class JsResourceFetcher {

    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"

    // 1x1 Transparent Pixel to prevent "Invalid Image" crashes if signature fails
    private val placeholderImage = "\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=\""

    suspend fun fetchResources(logger: (String) -> Unit, language: String, dictionary: Map<String, String> = emptyMap()): PdfResources = withContext(Dispatchers.IO) {
        try {
            // 1. Find the Main Script
            logger("Fetching index...")
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""") 
                ?: throw Exception("Main JS not found")
            
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            
            // 2. Find the Transcript Script
            val transcriptJsName = findMatch(mainJsContent, """["']\./(Transcript\.[^"']+\.js)["']""") 
                ?: throw Exception("Transcript JS not found")
            
            logger("Fetching $transcriptJsName...")
            val transcriptContent = fetchString("$baseUrl/assets/$transcriptJsName")

            val dependencies = StringBuilder()

            // 3. Dynamic Linking Function
            // Scans transcriptContent for imports and downloads them
            suspend fun linkModule(
                keyword: String, 
                varNameFallback: String, 
                fallbackValue: String
            ) {
                try {
                    // Try to find what variable name the script uses for this module
                    // Matches: import { P as J } from "./PdfStyle.123.js"  -> captures "J" and "PdfStyle.123.js"
                    // Also matches: import { k as X } from "./KeysValue.abc.js"
                    val regex = Regex("""import\s*\{\s*(\w+)\s+as\s+(\w+)\s*\}\s*from\s*["']\./($keyword\.[^"']+\.js)["']""")
                    val match = regex.find(transcriptContent)

                    if (match != null) {
                        val internalExportName = match.groupValues[1] // e.g. "P"
                        val variableName = match.groupValues[2]       // e.g. "J"
                        val fileName = match.groupValues[3]           // e.g. "PdfStyle.123.js"

                        logger("Linking $variableName ($keyword)...")
                        var fileContent = fetchString("$baseUrl/assets/$fileName")

                        if (language == "en") {
                            fileContent = applyDictionary(fileContent, dictionary)
                        }

                        // Find the internal variable inside the file to export
                        val internalVar = findExportedInternalName(fileContent, internalExportName) ?: "{}"
                        
                        // Clean the file (remove imports/exports)
                        val cleanContent = cleanJsContent(fileContent)

                        // Wrap in a closure and assign to the variable expected by Transcript.js
                        dependencies.append("var $variableName = (() => {\n$cleanContent\nreturn $internalVar;\n})();\n")
                    } else {
                        // If not found, try to find just the variable name to assign a fallback
                         logger("WARN: $keyword not imported. Using fallback.")
                         // Use a simpler regex to guess the variable name if the file wasn't found
                         val fallbackRegex = Regex("""import\s*\{\s*$varNameFallback\s+as\s+(\w+)\s*\}""")
                         val fbMatch = fallbackRegex.find(transcriptContent)
                         if(fbMatch != null) {
                             dependencies.append("var ${fbMatch.groupValues[1]} = $fallbackValue;\n")
                         }
                    }
                } catch (e: Exception) {
                    logger("Link Error ($keyword): ${e.message}")
                }
            }

            // 4. Link known modules dynamically
            // The fetcher will look for the actual filename in Transcript.js
            linkModule("PdfStyle", "P", "{}")
            linkModule("PdfFooter4", "P", "()=>({})")
            linkModule("KeysValue", "k", "(n)=>n")
            linkModule("Signed", "S", placeholderImage) // Fallback to empty image prevents crash
            linkModule("helpers", "e", "(...a)=>a.join(' ')")
            linkModule("ru", "r", "{}")

            // 5. Mock any remaining missing imports to prevent "is not defined" errors
            // This handles 'moment.js' (aliased as $) or others automatically
            val dummyScript = StringBuilder()
            // Robust Proxy that handles numbers and strings safely
            dummyScript.append("""
                const UniversalDummy = new Proxy(function(){}, {
                    get: function(target, prop) {
                        if(prop === Symbol.toPrimitive) return (hint) => hint === 'number' ? 0 : "";
                        if(prop === 'toString') return () => "";
                        if(prop === 'valueOf') return () => 0;
                        return UniversalDummy;
                    },
                    apply: () => UniversalDummy,
                    construct: () => UniversalDummy
                });
            """.trimIndent())

            // Find all imports like: import { $ as e, ... } from ...
            val allImports = Regex("""import\s*\{(.*?)\}\s*from""").findAll(transcriptContent)
            allImports.forEach { m ->
                m.groupValues[1].split(",").forEach { 
                    val parts = it.trim().split(Regex("""\s+as\s+"""))
                    if (parts.size == 2) {
                        val varName = parts[1].trim()
                        // Only mock if we haven't already defined it in 'dependencies'
                        if (!dependencies.contains("var $varName =")) {
                            dummyScript.append("\nvar $varName = UniversalDummy;")
                        }
                    }
                }
            }

            // 6. Prepare final Transcript script
            var cleanTranscript = cleanJsContent(transcriptContent)
            if (language == "en") {
                cleanTranscript = applyDictionary(cleanTranscript, dictionary)
            }

            // 7. Expose the Generator function (usually found at the end)
            val funcNameMatch = findMatch(cleanTranscript, """(\w+)\s*=\s*\(C,a,c,d\)""")
            val exposeCode = if (funcNameMatch != null) "\nwindow.PDFGenerator = $funcNameMatch;" else ""

            val finalScript = dummyScript.toString() + "\n" + dependencies.toString() + "\n" + cleanTranscript + exposeCode
            
            return@withContext PdfResources(finalScript)

        } catch (e: Exception) {
            logger("Fetch Error: ${e.message}")
            e.printStackTrace()
            return@withContext PdfResources("")
        }
    }

    private fun findExportedInternalName(content: String, exportName: String): String? {
        // Case 1: export { internal as P }
        val regexAs = Regex("""export\s*\{\s*(\w+)\s+as\s+$exportName\s*\}""")
        regexAs.find(content)?.let { return it.groupValues[1] }
        
        // Case 2: export { P }
        if (content.contains("export { $exportName }") || content.contains("export {$exportName}")) return exportName
        
        // Case 3: export { ..., P, ... }
        val regexDirect = Regex("""export\s*\{[^}]*\b$exportName\b[^}]*\}""")
        if (regexDirect.containsMatchIn(content)) return exportName

        return null
    }

    private fun cleanJsContent(content: String): String {
        return content
            .replace(Regex("""import\s*\{.*?\}\s*from\s*['"].*?['"];?"""), "")
            .replace(Regex("""export\s*\{.*?\}"""), "")
            .replace(Regex("""export\s+default"""), "")
    }

    private fun applyDictionary(script: String, dictionary: Map<String, String>): String {
        var s = script
        dictionary.forEach { (ru, en) -> 
            if (ru.length > 1) s = s.replace(ru, en) 
        }
        return s
    }

    private fun fetchString(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            return response.body?.string() ?: ""
        }
    }

    private fun findMatch(content: String, regex: String): String? {
        return Regex(regex).find(content)?.let { match ->
            if (match.groupValues.size > 1) match.groupValues[1] else match.groupValues[0]
        }
    }
}