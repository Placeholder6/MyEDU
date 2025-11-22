package com.example.myedu

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PdfResources(
    val stampBase64: String,
    val logicCode: String
)

class JsResourceFetcher {

    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"

    suspend fun fetchResources(logger: (String) -> Unit): PdfResources = withContext(Dispatchers.IO) {
        try {
            // 1. Get Index
            logger("Fetching index.html...")
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""")
                ?: throw Exception("Main JS not found")
            
            // 2. Get Main JS
            logger("Fetching Main JS...")
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            val transcriptJsName = findMatch(mainJsContent, """["']\./(Transcript\.[^"']+\.js)["']""")
                ?: throw Exception("Transcript JS not found")
            
            // 3. Get Transcript JS
            logger("Fetching Transcript JS: $transcriptJsName")
            var transcriptJsContent = fetchString("$baseUrl/assets/$transcriptJsName")

            // 4. MOCK IMPORTS (Universal Dummy)
            // We replace all imports with a dummy var to prevent "ReferenceError"
            val varsToMock = mutableSetOf<String>()
            val importRegex = Regex("""import\s*\{(.*?)\}\s*from\s*['"][^'"]*['"];?""")
            
            importRegex.findAll(transcriptJsContent).forEach { match ->
                match.groupValues[1].split(",").forEach { item ->
                    val parts = item.trim().split(Regex("""\s+as\s+"""))
                    val varName = if (parts.size == 2) parts[1] else parts[0]
                    if (varName.isNotBlank()) varsToMock.add(varName.trim())
                }
            }
            // Don't mock manually provided vars
            varsToMock.removeAll(setOf("J", "U", "K", "$", "mt"))

            val dummyScript = StringBuilder()
            // Universal Proxy: absorbs any call like G.someFunc() or at(...)
            dummyScript.append("const UniversalDummy = new Proxy(function(){}, { get: () => UniversalDummy, apply: () => UniversalDummy, construct: () => UniversalDummy });\n")
            
            if (varsToMock.isNotEmpty()) {
                dummyScript.append("var ")
                dummyScript.append(varsToMock.joinToString(",") { "$it = UniversalDummy" })
                dummyScript.append(";\n")
            }

            // 5. CLEAN CODE
            // Remove imports and exports
            transcriptJsContent = transcriptJsContent
                .replace(importRegex, "") 
                .replace(Regex("""export\s+default"""), "const TranscriptModule =")
                .replace(Regex("""export\s*\{.*?\}"""), "")

            // 6. EXPOSE GENERATOR
            // We look for: Y=(C,a,c,d)
            // The regex allows for optional spaces or preceding commas
            val funcNameMatch = findMatch(transcriptJsContent, """(?:[,;]|^)\s*(\w+)\s*=\s*\(C,a,c,d\)""")
            
            if (funcNameMatch != null) {
                logger("FOUND GENERATOR: $funcNameMatch")
                // We append this line to make the function globally accessible
                transcriptJsContent += "\nwindow.PDFGenerator = $funcNameMatch;"
            } else {
                logger("CRITICAL: Generator function signature (C,a,c,d) not found!")
            }

            val finalScript = dummyScript.toString() + "\n" + transcriptJsContent

            // 7. GET STAMP (Signed.js)
            var stampBase64 = ""
            // The import looks like: import { S as mt } from "./Signed.HASH.js"
            // We extract the filename for Signed.js from Transcript.js content
            val signedJsName = findMatch(transcriptJsContent, """from\s*["']\./(Signed\.[^"']+\.js)["']""") // Check Transcript content first
                ?: findMatch(mainJsContent, """["']\./(Signed\.[^"']+\.js)["']""") // Fallback to Main JS

            if (signedJsName != null) {
                logger("Fetching Stamp: $signedJsName")
                val signedContent = fetchString("$baseUrl/assets/$signedJsName")
                
                // Signed.js usually contains: const A = "data:image/..."
                // We just grab the big base64 string
                stampBase64 = findMatch(signedContent, """['"](data:image/[^;]+;base64,[^'"]+)['"]""") ?: ""
                
                if (stampBase64.isNotEmpty()) logger("Stamp extracted (${stampBase64.length} chars)")
                else logger("WARNING: Stamp variable not found in Signed.js")
            } else {
                logger("WARNING: Signed.js reference not found.")
            }

            return@withContext PdfResources(stampBase64, finalScript)

        } catch (e: Exception) {
            logger("Fetch Failed: ${e.message}")
            e.printStackTrace()
            // Return empty so we can at least see the error in the app logs
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
        return Regex(regex).find(content)?.let { match ->
            if (match.groupValues.size > 1) match.groupValues[1] else match.groupValues[0]
        }
    }
}