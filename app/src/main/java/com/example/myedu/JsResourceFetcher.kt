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
            // 1. Get Index HTML to find the main entry point JS
            val indexHtml = fetchString("$baseUrl/")
            
            // Regex to find: src="/assets/index.HASH.TIMESTAMP.js"
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""")
                ?: throw Exception("Main JS file not found in index.html")
            
            // 2. Get Main JS content
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")

            // 3. Find Transcript JS filename inside Main JS
            // The app splits code, so Main JS contains references like "./Transcript.HASH.js"
            val transcriptJsName = findMatch(mainJsContent, """(Transcript\.[^"']+\.js)""")
                ?: throw Exception("Transcript JS module not found in index.js")
            
            // 4. Find Signed JS filename (contains the stamp image)
            // It might be in Main JS or Transcript JS
            var signedJsName = findMatch(mainJsContent, """(Signed\.[^"']+\.js)""")
            
            // 5. Get Transcript JS Content
            val transcriptJsContent = fetchString("$baseUrl/assets/$transcriptJsName")
            
            // If Signed JS wasn't in Main, check Transcript JS
            if (signedJsName == null) {
                signedJsName = findMatch(transcriptJsContent, """(Signed\.[^"']+\.js)""")
                    ?: throw Exception("Signed JS module not found")
            }

            // 6. Get Stamp Image from Signed JS
            // Looks for "data:image/..." string
            val signedJsContent = fetchString("$baseUrl/assets/$signedJsName")
            val stampBase64 = findMatch(signedJsContent, """"(data:image/[a-zA-Z]+;base64,[^"]+)"""")
                ?: ""

            // 7. Extract the PDF generation logic from Transcript JS
            // We look for the variable where the logic starts (often 'const yt=' or similar minified name)
            // and where the export definition starts.
            // NOTE: These markers ("const yt=" and "export{") are heuristic based on common Vite/Rollup builds.
            // You may need to adjust "const yt=" if the minification changes variable names.
            val startMarker = "const yt=" 
            val endMarker = "export{"
            val startIndex = transcriptJsContent.indexOf(startMarker)
            val endIndex = transcriptJsContent.lastIndexOf(endMarker)

            val logicCode = if (startIndex != -1 && endIndex != -1) {
                 transcriptJsContent.substring(startIndex, endIndex)
            } else {
                // Fallback: pass whole file if specific block not found (might be messy but functional)
                transcriptJsContent
            }

            // 8. Find the main function name used to export the logic
            // Look for pattern: const FunctionName = (arg1, arg2, arg3, arg4)
            // In your logs/code it seemed to be "Y" or similar short variable.
            val mainFuncMatch = findMatch(transcriptJsContent, """const\s+([a-zA-Z0-9_${'$'}]+)\s*=\s*\(C,a,c,d\)""")
                ?: "Y" // Default fallback

            return@withContext PdfResources(stampBase64, logicCode, mainFuncMatch)

        } catch (e: Exception) {
            e.printStackTrace()
            // Return empty/default resources so app doesn't crash, just fails to generate PDF
            PdfResources("", "", "") 
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
            // Return the capture group if available, otherwise the whole match
            if (matcher.groupCount() >= 1) matcher.group(1) else matcher.group(0)
        } else {
            null
        }
    }
}