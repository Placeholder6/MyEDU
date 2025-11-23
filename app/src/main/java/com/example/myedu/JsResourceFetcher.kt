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

            // 4. LINK DEPENDENCIES (With Scope Isolation)
            val dependencies = StringBuilder()
            
            // Function to safely wrap and link a module
            suspend fun linkModule(importRegex: Regex, exportAlias: String, finalVarName: String) {
                // Find the filename (e.g., PdfStyle.hash.js)
                val fileNameMatch = importRegex.find(transcriptContent) ?: importRegex.find(mainJsContent)
                
                if (fileNameMatch != null) {
                    val fileName = fileNameMatch.groupValues[1]
                    logger("Linking $finalVarName from $fileName")
                    val fileContent = fetchString("$baseUrl/assets/$fileName")
                    
                    // Find what internal variable maps to the export alias
                    // Example: export { t as P } -> We want 't' when looking for 'P'
                    // Regex handles: "export{t as P}" or "export { t as P, ... }"
                    val exportRegex = Regex("""export\s*\{[^}]*?(\w+)\s+as\s+$exportAlias[^}]*?\}""")
                    val internalVarMatch = exportRegex.find(fileContent)
                    
                    if (internalVarMatch != null) {
                        val internalVar = internalVarMatch.groupValues[1]
                        
                        // Clean content: remove export statement
                        val cleanContent = fileContent.replace(Regex("""export\s*\{.*?\}"""), "")
                        
                        // Wrap in IIFE (Immediately Invoked Function Expression) to isolate scope
                        // const J = (() => { [CONTENT]; return t; })();
                        dependencies.append("const $finalVarName = (() => {\n")
                        dependencies.append(cleanContent).append("\n")
                        dependencies.append("return $internalVar;\n")
                        dependencies.append("})();\n")
                        
                    } else {
                        logger("WARNING: Could not find export '$exportAlias' in $fileName")
                    }
                } else {
                    logger("WARNING: Import for $finalVarName not found.")
                }
            }

            // A. PdfStyle -> J (Import: P as J, File exports: t as P) -> J = t
            linkModule(Regex("""from\s*["']\./(PdfStyle\.[^"']+\.js)["']"""), "P", "J")
            
            // B. PdfFooter -> U (Import: P as U, File exports: o as P) -> U = o
            linkModule(Regex("""from\s*["']\./(PdfFooter4\.[^"']+\.js)["']"""), "P", "U")
            
            // C. KeysValue -> K (Import: k as K, File exports: t as k) -> K = t
            linkModule(Regex("""from\s*["']\./(KeysValue\.[^"']+\.js)["']"""), "k", "K")
            
            // D. Signed -> mt (Import: S as mt, File exports: A as S) -> mt = A
            linkModule(Regex("""from\s*["']\./(Signed\.[^"']+\.js)["']"""), "S", "mt")

            // E. Helpers -> ct (Import: e as ct, File exports: d as e) -> ct = d
            linkModule(Regex("""from\s*["']\./(helpers\.[^"']+\.js)["']"""), "e", "ct")

            // 5. PREPARE TRANSCRIPT.JS
            val cleanTranscript = transcriptContent
                .replace(Regex("""import\s*\{.*?\}\s*from\s*['"].*?['"];?"""), "")
                .replace(Regex("""export\s+default"""), "const TranscriptModule =")
                .replace(Regex("""export\s*\{.*?\}"""), "")

            // 6. EXPOSE GENERATOR
            val funcNameMatch = findMatch(cleanTranscript, """(\w+)\s*=\s*\(C,a,c,d\)""")
            val exposeCode = if (funcNameMatch != null) {
                "\nwindow.PDFGenerator = $funcNameMatch;"
            } else {
                logger("CRITICAL: Generator function not found!")
                ""
            }

            // 7. COMBINE
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