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
            val transcriptContent = fetchString("$baseUrl/assets/$transcriptJsName")

            // 4. LINK DEPENDENCIES (With Scope Isolation)
            val dependencies = StringBuilder()
            
            // Helper to fetch, wrap, and link a module
            // Wraps code in (() => { ... return exportedVar; })() to prevent "const t" collisions.
            suspend fun linkModule(importRegex: Regex, exportRegex: Regex, finalVarName: String, fallbackName: String = "") {
                // Try to find filename in Transcript.js, then Main.js
                val fileNameMatch = importRegex.find(transcriptContent) ?: importRegex.find(mainJsContent)
                
                if (fileNameMatch != null) {
                    val fileName = fileNameMatch.groupValues[1]
                    logger("Linking $finalVarName from $fileName")
                    val fileContent = fetchString("$baseUrl/assets/$fileName")
                    
                    // Find the internal variable name (e.g. export { t as P })
                    val internalVarMatch = exportRegex.find(fileContent)
                    
                    if (internalVarMatch != null) {
                        val internalVar = internalVarMatch.groupValues[1]
                        
                        // Strip export statement
                        val cleanContent = fileContent.replace(Regex("""export\s*\{.*?\}"""), "")
                        
                        // Wrap in IIFE to isolate scope
                        dependencies.append("const $finalVarName = (() => {\n")
                        dependencies.append(cleanContent).append("\n")
                        dependencies.append("return $internalVar;\n")
                        dependencies.append("})();\n")
                    } else {
                        logger("WARNING: Export not found in $fileName")
                    }
                } else {
                    logger("WARNING: Import for $finalVarName not found.")
                    // If critical (like T), provide a dummy fallback
                    if (fallbackName.isNotEmpty()) {
                        dependencies.append("const $finalVarName = $fallbackName;\n")
                    }
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

            // E. Helpers -> ct (Import: e as ct, Export: d as e)
            linkModule(Regex("""from\s*["']\./(helpers\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*.*?(\w+)\s+as\s+e"""), "ct")

            // F. Ru Locale -> T (Import: r as T, Export: a as r) -> Fallback to empty obj
            linkModule(Regex("""from\s*["']\./(ru\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+r\s*\}"""), "T", "{}")

            // 5. PREPARE DUMMY VARIABLES FOR OTHERS
            // Extract all other imports (like Vue's 'at') and make them dummies so the script doesn't crash.
            val varsToMock = mutableSetOf<String>()
            val importRegex = Regex("""import\s*\{(.*?)\}\s*from\s*['"].*?['"];?""")
            importRegex.findAll(transcriptContent).forEach { match ->
                match.groupValues[1].split(",").forEach { item ->
                    val parts = item.trim().split(Regex("""\s+as\s+"""))
                    val varName = if (parts.size == 2) parts[1] else parts[0]
                    if (varName.isNotBlank()) varsToMock.add(varName.trim())
                }
            }
            // Don't mock the ones we just linked manually
            varsToMock.removeAll(setOf("J", "U", "K", "mt", "ct", "T", "$"))

            val dummyScript = StringBuilder()
            dummyScript.append("const UniversalDummy = new Proxy(function(){}, { get: () => UniversalDummy, apply: () => UniversalDummy, construct: () => UniversalDummy });\n")
            if (varsToMock.isNotEmpty()) {
                dummyScript.append("var ")
                dummyScript.append(varsToMock.joinToString(",") { "$it = UniversalDummy" })
                dummyScript.append(";\n")
            }

            // 6. PREPARE TRANSCRIPT.JS
            val cleanTranscript = transcriptContent
                .replace(importRegex, "") 
                .replace(Regex("""export\s+default"""), "const TranscriptModule =")
                .replace(Regex("""export\s*\{.*?\}"""), "")

            // 7. EXPOSE GENERATOR
            val funcNameMatch = findMatch(cleanTranscript, """(\w+)\s*=\s*\(C,a,c,d\)""")
            val exposeCode = if (funcNameMatch != null) {
                "\nwindow.PDFGenerator = $funcNameMatch;"
            } else {
                logger("CRITICAL: Generator function not found!")
                ""
            }

            // 8. COMBINE
            // Order: Dummies -> Dependencies -> Main Script -> Expose
            val finalScript = dummyScript.toString() + dependencies.toString() + "\n" + cleanTranscript + exposeCode
            
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