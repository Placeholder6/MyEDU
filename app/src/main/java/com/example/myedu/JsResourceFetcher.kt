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

            // 3. Helper to link specific modules
            suspend fun linkModule(
                keyword: String, 
                varNameFallback: String, 
                fallbackValue: String
            ) {
                try {
                    // Pattern: import { P as J } from "./PdfStyle.123.js"
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
                        
                        val internalVar = findExportedInternalName(fileContent, internalExportName) ?: "{}"
                        val cleanContent = cleanJsContent(fileContent)

                        dependencies.append("var $variableName = (() => {\n$cleanContent\nreturn $internalVar;\n})();\n")
                    } else {
                         // Fallback if file not found but variable is needed
                         val fallbackRegex = Regex("""import\s*\{\s*$varNameFallback\s+as\s+(\w+)\s*\}""")
                         val fbMatch = fallbackRegex.find(transcriptContent)
                         if(fbMatch != null) {
                             dependencies.append("var ${fbMatch.groupValues[1]} = $fallbackValue;\n")
                         }
                    }
                } catch (e: Exception) {
                    logger("Link Warn ($keyword): ${e.message}")
                }
            }

            // 4. Handle Moment.js (The Date Formatter) explicitly
            // This fixes the missing Date of Birth. We mock it with a real date formatter.
            val momentRegex = Regex("""import\s+(\w+)\s+from\s*["']\./(moment\.[^"']+\.js)["']""")
            val momentMatch = momentRegex.find(transcriptContent)
            
            if (momentMatch != null) {
                val varName = momentMatch.groupValues[1] // Likely "$"
                logger("Linking Moment.js as '$varName'...")
                
                // We inject a custom formatter that mimics moment.js behavior
                val locale = if (language == "en") "en-US" else "ru-RU"
                val momentMock = """
                    var $varName = function(d) { 
                        return { 
                            format: function(f) { 
                                var date = d ? new Date(d) : new Date();
                                return date.toLocaleDateString("$locale"); 
                            },
                            locale: function() {}
                        }; 
                    };
                """.trimIndent()
                dependencies.append(momentMock).append("\n")
            }

            // 5. Link other dependencies
            linkModule("PdfStyle", "P", "{}")
            linkModule("PdfFooter4", "P", "()=>({})")
            linkModule("KeysValue", "k", "(n)=>n")
            linkModule("Signed", "S", placeholderImage)
            linkModule("helpers", "e", "(...a)=>a.join(' ')")
            linkModule("ru", "r", "{}")

            // 6. Mock everything else with UniversalDummy
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

            // Find all named imports to mock
            val allImports = Regex("""import\s*\{(.*?)\}\s*from""").findAll(transcriptContent)
            allImports.forEach { m ->
                m.groupValues[1].split(",").forEach { 
                    val parts = it.trim().split(Regex("""\s+as\s+"""))
                    if (parts.size == 2) {
                        val varName = parts[1].trim()
                        if (!dependencies.contains("var $varName =")) {
                            dummyScript.append("\nvar $varName = UniversalDummy;")
                        }
                    }
                }
            }

            // 7. Prepare final script
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

    private fun findExportedInternalName(content: String, exportName: String): String? {
        val regexAs = Regex("""export\s*\{\s*(\w+)\s+as\s+$exportName\s*\}""")
        regexAs.find(content)?.let { return it.groupValues[1] }
        
        if (content.contains("export { $exportName }") || content.contains("export {$exportName}")) return exportName
        
        val regexDirect = Regex("""export\s*\{[^}]*\b$exportName\b[^}]*\}""")
        if (regexDirect.containsMatchIn(content)) return exportName
        return null
    }

    private fun cleanJsContent(content: String): String {
        return content
            // Remove named imports: import { ... } from ...
            .replace(Regex("""import\s*\{.*?\}\s*from\s*['"].*?['"];?""", RegexOption.DOT_MATCHES_ALL), "")
            // Remove default imports: import X from ...
            .replace(Regex("""import\s+\w+\s+from\s*['"].*?['"];?"""), "")
            // Remove exports
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