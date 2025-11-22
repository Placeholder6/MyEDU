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
            logger("Downloading index.html...")
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""")
                ?: throw Exception("Main JS not found")
            
            logger("Found Main JS: $mainJsName")
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            
            // Find Transcript chunk
            val transcriptJsName = findMatch(mainJsContent, """["']\./(Transcript\.[^"']+\.js)["']""")
                ?: throw Exception("Transcript JS not found in index.js")
            
            logger("Found Transcript JS: $transcriptJsName")
            var transcriptJsContent = fetchString("$baseUrl/assets/$transcriptJsName")

            // --- FIX START: GENERATE DUMMY VARIABLES ---
            // 1. Extract all imported names: import { d as at, Y as lt } from ...
            val importRegex = Regex("""import\s*\{(.*?)\}\s*from""")
            val allImports = importRegex.findAll(transcriptJsContent)
            
            val varsToMock = mutableSetOf<String>()
            allImports.forEach { match ->
                val content = match.groupValues[1]
                val parts = content.split(",")
                parts.forEach { part ->
                    val trimmed = part.trim()
                    if (trimmed.contains(" as ")) {
                        varsToMock.add(trimmed.split(" as ")[1].trim())
                    } else if (trimmed.isNotEmpty()) {
                        varsToMock.add(trimmed)
                    }
                }
            }

            // Exclude vars we manually mock in WebPdfGenerator
            val manualMocks = setOf("J", "U", "K", "$", "mt")
            varsToMock.removeAll(manualMocks)

            // Generate JS code that defines these as dummies
            val dummyDecl = StringBuilder()
            dummyDecl.append("const UniversalDummy = new Proxy(function(){}, { get: () => UniversalDummy, apply: () => UniversalDummy, construct: () => UniversalDummy });\n")
            
            if (varsToMock.isNotEmpty()) {
                dummyDecl.append("var ")
                dummyDecl.append(varsToMock.joinToString(", ") { "$it = UniversalDummy" })
                dummyDecl.append(";\n")
            }
            // --- FIX END ---

            // 2. Sanitize Code: Remove imports/exports
            transcriptJsContent = transcriptJsContent
                .replace(Regex("""import\s+.*?from\s+['"].*?['"];?"""), "")
                .replace(Regex("""export\s+default"""), "const TranscriptModule =")
                .replace(Regex("""export\s+\{.*?\}"""), "")

            // Prepend the dummies
            val finalScript = dummyDecl.toString() + "\n" + transcriptJsContent
            
            logger("Prepared JS: ${finalScript.length} chars (Dummies: ${varsToMock.size})")

            // 3. Fetch Stamp (Optional)
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