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

            // 4. MOCK IMPORTS (Regex-based, crash proof)
            val varsToMock = mutableSetOf<String>()
            // Finds: import { d as at, ... } from ...
            val importRegex = Regex("""import\s*\{(.*?)\}\s*from\s*['"].*?['"];?""")
            
            importRegex.findAll(transcriptJsContent).forEach { match ->
                match.groupValues[1].split(",").forEach { item ->
                    val parts = item.trim().split(Regex("""\s+as\s+"""))
                    val varName = if (parts.size == 2) parts[1] else parts[0]
                    if (varName.isNotBlank()) varsToMock.add(varName.trim())
                }
            }
            varsToMock.removeAll(setOf("J", "U", "K", "$", "mt"))

            val dummyScript = StringBuilder()
            // Universal Proxy: prevents "ReferenceError" by swallowing all calls
            dummyScript.append("const UniversalDummy = new Proxy(function(){}, { get: () => UniversalDummy, apply: () => UniversalDummy, construct: () => UniversalDummy });\n")
            if (varsToMock.isNotEmpty()) {
                dummyScript.append("var ")
                dummyScript.append(varsToMock.joinToString(",") { "$it = UniversalDummy" })
                dummyScript.append(";\n")
            }

            // 5. CLEAN CODE (Remove imports/exports)
            transcriptJsContent = transcriptJsContent
                .replace(importRegex, "") 
                .replace(Regex("""export\s+default"""), "const TranscriptModule =")
                .replace(Regex("""export\s*\{.*?\}"""), "")

            // 6. EXPOSE GENERATOR FUNCTION
            // Matches pattern: Y=(C,a,c,d)
            // We find this name and attach it to window.PDFGenerator
            val funcNameMatch = findMatch(transcriptJsContent, """(\w+)\s*=\s*\(C,a,c,d\)""")
            
            if (funcNameMatch != null) {
                logger("FOUND GENERATOR: $funcNameMatch")
                transcriptJsContent += "\nwindow.PDFGenerator = $funcNameMatch;"
            } else {
                logger("CRITICAL: Generator signature not found! PDF will fail.")
            }

            val finalScript = dummyScript.toString() + "\n" + transcriptJsContent

            // 7. STAMP
            var stampBase64 = ""
            val signedJsName = findMatch(transcriptJsContent, """["']\./(Signed\.[^"']+\.js)["']""") 
                ?: findMatch(mainJsContent, """["']\./(Signed\.[^"']+\.js)["']""")

            if (signedJsName != null) {
                logger("Fetching Stamp: $signedJsName")
                val signedContent = fetchString("$baseUrl/assets/$signedJsName")
                stampBase64 = findMatch(signedContent, """['"](data:image/[^;]+;base64,[^'"]+)['"]""") ?: ""
                if(stampBase64.isNotEmpty()) logger("Stamp Size: ${stampBase64.length}")
            }

            return@withContext PdfResources(stampBase64, finalScript)

        } catch (e: Exception) {
            logger("Fetch Failed: ${e.message}")
            e.printStackTrace()
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