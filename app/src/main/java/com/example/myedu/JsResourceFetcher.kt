package com.example.myedu

import android.util.Log
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

    suspend fun fetchResources(): PdfResources = withContext(Dispatchers.IO) {
        try {
            // 1. Get Index HTML to find main JS
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""")
                ?: throw Exception("Main JS not found")
            
            // 2. Get Main JS content to find Transcript JS
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            
            // Look for Transcript import in the main bundle
            // Pattern matches: import("./Transcript.1ba68965.1762755934747.js")
            val transcriptJsName = findMatch(mainJsContent, """["']\./(Transcript\.[^"']+\.js)["']""")
                ?: throw Exception("Transcript JS not found in index.js")

            // 3. Fetch Transcript JS Content
            var transcriptJsContent = fetchString("$baseUrl/assets/$transcriptJsName")

            // 4. Sanitize Transcript JS for WebView usage
            // Remove imports and exports so it runs as a standard script
            transcriptJsContent = transcriptJsContent
                .replace(Regex("""import\s+.*?from\s+['"].*?['"];?"""), "")
                .replace(Regex("""export\s+default"""), "const TranscriptModule =")
                .replace(Regex("""export\s+\{.*?\}"""), "")

            // 5. Fetch Stamp (Optional - usually in Signed.js or encoded in Transcript)
            // We try to find Signed.js in the main bundle just in case
            var stampBase64 = ""
            val signedJsName = findMatch(mainJsContent, """["']\./(Signed\.[^"']+\.js)["']""")
            if (signedJsName != null) {
                 val signedJsContent = fetchString("$baseUrl/assets/$signedJsName")
                 stampBase64 = findMatch(signedJsContent, """"(data:image/[a-zA-Z]+;base64,[^"]+)"""") ?: ""
            }

            return@withContext PdfResources(stampBase64, transcriptJsContent)

        } catch (e: Exception) {
            e.printStackTrace()
            // Return dummy data so app doesn't crash, but PDF will likely fail
            PdfResources("", "// JS Fetch Failed: ${e.message}")
        }
    }

    private fun fetchString(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to fetch $url")
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