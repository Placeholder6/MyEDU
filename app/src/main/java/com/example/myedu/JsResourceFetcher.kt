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

    // Fallback 1x1 Transparent Image (prevents crash if Signed.js is missing)
    private val placeholderImage = "\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=\""

    suspend fun fetchResources(logger: (String) -> Unit, language: String, dictionary: Map<String, String> = emptyMap()): PdfResources = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch Main Index
            logger("Fetching index...")
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""") 
                ?: throw Exception("Main JS not found")
            
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            
            // 2. Fetch Transcript Script
            val transcriptJsName = findMatch(mainJsContent, """["']\./(Transcript\.[^"']+\.js)["']""") 
                ?: throw Exception("Transcript JS not found")
            
            logger("Fetching $transcriptJsName...")
            val transcriptContent = fetchString("$baseUrl/assets/$transcriptJsName")

            val dependencies = StringBuilder()
            val linkedVariables = mutableSetOf<String>()

            // ==================================================================================
            // DYNAMIC LINKER: Scans imports and links them regardless of variable names
            // ==================================================================================
            suspend fun linkDynamicModule(
                fileKeyword: String,        // Unique part of filename (e.g., "moment", "PdfStyle")
                fallbackValue: String? = null
            ) {
                try {
                    // Step A: Find the file and the import statement in Transcript.js
                    // This Regex captures:
                    // 1. The Exported Name (what the file provides, e.g., "h" or "default")
                    // 2. The Local Name (what Transcript.js calls it, e.g., "$")
                    // 3. The Full Filename
                    
                    // Pattern 1: Named Import -> import { h as $ } from "./moment.123.js"
                    val namedImportRegex = Regex("""import\s*\{\s*(\w+)\s+as\s+(\w+)\s*\}\s*from\s*["']\./($fileKeyword\.[^"']+\.js)["']""")
                    
                    // Pattern 2: Default Import -> import $ from "./moment.123.js"
                    val defaultImportRegex = Regex("""import\s+(\w+)\s+from\s*["']\./($fileKeyword\.[^"']+\.js)["']""")

                    var match = namedImportRegex.find(transcriptContent)
                    var isDefault = false

                    if (match == null) {
                        match = defaultImportRegex.find(transcriptContent)
                        isDefault = true
                    }

                    if (match != null) {
                        val fileName = if (isDefault) match.groupValues[2] else match.groupValues[3]
                        val localName = if (isDefault) match.groupValues[1] else match.groupValues[2]
                        val exportName = if (isDefault) "default" else match.groupValues[1]

                        logger("Linking '$localName' from $fileName (Export: $exportName)")
                        
                        var fileContent = fetchString("$baseUrl/assets/$fileName")
                        
                        // Apply translations if needed
                        if (language == "en") {
                            fileContent = applyDictionary(fileContent, dictionary)
                        }

                        // Step B: Find the internal variable inside the fetched file
                        // e.g., if Transcript imports 'h', we check moment.js for 'export { l as h }'
                        val internalVar = if (isDefault) {
                             findExportedDefaultName(fileContent)
                        } else {
                             findExportedInternalName(fileContent, exportName)
                        } ?: "{}"

                        // Step C: Clean the file content (remove imports/exports to make it run in WebView)
                        val cleanContent = cleanJsContent(fileContent)

                        // Step D: Wrap and Append
                        dependencies.append("var $localName = (() => {\n$cleanContent\nreturn $internalVar;\n})();\n")
                        linkedVariables.add(localName)

                    } else {
                        // Fallback: If file not found, we try to guess the variable name to prevent crash
                        if (fallbackValue != null) {
                            logger("WARN: $fileKeyword not found. Using fallback.")
                            // Try to guess the variable name by looking for a simpler import signature
                            val guessRegex = Regex("""import\s*\{\s*\w+\s+as\s+(\w+)\s*\}\s*from.*$fileKeyword""")
                            guessRegex.find(transcriptContent)?.let { 
                                val guessedVar = it.groupValues[1]
                                dependencies.append("var $guessedVar = $fallbackValue;\n")
                                linkedVariables.add(guessedVar)
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger("Link Error ($fileKeyword): ${e.message}")
                }
            }

            // 3. Link All Dependencies Dynamically
            // This order doesn't strictly matter, but good for organization
            linkDynamicModule("moment")       // FIXES DATE OF BIRTH (The '$' variable)
            linkDynamicModule("PdfStyle")
            linkDynamicModule("PdfFooter4", "()=>({})")
            linkDynamicModule("KeysValue")
            linkDynamicModule("Signed", placeholderImage) // Fallback image if signature fails
            linkDynamicModule("helpers")
            linkDynamicModule("ru")

            // 4. Robust Mocking for any leftovers
            // If we missed any imports, this creates a "UniversalDummy" to prevent "is not defined" errors.
            val dummyScript = StringBuilder()
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

            // Find ALL named imports in Transcript.js
            val allImports = Regex("""import\s*\{(.*?)\}\s*from""").findAll(transcriptContent)
            allImports.forEach { m ->
                m.groupValues[1].split(",").forEach { 
                    val parts = it.trim().split(Regex("""\s+as\s+"""))
                    if (parts.size == 2) {
                        val varName = parts[1].trim()
                        // Only mock if we haven't already linked it correctly above
                        if (!linkedVariables.contains(varName)) {
                            dummyScript.append("\nvar $varName = UniversalDummy;")
                        }
                    }
                }
            }

            // 5. Final Assembly
            var cleanTranscript = cleanJsContent(transcriptContent)
            if (language == "en") {
                cleanTranscript = applyDictionary(cleanTranscript, dictionary)
            }

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

    // Helper: Finds "export { internal as Exported }"
    private fun findExportedInternalName(content: String, exportName: String): String? {
        // Case A: export { l as h }  (Minified named export)
        val regexAs = Regex("""export\s*\{\s*(\w+)\s+as\s+$exportName\s*\}""")
        regexAs.find(content)?.let { return it.groupValues[1] }
        
        // Case B: export { h } (Direct export)
        val regexDirect = Regex("""export\s*\{[^}]*\b$exportName\b[^}]*\}""")
        if (regexDirect.containsMatchIn(content)) return exportName
        
        return null
    }
    
    // Helper: Finds "export default internal"
    private fun findExportedDefaultName(content: String): String? {
        val regexDefault = Regex("""export\s+default\s+(\w+)""")
        regexDefault.find(content)?.let { return it.groupValues[1] }
        return null
    }

    private fun cleanJsContent(content: String): String {
        return content
            .replace(Regex("""import\s*\{.*?\}\s*from\s*['"].*?['"];?""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""import\s+\w+\s+from\s*['"].*?['"];?"""), "")
            .replace(Regex("""export\s*\{.*?\}""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""export\s+default"""), "")
    }

    private fun applyDictionary(script: String, dictionary: Map<String, String>): String {
        var s = script
        dictionary.forEach { (ru, en) -> if (ru.length > 1) s = s.replace(ru, en) }
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