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
            logger("Downloading index.html...")
            val indexHtml = fetchString("$baseUrl/")
            // Find main entry JS: src="/assets/index.HASH.js"
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""")
                ?: throw Exception("Main JS not found in HTML")
            
            // 2. Get Main JS
            logger("Downloading Main JS: $mainJsName")
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")

            // 3. Find Transcript JS inside Main JS
            // It looks like: import("./Transcript.HASH.js")
            val transcriptJsName = findMatch(mainJsContent, """["']\./(Transcript\.[^"']+\.js)["']""")
                ?: throw Exception("Transcript module not found in Index JS")
            
            logger("Found Transcript module: $transcriptJsName")

            // 4. Download Transcript JS
            var transcriptJsContent = fetchString("$baseUrl/assets/$transcriptJsName")

            // 5. Sanitize Code (Remove imports/exports)
            // This allows the code to run in a simple WebView script tag
            val originalLen = transcriptJsContent.length
            transcriptJsContent = transcriptJsContent
                .replace(Regex("""import\s+.*?from\s+['"].*?['"];?"""), "") // Remove imports
                .replace(Regex("""export\s+default"""), "const TranscriptModule =") // Convert export default
                .replace(Regex("""export\s+\{.*?\}"""), "") // Remove named exports
            
            logger("Sanitized JS (${originalLen} -> ${transcriptJsContent.length} chars)")

            // 6. Try to find Stamp (Signed.js)
            // This is optional but nice to have. It might be in Main JS.
            var stampBase64 = ""
            val signedJsName = findMatch(mainJsContent, """["']\./(Signed\.[^"']+\.js)["']""")
            if (signedJsName != null) {
                logger("Downloading Stamp from: $signedJsName")
                val signedContent = fetchString("$baseUrl/assets/$signedJsName")
                stampBase64 = findMatch(signedContent, """"(data:image/[a-zA-Z]+;base64,[^"]+)"""") ?: ""
            }

            return@withContext PdfResources(stampBase64, transcriptJsContent)

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