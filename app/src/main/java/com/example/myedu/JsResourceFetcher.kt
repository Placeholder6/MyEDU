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

            // 4. EXTRACT & MOCK IMPORTS (Crucial Fix)
            // Minified imports often look like: import{d as at,Y as lt}from"./index.js";
            // We must find all variables (at, lt) and define them as dummies.
            val varsToMock = mutableSetOf<String>()
            
            // Regex handles spaces or no spaces (\s*)
            val importRegex = Regex("""import\s*\{(.*?)\}\s*from\s*['"].*?['"];?""")
            
            importRegex.findAll(transcriptJsContent).forEach { match ->
                val content = match.groupValues[1] // Content inside { ... }
                content.split(",").forEach { item ->
                    val parts = item.trim().split(Regex("""\s+as\s+"""))
                    if (parts.size == 2) {
                        varsToMock.add(parts[1].trim()) // Capture alias (e.g., 'at' from 'd as at')
                    } else {
                        varsToMock.add(parts[0].trim()) // Capture direct name
                    }
                }
            }

            // Remove manual mocks from the auto-generated list to avoid conflicts
            varsToMock.removeAll(setOf("J", "U", "K", "$", "mt"))
            
            // Create a Universal Dummy Object
            // This Proxy intercepts ALL calls (get, apply, construct) and returns itself.
            // This prevents crashes like "at(...) is not a function"
            val dummyScript = StringBuilder()
            dummyScript.append("const UniversalDummy = new Proxy(function(){}, { get: () => UniversalDummy, apply: () => UniversalDummy, construct: () => UniversalDummy });\n")
            
            if (varsToMock.isNotEmpty()) {
                dummyScript.append("var ")
                dummyScript.append(varsToMock.joinToString(",") { "$it = UniversalDummy" })
                dummyScript.append(";\n")
            }

            // 5. STRIP IMPORTS/EXPORTS
            transcriptJsContent = transcriptJsContent
                .replace(importRegex, "") // Remove import lines
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