package com.example.myedu

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PdfResources(
    val stampBase64: String,
    val logicCode: String,
    val stampVarName: String // Added: The actual variable name used in the script
)

class JsResourceFetcher {

    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"

    suspend fun fetchResources(logger: (String) -> Unit): PdfResources = withContext(Dispatchers.IO) {
        try {
            // 1. Index
            logger("Fetching index.html...")
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""")
                ?: throw Exception("Main JS not found")
            
            // 2. Main JS
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            val transcriptJsName = findMatch(mainJsContent, """["']\./(Transcript\.[^"']+\.js)["']""")
                ?: throw Exception("Transcript JS not found")
            
            // 3. Transcript JS
            logger("Fetching $transcriptJsName...")
            var transcriptJsContent = fetchString("$baseUrl/assets/$transcriptJsName")

            // 4. DETECT STAMP VARIABLE NAME
            // We look for: import { S as SOMETHING } from "./Signed..."
            // This tells us what variable name the script expects for the stamp.
            var stampVarName = "mt" // Default fallback
            val signedImportMatch = Regex("""import\s*\{\s*S\s+as\s+(\w+)\s*\}\s*from\s*["']\./Signed\.""").find(transcriptJsContent)
            if (signedImportMatch != null) {
                stampVarName = signedImportMatch.groupValues[1]
                logger("Stamp Variable Detected: '$stampVarName'")
            } else {
                logger("WARNING: Could not detect stamp variable name. Defaulting to 'mt'.")
            }

            // 5. MOCK IMPORTS
            // We must NOT mock the stamp variable, or we'll overwrite it with a dummy.
            val varsToMock = mutableSetOf<String>()
            val importRegex = Regex("""import\s*\{(.*?)\}\s*from\s*['"].*?['"];?""")
            
            importRegex.findAll(transcriptJsContent).forEach { match ->
                match.groupValues[1].split(",").forEach { item ->
                    val parts = item.trim().split(Regex("""\s+as\s+"""))
                    val varName = if (parts.size == 2) parts[1] else parts[0]
                    if (varName.isNotBlank()) varsToMock.add(varName.trim())
                }
            }
            // CRITICAL: Remove manual mocks so we don't break them
            varsToMock.removeAll(setOf("J", "U", "K", "$", stampVarName))

            val dummyScript = StringBuilder()
            dummyScript.append("const UniversalDummy = new Proxy(function(){}, { get: () => UniversalDummy, apply: () => UniversalDummy, construct: () => UniversalDummy });\n")
            if (varsToMock.isNotEmpty()) {
                dummyScript.append("var ")
                dummyScript.append(varsToMock.joinToString(",") { "$it = UniversalDummy" })
                dummyScript.append(";\n")
            }

            // 6. CLEAN CODE
            transcriptJsContent = transcriptJsContent
                .replace(importRegex, "") 
                .replace(Regex("""export\s+default"""), "const TranscriptModule =")
                .replace(Regex("""export\s*\{.*?\}"""), "")

            // 7. EXPOSE GENERATOR
            val funcNameMatch = findMatch(transcriptJsContent, """(\w+)\s*=\s*\(C,a,c,d\)""")
            if (funcNameMatch != null) {
                transcriptJsContent += "\nwindow.PDFGenerator = $funcNameMatch;"
            } else {
                logger("CRITICAL: Generator function not found!")
            }

            val finalScript = dummyScript.toString() + "\n" + transcriptJsContent

            // 8. STAMP EXTRACTION
            var stampBase64 = ""
            val signedJsName = findMatch(transcriptJsContent, """from\s*["']\./(Signed\.[^"']+\.js)["']""") 
                ?: findMatch(mainJsContent, """["']\./(Signed\.[^"']+\.js)["']""")

            if (signedJsName != null) {
                logger("Fetching Stamp: $signedJsName")
                val signedContent = fetchString("$baseUrl/assets/$signedJsName")
                
                val stampMatch = Regex("""['"](data:image/[^;]+;base64,[^'"]+)['"]""").find(signedContent)
                stampBase64 = stampMatch?.groupValues?.get(1) ?: ""
                
                if(stampBase64.isNotEmpty()) logger("Stamp Data Found (${stampBase64.length} chars)")
            }

            return@withContext PdfResources(stampBase64, finalScript, stampVarName)

        } catch (e: Exception) {
            logger("Fetch Failed: ${e.message}")
            e.printStackTrace()
            // Return safe empty object
            return@withContext PdfResources("", "", "mt")
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