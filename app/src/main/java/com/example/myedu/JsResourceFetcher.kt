package com.example.myedu

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PdfResources(
    val stampBase64: String,
    val logicCode: String
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
                ?: throw Exception("Main JS not found")
            
            // 2. Main JS
            logger("Fetching Main JS ($mainJsName)...")
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            val transcriptJsName = findMatch(mainJsContent, """["']\./(Transcript\.[^"']+\.js)["']""")
                ?: throw Exception("Transcript JS not found")
            
            // 3. Transcript JS
            logger("Fetching Transcript JS ($transcriptJsName)...")
            var transcriptJsContent = fetchString("$baseUrl/assets/$transcriptJsName")

            // 4. STAMP EXTRACTION (Done BEFORE cleaning code)
            var stampBase64 = ""
            logger("STAMP DEBUG: Looking for Signed.js reference...")
            
            // Find filename: import ... from "./Signed.HASH.js"
            val signedJsName = findMatch(transcriptJsContent, """from\s*["']\./(Signed\.[^"']+\.js)["']""") 
                ?: findMatch(mainJsContent, """["']\./(Signed\.[^"']+\.js)["']""")

            if (signedJsName != null) {
                logger("STAMP DEBUG: Found filename: $signedJsName")
                try {
                    val signedContent = fetchString("$baseUrl/assets/$signedJsName")
                    
                    // Regex: Find any "data:image/..." string inside quotes
                    val stampMatch = Regex("""['"](data:image/[^;]+;base64,[^'"]+)['"]""").find(signedContent)
                    
                    if (stampMatch != null) {
                        stampBase64 = stampMatch.groupValues[1]
                        logger("STAMP DEBUG: SUCCESS! Extracted ${stampBase64.length} bytes")
                    } else {
                        logger("STAMP DEBUG: FAILURE. File downloaded but no 'data:image' found.")
                        // Log snippet to debug
                        logger("Snippet: ${signedContent.take(50)}")
                    }
                } catch (e: Exception) {
                    logger("STAMP DEBUG: Network failed for Signed.js: ${e.message}")
                }
            } else {
                logger("STAMP DEBUG: Signed.js import not found in Transcript.js!")
            }

            // 5. MOCK IMPORTS
            val varsToMock = mutableSetOf<String>()
            val importRegex = Regex("""import\s*\{(.*?)\}\s*from\s*['"].*?['"];?""")
            importRegex.findAll(transcriptJsContent).forEach { match ->
                match.groupValues[1].split(",").forEach { item ->
                    val parts = item.trim().split(Regex("""\s+as\s+"""))
                    val varName = if (parts.size == 2) parts[1] else parts[0]
                    if (varName.isNotBlank()) varsToMock.add(varName.trim())
                }
            }
            varsToMock.removeAll(setOf("J", "U", "K", "$", "mt", "ct"))

            val dummyScript = StringBuilder()
            dummyScript.append("const UniversalDummy = new Proxy(function(){}, { get: () => UniversalDummy, apply: () => UniversalDummy, construct: () => UniversalDummy });\n")
            if (varsToMock.isNotEmpty()) {
                dummyScript.append("var ")
                dummyScript.append(varsToMock.joinToString(",") { "$it = UniversalDummy" })
                dummyScript.append(";\n")
            }

            // 6. CLEAN CODE
            transcriptJsContent = transcriptJsContent
                .replace(importRegex, "") 
                .replace(Regex("""export\s+default"""), "const TranscriptModule =")
                .replace(Regex("""export\s*\{.*?\}"""), "")

            // 7. EXPOSE GENERATOR
            val funcNameMatch = findMatch(transcriptJsContent, """(\w+)\s*=\s*\(C,a,c,d\)""")
            if (funcNameMatch != null) {
                transcriptJsContent += "\nwindow.PDFGenerator = $funcNameMatch;"
            } else {
                logger("CRITICAL: Generator function not found!")
            }

            val finalScript = dummyScript.toString() + "\n" + transcriptJsContent

            return@withContext PdfResources(stampBase64, finalScript)

        } catch (e: Exception) {
            logger("Fetch Failed: ${e.message}")
            e.printStackTrace()
            return@withContext PdfResources("", "")
        }
    }

    private fun fetchString(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code} for $url")
            return response.body?.string() ?: ""
        }
    }

    private fun findMatch(content: String, regex: String): String? {
        return Regex(regex).find(content)?.let { match ->
            if (match.groupValues.size > 1) match.groupValues[1] else match.groupValues[0]
        }
    }
}