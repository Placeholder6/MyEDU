package com.example.myedu

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

data class PdfResources(
    val stampBase64: String,
    val logicCode: String,
    val mainFuncName: String
)

class JsResourceFetcher {

    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"

    suspend fun fetchResources(): PdfResources = withContext(Dispatchers.IO) {
        try {
            // 1. Get Index HTML
            val indexHtml = fetchString("$baseUrl/")
            // Match: src="/assets/index.HASH.TIMESTAMP.js"
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""")
                ?: throw Exception("Main JS not found in index.html")
            
            // 2. Get Main JS
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")

            // 3. Find Transcript JS
            // Match: Transcript.HASH.js (handles both ' and " quotes)
            val transcriptJsName = findMatch(mainJsContent, """(Transcript\.[^"']+\.js)""")
                ?: throw Exception("Transcript JS not found in index.js")
            
            // 4. Find Signed JS (Try Main JS first, then Transcript JS)
            var signedJsName = findMatch(mainJsContent, """(Signed\.[^"']+\.js)""")
            
            // 5. Get Transcript JS Content
            val transcriptJsContent = fetchString("$baseUrl/assets/$transcriptJsName")
            
            if (signedJsName == null) {
                signedJsName = findMatch(transcriptJsContent, """(Signed\.[^"']+\.js)""")
                    ?: throw Exception("Signed JS not found")
            }

            // 6. Get Stamp
            val signedJsContent = fetchString("$baseUrl/assets/$signedJsName")
            val stampBase64 = findMatch(signedJsContent, """"(data:image/[a-zA-Z]+;base64,[^"]+)"""")
                ?: ""

            // 7. Extract Logic
            val startMarker = "const yt="
            val endMarker = "export{"
            val startIndex = transcriptJsContent.indexOf(startMarker)
            val endIndex = transcriptJsContent.lastIndexOf(endMarker)

            if (startIndex == -1 || endIndex == -1) throw Exception("Logic block not found in Transcript.js")
            val logicCode = transcriptJsContent.substring(startIndex, endIndex)

            // 8. Function Name
            val mainFuncMatch = findMatch(transcriptJsContent, """const\s+([a-zA-Z0-9_${'$'}]+)\s*=\s*\(C,a,c,d\)""")
                ?: "Y"

            return@withContext PdfResources(stampBase64, logicCode, mainFuncMatch)

        } catch (e: Exception) {
            e.printStackTrace()
            throw e // Throw to see the error in logs
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
        return if (matcher.find()) {
            if (matcher.groupCount() >= 1) matcher.group(1) else matcher.group(0)
        } else {
            null
        }
    }
}