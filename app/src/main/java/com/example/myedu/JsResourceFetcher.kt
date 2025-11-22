package com.example.myedu

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

data class PdfResources(
    val stampBase64: String,
    val tableGenCode: String, // The function that builds rows (yt)
    val docDefCode: String,   // The function that builds the PDF object (Y)
    val tableGenName: String, // Variable name for (1)
    val docDefName: String    // Variable name for (2)
)

class JsResourceFetcher {

    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"

    suspend fun fetchResources(): PdfResources = withContext(Dispatchers.IO) {
        try {
            // 1. Find Main JS
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[a-z0-9]+\.js)"""") 
                ?: throw Exception("Main JS not found")
            
            // 2. Find Transcript JS
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            val transcriptHash = findMatch(mainJsContent, """Transcript\.([a-z0-9]+)\.js""") 
                ?: throw Exception("Transcript Hash not found")
            val transcriptJsName = "Transcript.$transcriptHash.js"
            
            // 3. Find Signed JS (for Stamp)
            val transcriptJsContent = fetchString("$baseUrl/assets/$transcriptJsName")
            val signedHash = findMatch(transcriptJsContent, """Signed\.([a-z0-9]+)\.js""") 
                ?: throw Exception("Signed Hash not found")
            
            // 4. Get Stamp Image
            val signedJsContent = fetchString("$baseUrl/assets/Signed.$signedHash.js")
            val stampBase64 = findMatch(signedJsContent, """"(data:image/[a-zA-Z]+;base64,[^"]+)"""") 
                ?: ""

            // 5. Extract Logic Functions from Transcript.js
            // Pattern: const yt = (C, a) => { ... return c; }
            // We look for the signature: (C, a) => { ... }
            // and (C, a, c, d) => { ... return { pageSize ... } }
            
            // Extract Table Generator (yt)
            // Looks for: const XX=(C,a)=>{ ... return c}
            // Note: We grab the whole line or block.
            val ytMatch = findMatchGroup(transcriptJsContent, """const ([a-zA-Z0-9]+)=\(C,a\)=>\{.*?return c\},""")
                ?: throw Exception("Table Generator logic not found")
            val ytName = ytMatch.first
            val ytCode = "const $ytName=(C,a)=>{${ytMatch.second} return c};"

            // Extract Document Definer (Y)
            // Looks for: const XX=(C,a,c,d)=>{ ... pageSize:"A4" ... }
            val yMatch = findMatchGroup(transcriptJsContent, """const ([a-zA-Z0-9]+)=\(C,a,c,d\)=>\{.*?pageSize:"A4".*?\}\};""")
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
            if (!response.isSuccessful) throw Exception("Failed $url: ${response.code}")
            return response.body?.string() ?: ""
        }
    }

    private fun findMatch(content: String, regex: String): String? {
        val matcher = Pattern.compile(regex).matcher(content)
        return if (matcher.find()) (if (matcher.groupCount() >= 1) matcher.group(1) else matcher.group(0)) else null
    }

    // Returns Pair(VariableName, FunctionBody_Inside_Braces_Roughly)
    private fun findMatchGroup(content: String, regex: String): Pair<String, String>? {
        val matcher = Pattern.compile(regex).matcher(content)
        return if (matcher.find()) {
            // Group 1 is Var Name. The rest is the body match.
            // To be safe, we extract the whole match and strip the "const name=" part in Kotlin
            val fullMatch = matcher.group(0)
            val varName = matcher.group(1)
            
            // Hacky cleanup to reconstruct valid JS:
            // The regex matches "const yt=(C,a)=>{...},"
            // We want to return just the body part to reconstruct it safely? 
            // Actually, let's just return the match minus the trailing comma
            var code = fullMatch.trim().removeSuffix(",")
            // If it ends with a comma inside the block (rare), this might break, 
            // but minified code usually puts commas between const declarations.
            
            // Better approach: Regex grabbed "const name=(args)=>{body}"
            // We just clean the code string we captured.
            val bodyStart = code.indexOf("=>")
            val body = code.substring(bodyStart + 2) // { ... }
            
            Pair(varName, body)
        } else null
    }
}