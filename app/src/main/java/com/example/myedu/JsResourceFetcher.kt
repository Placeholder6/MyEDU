package com.example.myedu

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// This data class holds the final executable script
data class PdfResources(
    val combinedScript: String
)

class JsResourceFetcher {

    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"

    suspend fun fetchResources(logger: (String) -> Unit): PdfResources = withContext(Dispatchers.IO) {
        try {
            // 1. Index
            logger("Fetching index.html...")
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""") 
                ?: throw Exception("Main JS missing")
            
            // 2. Main JS
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            val transcriptJsName = findMatch(mainJsContent, """["']\./(Transcript\.[^"']+\.js)["']""") 
                ?: throw Exception("Transcript JS missing")
            
            // 3. Transcript JS
            logger("Fetching $transcriptJsName...")
            var transcriptContent = fetchString("$baseUrl/assets/$transcriptJsName")

            // 4. LINK DEPENDENCIES
            // We find the filenames for Style, Footer, Keys, Signed, and fetch them.
            val dependencies = StringBuilder()
            
            // Helper to fetch and link a module
            // e.g. import { P as J } from "./PdfStyle..." -> turns into -> const J = t;
            suspend fun linkModule(importRegex: Regex, exportRegex: Regex, varName: String) {
                // Try to find the import line in Transcript.js, fallback to Main.js
                val fileNameMatch = importRegex.find(transcriptContent) ?: importRegex.find(mainJsContent)
                
                if (fileNameMatch != null) {
                    val fileName = fileNameMatch.groupValues[1]
                    logger("Linking $varName from $fileName")
                    val fileContent = fetchString("$baseUrl/assets/$fileName")
                    
                    // Find the internal variable name it exports (e.g., export { t as P })
                    val internalVarMatch = exportRegex.find(fileContent)
                    if (internalVarMatch != null) {
                        val internalVar = internalVarMatch.groupValues[1]
                        // Strip the export statement so it's valid in WebView
                        val cleanContent = fileContent.replace(Regex("""export\s*\{.*?\}"""), "")
                        dependencies.append(cleanContent).append("\n")
                        // Glue the internal name to the name expected by Transcript.js
                        dependencies.append("const $varName = $internalVar;\n")
                    } else {
                        logger("WARNING: Could not find export in $fileName")
                    }
                } else {
                    logger("WARNING: Could not find import for $varName")
                }
            }

            // A. PdfStyle -> J (Import: P as J, Export: t as P)
            linkModule(Regex("""from\s*["']\./(PdfStyle\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+P\s*\}"""), "J")
            
            // B. PdfFooter -> U (Import: P as U, Export: o as P)
            linkModule(Regex("""from\s*["']\./(PdfFooter4\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+P\s*\}"""), "U")
            
            // C. KeysValue -> K (Import: k as K, Export: t as k)
            linkModule(Regex("""from\s*["']\./(KeysValue\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+k\s*\}"""), "K")
            
            // D. Signed -> mt (Import: S as mt, Export: A as S)
            linkModule(Regex("""from\s*["']\./(Signed\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+S\s*\}"""), "mt")

            // 5. PREPARE TRANSCRIPT.JS
            // Strip imports and exports
            val cleanTranscript = transcriptContent
                .replace(Regex("""import\s*\{.*?\}\s*from\s*['"].*?['"];?"""), "")
                .replace(Regex("""export\s+default"""), "const TranscriptModule =")
                .replace(Regex("""export\s*\{.*?\}"""), "")

            // 6. EXPOSE GENERATOR (Y)
            // Looks for Y=(C,a,c,d)
            val funcNameMatch = findMatch(cleanTranscript, """(\w+)\s*=\s*\(C,a,c,d\)""")
            val exposeCode = if (funcNameMatch != null) {
                "\nwindow.PDFGenerator = $funcNameMatch;"
            } else {
                logger("CRITICAL: Generator function not found in Transcript.js!")
                ""
            }

            // 7. COMBINE EVERYTHING
            val finalScript = dependencies.toString() + "\n" + cleanTranscript + exposeCode
            
            return@withContext PdfResources(finalScript)

        } catch (e: Exception) {
            logger("Fetch Error: ${e.message}")
            e.printStackTrace()
            return@withContext PdfResources("")
        }
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