package com.example.myedu

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Data class needed by other files
data class PdfResources(
    val combinedScript: String
)

class JsResourceFetcher {

    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"

    suspend fun fetchResources(logger: (String) -> Unit, language: String, dictionary: Map<String, String> = emptyMap()): PdfResources = withContext(Dispatchers.IO) {
        try {
            logger("Fetching index.html...")
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""") 
                ?: throw Exception("Main JS missing")
            
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            val transcriptJsName = findMatch(mainJsContent, """["']\./(Transcript\.[^"']+\.js)["']""") 
                ?: throw Exception("Transcript JS missing")
            
            logger("Fetching $transcriptJsName...")
            var transcriptContent = fetchString("$baseUrl/assets/$transcriptJsName")

            val dependencies = StringBuilder()
            
            suspend fun linkModule(importRegex: Regex, exportRegex: Regex, finalVarName: String, fallbackValue: String) {
                var success = false
                try {
                    val fileNameMatch = importRegex.find(transcriptContent) ?: importRegex.find(mainJsContent)
                    if (fileNameMatch != null) {
                        val fileName = fileNameMatch.groupValues[1]
                        logger("Linking $finalVarName from $fileName")
                        var fileContent = fetchString("$baseUrl/assets/$fileName")
                        
                        if (language == "en") {
                             fileContent = applyDictionary(fileContent, dictionary)
                        }

                        val internalVarMatch = exportRegex.find(fileContent)
                        if (internalVarMatch != null) {
                            val internalVar = internalVarMatch.groupValues[1]
                            val cleanContent = fileContent.replace(Regex("""export\s*\{.*?\}"""), "")
                            dependencies.append("const $finalVarName = (() => {\n$cleanContent\nreturn $internalVar;\n})();\n")
                            success = true
                        }
                    }
                } catch (e: Exception) { logger("Link Error ($finalVarName): ${e.message}") }

                if (!success) dependencies.append("const $finalVarName = $fallbackValue;\n")
            }

            linkModule(Regex("""from\s*["']\./(PdfStyle\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+P\s*\}"""), "J", "{}")
            linkModule(Regex("""from\s*["']\./(PdfFooter4\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+P\s*\}"""), "U", "()=>({})")
            linkModule(Regex("""from\s*["']\./(KeysValue\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+k\s*\}"""), "K", "(n)=>n")
            linkModule(Regex("""from\s*["']\./(Signed\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+S\s*\}"""), "mt", "\"\"")
            linkModule(Regex("""from\s*["']\./(helpers\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*.*?(\w+)\s+as\s+e"""), "ct", "(...a)=>a.join(' ')")
            linkModule(Regex("""from\s*["']\./(ru\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+r\s*\}"""), "T", "{}")

            val varsToMock = mutableSetOf<String>()
            val importRegex = Regex("""import\s*\{(.*?)\}\s*from\s*['"].*?['"];?""")
            importRegex.findAll(transcriptContent).forEach { match ->
                match.groupValues[1].split(",").forEach { item ->
                    val parts = item.trim().split(Regex("""\s+as\s+"""))
                    val varName = if (parts.size == 2) parts[1] else parts[0]
                    if (varName.isNotBlank()) varsToMock.add(varName.trim())
                }
            }
            varsToMock.removeAll(setOf("J", "U", "K", "mt", "ct", "T", "$"))

            val dummyScript = StringBuilder()
            dummyScript.append("const UniversalDummy = new Proxy(function(){}, { get: () => UniversalDummy, apply: () => UniversalDummy, construct: () => UniversalDummy });\n")
            if (varsToMock.isNotEmpty()) {
                dummyScript.append("var ")
                dummyScript.append(varsToMock.joinToString(",") { "$it = UniversalDummy" })
                dummyScript.append(";\n")
            }

            var cleanTranscript = transcriptContent
                .replace(importRegex, "") 
                .replace(Regex("""export\s+default"""), "const TranscriptModule =")
                .replace(Regex("""export\s*\{.*?\}"""), "")

            if (language == "en") {
                logger("Applying Dictionary to Transcript Template...")
                cleanTranscript = applyDictionary(cleanTranscript, dictionary)
            }

            val funcNameMatch = findMatch(cleanTranscript, """(\w+)\s*=\s*\(C,a,c,d\)""")
            val exposeCode = if (funcNameMatch != null) "\nwindow.PDFGenerator = $funcNameMatch;" else ""

            val finalScript = dummyScript.toString() + dependencies.toString() + "\n" + cleanTranscript + exposeCode
            return@withContext PdfResources(finalScript)

        } catch (e: Exception) {
            logger("Fetch Error: ${e.message}")
            e.printStackTrace()
            return@withContext PdfResources("")
        }
    }

    private fun applyDictionary(script: String, dictionary: Map<String, String>): String {
        var s = script
        // Iterate dictionary and replace matches
        dictionary.forEach { (ru, en) -> 
            if (ru.length > 1) { 
                s = s.replace(ru, en) 
            }
        }
        return s
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