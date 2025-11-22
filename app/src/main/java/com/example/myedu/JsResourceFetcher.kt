package com.example.myedu

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

data class PdfResources(
    val stampBase64: String,
    val logicCode: String // Contains 'yt', 'Y', 'ht' functions extracted from server
)

class JsResourceFetcher {

    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"

    suspend fun fetchResources(): PdfResources = withContext(Dispatchers.IO) {
        try {
            // 1. Find Main JS from Index HTML
            // Regex matches: src="/assets/index.HASH.TIMESTAMP.js"
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""") 
                ?: throw Exception("Main JS not found in index.html")
            
            // 2. Find Transcript JS from Main JS
            // Regex matches: import("./Transcript.HASH.TIMESTAMP.js")
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            val transcriptJsName = findMatch(mainJsContent, """(Transcript\.[^"]+\.js)""") 
                ?: throw Exception("Transcript JS name not found")
            
            // 3. Get Transcript JS Content
            val transcriptJsContent = fetchString("$baseUrl/assets/$transcriptJsName")
            
            // 4. Find Signed JS Name (for Stamp) within Transcript JS
            // Regex matches: from"./Signed.HASH.TIMESTAMP.js"
            val signedJsName = findMatch(transcriptJsContent, """(Signed\.[^"]+\.js)""") 
                ?: throw Exception("Signed JS name not found")
            
            // 5. Get Stamp Image
            val signedJsContent = fetchString("$baseUrl/assets/$signedJsName")
            val stampBase64 = findMatch(signedJsContent, """"(data:image/[a-zA-Z]+;base64,[^"]+)"""") 
                ?: ""

            // 6. Extract the PDF Logic Block
            // We grab everything from "const yt=" up to "export{"
            // This effectively copies the website's PDF generation logic source code.
            val startMarker = "const yt="
            val endMarker = "export{"
            
            val startIndex = transcriptJsContent.indexOf(startMarker)
            val endIndex = transcriptJsContent.lastIndexOf(endMarker)

            if (startIndex == -1 || endIndex == -1) {
                throw Exception("Could not extract logic block from Transcript.js")
            }

            val logicCode = transcriptJsContent.substring(startIndex, endIndex)

            return@withContext PdfResources(stampBase64, logicCode)

        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to empty if offline/broken, to prevent crash
            return@withContext PdfResources("", "")
        }
    }

    private fun fetchString(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to fetch $url: ${response.code}")
            return response.body?.string() ?: ""
        }
    }

    private fun findMatch(content: String, regex: String): String? {
        val matcher = Pattern.compile(regex).matcher(content)
        return if (matcher.find()) (if (matcher.groupCount() >= 1) matcher.group(1) else matcher.group(0)) else null
    }
}