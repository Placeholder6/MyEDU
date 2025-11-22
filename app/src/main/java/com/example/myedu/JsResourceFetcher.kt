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
            // Matches: src="/assets/index.HASH.TIMESTAMP.js"
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""")
                ?: throw Exception("Entry point (index.js) not found in HTML")
            
            // STEP 2: Fetch Main Index JS
            // This file contains the map/imports for all other modules
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")

            // STEP 3: Find Target Files inside Index JS
            // The filenames are strings inside the code, e.g., "Transcript.1ba6.172.js"
            val transcriptJsName = findMatch(mainJsContent, """(Transcript\.[^"]+\.js)""")
                ?: throw Exception("Transcript JS file definition not found in index.js")
            
            // The Signed.js file might be referenced in Transcript.js or Index.js
            // We check Index JS first (as you suggested), then fallback to fetching Transcript to find it.
            var signedJsName = findMatch(mainJsContent, """(Signed\.[^"]+\.js)""")
            
            // STEP 4: Fetch the Logic (Transcript.js)
            val transcriptJsContent = fetchString("$baseUrl/assets/$transcriptJsName")
            
            // If Signed.js wasn't in Index, it must be in Transcript.js
            if (signedJsName == null) {
                signedJsName = findMatch(transcriptJsContent, """(Signed\.[^"]+\.js)""")
                    ?: throw Exception("Signed JS file definition not found")
            }

            // STEP 5: Fetch the Stamp (Signed.js)
            val signedJsContent = fetchString("$baseUrl/assets/$signedJsName")
            val stampBase64 = findMatch(signedJsContent, """"(data:image/[a-zA-Z]+;base64,[^"]+)"""")
                ?: ""

            // STEP 6: Extract Logic Block
            // We grab the table generation logic ("const yt=") to the end of the file
            val startMarker = "const yt="
            val endMarker = "export{"
            val startIndex = transcriptJsContent.indexOf(startMarker)
            val endIndex = transcriptJsContent.lastIndexOf(endMarker)

            if (startIndex == -1 || endIndex == -1) throw Exception("PDF Logic block not found")
            val logicCode = transcriptJsContent.substring(startIndex, endIndex)

            // STEP 7: Dynamic Function Name
            // Finds: const Y = (C, a, c, d)
            val mainFuncMatch = findMatch(transcriptJsContent, """const\s+([a-zA-Z0-9_${'$'}]+)\s*=\s*\(C,a,c,d\)""")
                ?: "Y"

            return@withContext PdfResources(stampBase64, logicCode, mainFuncMatch)

        } catch (e: Exception) {
            e.printStackTrace()
            // Return safe empty object to prevent crash
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
            // Prefer the captured group (inside parenthesis), else the whole match
            if (matcher.groupCount() >= 1) matcher.group(1) else matcher.group(0)
        } else {
            null
        }
    }
}