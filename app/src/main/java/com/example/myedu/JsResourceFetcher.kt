package com.example.myedu

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

data class PdfResources(
    val stampBase64: String,
    val logicCode: String
)

class JsResourceFetcher {

    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"

    suspend fun fetchResources(logger: (String) -> Unit): PdfResources = withContext(Dispatchers.IO) {
        try {
            // 1. Get Index HTML
            logger("Fetching index.html...")
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""")
                ?: throw Exception("Main JS not found")
            
            // 2. Get Main JS
            logger("Fetching Main JS: $mainJsName")
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            
            // 3. Find Transcript JS
            val transcriptJsName = findMatch(mainJsContent, """["']\./(Transcript\.[^"']+\.js)["']""")
                ?: throw Exception("Transcript JS not found")
            
            logger("Fetching Transcript JS: $transcriptJsName")
            var transcriptJsContent = fetchString("$baseUrl/assets/$transcriptJsName")

            // 4. GENERATE DUMMY VARIABLES FOR IMPORTS
            // Finds strings like: import { d as at, Y as lt } from ...
            // Extracts 'at', 'lt' and makes them dummy variables so the script doesn't crash.
            val varsToMock = mutableSetOf<String>()
            val importPattern = Regex("""import\s*\{([^}]*)\}\s*from\s*['"][^'"]*['"];?""")
            
            importPattern.findAll(transcriptJsContent).forEach { match ->
                val content = match.groupValues[1]
                content.split(",").forEach { item ->
                    val parts = item.trim().split(Regex("""\s+as\s+"""))
                    if (parts.size == 2) {
                        varsToMock.add(parts[1].trim())
                    } else if (parts[0].isNotBlank()) {
                        varsToMock.add(parts[0].trim())
                    }
                }
            }

            // Remove variables we mock manually to avoid re-declaration errors
            varsToMock.removeAll(setOf("J", "U", "K", "$", "mt"))

            val dummyScript = StringBuilder()
            // Universal Dummy: a Proxy that returns itself for any property access or function call
            dummyScript.append("const UniversalDummy = new Proxy(function(){}, { get: () => UniversalDummy, apply: () => UniversalDummy, construct: () => UniversalDummy });\n")
            
            if (varsToMock.isNotEmpty()) {
                dummyScript.append("var ")
                dummyScript.append(varsToMock.joinToString(",") { "$it = UniversalDummy" })
                dummyScript.append(";\n")
            }

            // 5. REMOVE IMPORTS & EXPORTS
            transcriptJsContent = transcriptJsContent
                .replace(importPattern, "")
                .replace(Regex("""export\s+default"""), "const TranscriptModule =")
                .replace(Regex("""export\s*\{.*?\}"""), "")

            val finalScript = dummyScript.toString() + "\n" + transcriptJsContent
            
            // 6. Get Stamp (Optional)
            var stampBase64 = ""
            val signedJsName = findMatch(mainJsContent, """["']\./(Signed\.[^"']+\.js)["']""")
            if (signedJsName != null) {
                val signedContent = fetchString("$baseUrl/assets/$signedJsName")
                stampBase64 = findMatch(signedContent, """"(data:image/[a-zA-Z]+;base64,[^"]+)"""") ?: ""
            }

            logger("Resources prepared (${finalScript.length} chars)")
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