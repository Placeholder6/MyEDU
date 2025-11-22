package com.example.myedu

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

// This Data Class must match what WebPdfGenerator uses
data class PdfResources(
    val stampBase64: String,
    val logicCode: String,    // Contains the full 'yt' and 'Y' functions
    val mainFuncName: String  // The dynamic name of the main function (e.g. 'Y')
)

class JsResourceFetcher {

    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"

    suspend fun fetchResources(): PdfResources = withContext(Dispatchers.IO) {
        try {
            // 1. Find Main JS (Handles timestamps like index.654edb4d.1762755934747.js)
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""") 
                ?: throw Exception("Main JS not found in index.html")
            
            // 2. Find Transcript JS
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            val transcriptJsName = findMatch(mainJsContent, """(Transcript\.[^"]+\.js)""") 
                ?: throw Exception("Transcript JS name not found")
            
            // 3. Get Transcript JS Content
            val transcriptJsContent = fetchString("$baseUrl/assets/$transcriptJsName")
            
            // 4. Find Signed JS (Stamp)
            val signedJsName = findMatch(transcriptJsContent, """(Signed\.[^"]+\.js)""") 
                ?: throw Exception("Signed JS name not found")
            
            // 5. Get Stamp Image
            val signedJsContent = fetchString("$baseUrl/assets/$signedJsName")
            val stampBase64 = findMatch(signedJsContent, """"(data:image/[a-zA-Z]+;base64,[^"]+)"""") 
                ?: ""

            // 6. Extract Logic Block
            // We grab everything from "const yt=" down to "export"
            // This copies the exact PDF generation logic from the server.
            val startMarker = "const yt="
            val endMarker = "export{"
            val startIndex = transcriptJsContent.indexOf(startMarker)
            val endIndex = transcriptJsContent.lastIndexOf(endMarker)

            if (startIndex == -1 || endIndex == -1) {
                // Fallback for safety if file format changes drastically
                return@withContext PdfResources(stampBase64, "", "Y") 
            }
            val logicCode = transcriptJsContent.substring(startIndex, endIndex)

            // 7. Find Main Function Name dynamically
            // Looks for: const Y = (C, a, c, d)
            val mainFuncMatch = findMatch(transcriptJsContent, """const\s+([a-zA-Z0-9_${'$'}]+)\s*=\s*\(C,a,c,d\)""") 
                ?: "Y"

            return@withContext PdfResources(stampBase64, logicCode, mainFuncMatch)

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext PdfResources("", "", "Y")
        }
    }

    private fun fetchString(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed $url: ${response.code}")
            return response.body?.string() ?: ""
        }
    }

    private fun findMatch(content: String, regex: String): String? {
        val matcher = Pattern.compile(regex).matcher(content)
        return if (matcher.find()) (if (matcher.groupCount() >= 1) matcher.group(1) else matcher.group(0)) else null
    }
}