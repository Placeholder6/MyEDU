package com.example.myedu

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

data class PdfResources(
    val stampBase64: String,
    val tableGenCode: String,
    val docDefCode: String,
    val tableGenName: String,
    val docDefName: String
)

class JsResourceFetcher {

    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"

    suspend fun fetchResources(): PdfResources = withContext(Dispatchers.IO) {
        try {
            // 1. Find Main JS from Index HTML
            // New Robust Regex: Matches index.<hash>.<timestamp>.js
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""") 
                ?: throw Exception("Main JS not found in index.html")
            
            // 2. Find Transcript JS from Main JS
            // Look for dynamic import: import("./Transcript.HASH.TIMESTAMP.js")
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            // Matches "Transcript." followed by anything until ".js"
            val transcriptJsName = findMatch(mainJsContent, """(Transcript\.[^"]+\.js)""") 
                ?: throw Exception("Transcript JS name not found")
            
            // 3. Find Signed JS from Transcript JS (for Stamp)
            // Look for import: from"./Signed.HASH.TIMESTAMP.js"
            val transcriptJsContent = fetchString("$baseUrl/assets/$transcriptJsName")
            val signedJsName = findMatch(transcriptJsContent, """(Signed\.[^"]+\.js)""") 
                ?: throw Exception("Signed JS name not found")
            
            // 4. Get Stamp Image
            val signedJsContent = fetchString("$baseUrl/assets/$signedJsName")
            val stampBase64 = findMatch(signedJsContent, """"(data:image/[a-zA-Z]+;base64,[^"]+)"""") 
                ?: ""

            // 5. Extract Logic Functions from Transcript JS
            // We use flexible regexes that handle whitespace/formatting differences
            
            // Extract Table Generator (yt)
            // Looks for: const NAME=(C,a)=>{ ... return c},
            val ytMatch = findMatchGroup(transcriptJsContent, """const\s+([a-zA-Z0-9_${'$'}]+)\s*=\s*\(C,a\)\s*=>\s*\{.*?return c\},""")
                ?: throw Exception("Table Gen logic not found")
            val ytName = ytMatch.first
            val ytCode = "const $ytName=(C,a)=>{${ytMatch.second} return c};"

            // Extract Document Definer (Y)
            // Looks for: const NAME=(C,a,c,d)=>{ ... pageSize:"A4" ... }
            val yMatch = findMatchGroup(transcriptJsContent, """const\s+([a-zA-Z0-9_${'$'}]+)\s*=\s*\(C,a,c,d\)\s*=>\s*\{.*?pageSize:"A4".*?\}\};""")
                ?: throw Exception("Doc Def logic not found")
            val yName = yMatch.first
            val yCode = "const $yName=(C,a,c,d)=>{${yMatch.second}}};"

            return@withContext PdfResources(stampBase64, ytCode, yCode, ytName, yName)

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
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

    private fun findMatchGroup(content: String, regex: String): Pair<String, String>? {
        val matcher = Pattern.compile(regex).matcher(content)
        return if (matcher.find()) {
            val fullMatch = matcher.group(0)
            val varName = matcher.group(1)
            
            // Clean up the match to get just the function body content
            // Removes trailing comma or closing syntax to prevent syntax errors when re-injecting
            var code = fullMatch.trim().removeSuffix(",")
            val bodyStart = code.indexOf("=>")
            val body = code.substring(bodyStart + 2) 
            
            Pair(varName, body)
        } else null
    }
}