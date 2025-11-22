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
            // 1. Index HTML
            logger("Fetching index.html...")
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""")
                ?: throw Exception("Main JS not found in HTML")
            
            // 2. Main JS
            logger("Fetching Main JS...")
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            val transcriptJsName = findMatch(mainJsContent, """["']\./(Transcript\.[^"']+\.js)["']""")
                ?: throw Exception("Transcript JS not found in Main JS")
            
            // 3. Transcript JS
            logger("Fetching Transcript JS: $transcriptJsName")
            var transcriptJsContent = fetchString("$baseUrl/assets/$transcriptJsName")

            // 4. PREPARE DUMMY VARIABLES (Prevents crash on import)
            val varsToMock = mutableSetOf<String>()
            // Regex to find: import { d as at, Y as lt } from ...
            val importRegex = Regex("""import\s*\{(.*?)\}\s*from\s*['"].*?['"];?""")
            
            importRegex.findAll(transcriptJsContent).forEach { match ->
                match.groupValues[1].split(",").forEach { item ->
                    val parts = item.trim().split(Regex("""\s+as\s+"""))
                    val varName = if (parts.size == 2) parts[1] else parts[0]
                    if (varName.isNotBlank()) varsToMock.add(varName.trim())
                }
            }
            // Don't mock manually provided vars
            varsToMock.removeAll(setOf("J", "U", "K", "$", "mt"))

            val dummyScript = StringBuilder()
            // A Proxy that absorbs all calls without crashing
            dummyScript.append("const UniversalDummy = new Proxy(function(){}, { get: () => UniversalDummy, apply: () => UniversalDummy, construct: () => UniversalDummy });\n")
            if (varsToMock.isNotEmpty()) {
                dummyScript.append("var ")
                dummyScript.append(varsToMock.joinToString(",") { "$it = UniversalDummy" })
                dummyScript.append(";\n")
            }

            // 5. CLEAN CODE
            transcriptJsContent = transcriptJsContent
                .replace(importRegex, "") // Remove imports
                .replace(Regex("""export\s+default"""), "const TranscriptModule =")
                .replace(Regex("""export\s*\{.*?\}"""), "")

            // 6. FIND & EXPOSE GENERATOR FUNCTION
            // The signature in your file is: Y=(C,a,c,d)
            // We look for: (WORD)=(C,a,c,d)
            val funcNameMatch = findMatch(transcriptJsContent, """(\w+)\s*=\s*\(C,a,c,d\)""")
            
            if (funcNameMatch != null) {
                logger("FOUND GENERATOR: $funcNameMatch")
                // Explicitly attach to window so we can call it
                transcriptJsContent += "\nwindow.PDFGenerator = $funcNameMatch;"
            } else {
                logger("CRITICAL: Generator function signature (C,a,c,d) not found!")
            }

            val finalScript = dummyScript.toString() + "\n" + transcriptJsContent

            // 7. Stamp (Optional)
            var stampBase64 = ""
            val signedJsName = findMatch(mainJsContent, """["']\./(Signed\.[^"']+\.js)["']""")
            if (signedJsName != null) {
                val signedContent = fetchString("$baseUrl/assets/$signedJsName")
                stampBase64 = findMatch(signedContent, """"(data:image/[a-zA-Z]+;base64,[^"]+)"""") ?: ""
            }

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
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            return response.body?.string() ?: ""
        }
    }

    private fun findMatch(content: String, regex: String): String? {
        val matcher = Pattern.compile(regex).matcher(content)
        return if (matcher.find()) {
            if (matcher.groupCount() >= 1) matcher.group(1) else matcher.group(0)
        } else {
            null
        }
    }
}