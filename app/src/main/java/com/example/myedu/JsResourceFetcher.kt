package com.example.myedu

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Data class needed by other files
data class PdfResources(
    val combinedScript: String
)

class JsResourceFetcher {

    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"

    // Transparent 1x1 pixel base64 to prevent crashes if signature image fails
    private val placeholderImage = "\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=\""

    suspend fun fetchResources(logger: (String) -> Unit, language: String, dictionary: Map<String, String> = emptyMap()): PdfResources = withContext(Dispatchers.IO) {
        try {
            // 1. Get Main JS to find Transcript JS
            logger("Fetching index...")
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""") 
                ?: throw Exception("Main JS not found")
            
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            
            // 2. Find Transcript JS filename
            val transcriptJsName = findMatch(mainJsContent, """["']\./(Transcript\.[^"']+\.js)["']""") 
                ?: throw Exception("Transcript JS not found")
            
            logger("Fetching $transcriptJsName...")
            var transcriptContent = fetchString("$baseUrl/assets/$transcriptJsName")

            // 3. Dynamic Linker (The "Reference Generator" Logic)
            val dependencies = StringBuilder()
            
            suspend fun linkModule(
                fileKeyword: String,     // e.g., "PdfStyle"
                exportName: String,      // e.g., "P" (the variable name exported inside the file)
                fallbackValue: String    // e.g., "{}" if download fails
            ) {
                try {
                    // A. Find what variable name Transcript.js uses for this module
                    // Pattern: import { P as SomeVar } from "./PdfStyle.hash.js"
                    val importRegex = Regex("""import\s*\{\s*$exportName\s+as\s+(\w+)\s*\}\s*from\s*["']\./($fileKeyword\.[^"']+\.js)["']""")
                    val match = importRegex.find(transcriptContent)

                    if (match != null) {
                        val localName = match.groupValues[1] // e.g., "J" or "a"
                        val fileName = match.groupValues[2]  // e.g., "PdfStyle.123.js"

                        logger("Linking $localName from $fileName")
                        var fileContent = fetchString("$baseUrl/assets/$fileName")

                        // Apply dictionary if needed (for EN translation)
                        if (language == "en") {
                            fileContent = applyDictionary(fileContent, dictionary)
                        }

                        // B. Extract the specific export from the file
                        // Pattern: export { ... a as P ... } OR export { P }
                        // We need to find the internal variable name mapped to 'exportName'
                        val internalVar = findExportedInternalName(fileContent, exportName) ?: "{}"
                        
                        // C. Clean content (remove imports/exports) so it runs in WebView
                        val cleanContent = cleanJsContent(fileContent)

                        // D. Append to our big script: var LocalName = (() => { ... return InternalVar })();
                        dependencies.append("var $localName = (() => {\n$cleanContent\nreturn $internalVar;\n})();\n")
                    } else {
                        logger("Note: $fileKeyword not imported by Transcript.js")
                    }
                } catch (e: Exception) {
                    logger("Link Warn ($fileKeyword): ${e.message}")
                    // Try to find the variable name to assign the fallback
                    val fallbackRegex = Regex("""import\s*\{\s*$exportName\s+as\s+(\w+)\s*\}""")
                    fallbackRegex.find(transcriptContent)?.let { 
                        dependencies.append("var ${it.groupValues[1]} = $fallbackValue;\n")
                    }
                }
            }

            // 4. Link all known dependencies dynamically
            linkModule("PdfStyle", "P", "{}")
            linkModule("PdfFooter4", "P", "()=>({})")
            linkModule("KeysValue", "k", "(n)=>n")
            linkModule("Signed", "S", placeholderImage) // <--- Falls back to empty image if fails
            linkModule("helpers", "e", "(...a)=>a.join(' ')")
            linkModule("ru", "r", "{}")

            // 5. Clean the Main Transcript Script
            var cleanTranscript = cleanJsContent(transcriptContent)
            if (language == "en") {
                cleanTranscript = applyDictionary(cleanTranscript, dictionary)
            }

            // 6. Mock any other missing imports (Robustness)
            // This creates a "UniversalDummy" for any import we didn't explicitly handle above.
            val dummyScript = StringBuilder()
            val remainingImports = Regex("""import\s*\{(.*?)\}\s*from""").findAll(transcriptContent)
            val mockedVars = mutableSetOf<String>()
            
            remainingImports.forEach { m ->
                m.groupValues[1].split(",").forEach { 
                    val parts = it.trim().split(Regex("""\s+as\s+"""))
                    if (parts.size == 2) mockedVars.add(parts[1]) 
                }
            }
            
            // Create a Proxy that returns 0 for numbers and "" for strings to prevent crashes
            dummyScript.append("""
                const UniversalDummy = new Proxy(function(){}, {
                    get: function(target, prop) {
                        if(prop === Symbol.toPrimitive) return (hint) => hint === 'number' ? 0 : "";
                        if(prop === 'toString') return () => "";
                        if(prop === 'valueOf') return () => 0;
                        return UniversalDummy;
                    },
                    apply: () => UniversalDummy
                });
            """.trimIndent())
            
            mockedVars.forEach { varName ->
                // Only mock if not already defined by our linkModule calls
                if (!dependencies.contains("var $varName =")) {
                    dummyScript.append("\nvar $varName = UniversalDummy;")
                }
            }

            // 7. Expose the Generator Function
            // Finds: const X = (C,a,c,d) => ...
            val funcNameMatch = findMatch(cleanTranscript, """(\w+)\s*=\s*\(C,a,c,d\)""")
            val exposeCode = if (funcNameMatch != null) "\nwindow.PDFGenerator = $funcNameMatch;" else ""

            // 8. Assemble Final Script
            val finalScript = dummyScript.toString() + "\n" + dependencies.toString() + "\n" + cleanTranscript + exposeCode
            
            return@withContext PdfResources(finalScript)

        } catch (e: Exception) {
            logger("Fetch Error: ${e.message}")
            e.printStackTrace()
            return@withContext PdfResources("")
        }
    }

    // Helper: Finds the internal name of an exported variable
    // e.g. "export { a as P }" -> returns "a"
    // e.g. "export { P }" -> returns "P"
    private fun findExportedInternalName(content: String, exportName: String): String? {
        // Try named export: { a as P }
        val regexAs = Regex("""export\s*\{\s*(\w+)\s+as\s+$exportName\s*\}""")
        regexAs.find(content)?.let { return it.groupValues[1] }

        // Try direct export: { P }
        val regexDirect = Regex("""export\s*\{\s*[^}]*\b$exportName\b[^}]*\}""")
        if (regexDirect.containsMatchIn(content)) return exportName

        return null
    }

    private fun cleanJsContent(content: String): String {
        return content
            .replace(Regex("""import\s*\{.*?\}\s*from\s*['"].*?['"];?"""), "") // Remove imports
            .replace(Regex("""export\s*\{.*?\}"""), "") // Remove export statements
            .replace(Regex("""export\s+default"""), "") // Remove default exports
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