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
            // STEP 1: Fetch HTML to get the Entry Point (index.js)
            val indexHtml = fetchString("$baseUrl/")
            // Looks for: src="/assets/index.HASH.TIMESTAMP.js"
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""")
                ?: throw Exception("Main JS not found in index.html")
            
            // STEP 2: Fetch Main Index JS
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")

            // STEP 3: Find Target Files inside Index JS
            // Regex looks for "Transcript.HASH.TIMESTAMP.js" inside the code
            val transcriptJsName = findMatch(mainJsContent, """(Transcript\.[^"]+\.js)""")
                ?: throw Exception("Transcript JS filename not found in index.js")
            
            // Try to find Signed.js in Index JS, fallback to Transcript JS later
            var signedJsName = findMatch(mainJsContent, """(Signed\.[^"]+\.js)""")
            
            // STEP 4: Fetch Transcript JS
            val transcriptJsContent = fetchString("$baseUrl/assets/$transcriptJsName")
            
            // If Signed.js wasn't in Index, check Transcript.js
            if (signedJsName == null) {
                signedJsName = findMatch(transcriptJsContent, """(Signed\.[^"]+\.js)""")
                    ?: throw Exception("Signed JS filename not found")
            }

            // STEP 5: Fetch Stamp from Signed.js
            val signedJsContent = fetchString("$baseUrl/assets/$signedJsName")
            val stampBase64 = findMatch(signedJsContent, """"(data:image/[a-zA-Z]+;base64,[^"]+)"""")
                ?: ""

            // STEP 6: Extract PDF Logic from Transcript.js
            // We extract the code block starting from "const yt=" (table generator)
            val startMarker = "const yt="
            val endMarker = "export{"
            val startIndex = transcriptJsContent.indexOf(startMarker)
            val endIndex = transcriptJsContent.lastIndexOf(endMarker)

            if (startIndex == -1 || endIndex == -1) throw Exception("PDF Logic block not found in Transcript.js")
            val logicCode = transcriptJsContent.substring(startIndex, endIndex)

            // STEP 7: Find the Main Function Name (dynamic)
            // Looks for: const Y = (C, a, c, d)
            val mainFuncMatch = findMatch(transcriptJsContent, """const\s+([a-zA-Z0-9_${'$'}]+)\s*=\s*\(C,a,c,d\)""")
                ?: "Y"

            return@withContext PdfResources(stampBase64, logicCode, mainFuncMatch)

        } catch (e: Exception) {
            e.printStackTrace()
            // Return safe empty object to prevent crash, allowing retry
            return@withContext PdfResources("", "", "Y")
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