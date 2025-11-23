package com.example.myedu

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PdfResources(
    val combinedScript: String
)

class JsResourceFetcher {

    private val client = OkHttpClient()
    private val baseUrl = "https://myedu.oshsu.kg"

    suspend fun fetchResources(logger: (String) -> Unit, language: String = "ru"): PdfResources = withContext(Dispatchers.IO) {
        try {
            // 1. Index
            logger("Fetching index.html...")
            val indexHtml = fetchString("$baseUrl/")
            val mainJsName = findMatch(indexHtml, """src="/assets/(index\.[^"]+\.js)"""") 
                ?: throw Exception("Main JS missing")
            
            // 2. Main JS
            val mainJsContent = fetchString("$baseUrl/assets/$mainJsName")
            val transcriptJsName = findMatch(mainJsContent, """["']\./(Transcript\.[^"']+\.js)["']""") 
                ?: throw Exception("Transcript JS missing")
            
            // 3. Transcript JS
            logger("Fetching $transcriptJsName...")
            var transcriptContent = fetchString("$baseUrl/assets/$transcriptJsName")

            // 4. LINK DEPENDENCIES
            val dependencies = StringBuilder()
            
            // Helper: Tries to fetch and link. If ANY step fails, it writes the fallback.
            suspend fun linkModule(importRegex: Regex, exportRegex: Regex, finalVarName: String, fallbackValue: String) {
                var success = false
                try {
                    // Find filename
                    val fileNameMatch = importRegex.find(transcriptContent) ?: importRegex.find(mainJsContent)
                    
                    if (fileNameMatch != null) {
                        val fileName = fileNameMatch.groupValues[1]
                        logger("Linking $finalVarName from $fileName")
                        var fileContent = fetchString("$baseUrl/assets/$fileName")
                        
                        // Translate if needed (only for Footer U)
                        if (language == "en" && finalVarName == "U") {
                            fileContent = fileContent.replace("Страница", "Page").replace("из", "of")
                        }

                        // Find export
                        val internalVarMatch = exportRegex.find(fileContent)
                        if (internalVarMatch != null) {
                            val internalVar = internalVarMatch.groupValues[1]
                            val cleanContent = fileContent.replace(Regex("""export\s*\{.*?\}"""), "")
                            
                            // Wrap in IIFE to isolate scope
                            dependencies.append("const $finalVarName = (() => {\n")
                            dependencies.append(cleanContent).append("\n")
                            dependencies.append("return $internalVar;\n")
                            dependencies.append("})();\n")
                            success = true
                        }
                    }
                } catch (e: Exception) {
                    logger("Link Error ($finalVarName): ${e.message}")
                }

                // SAFETY NET: If linking failed, use fallback.
                if (!success) {
                    logger("WARNING: Using fallback for $finalVarName")
                    dependencies.append("const $finalVarName = $fallbackValue;\n")
                }
            }

            // Link Modules (With explicit fallbacks)
            // Styles (J)
            linkModule(Regex("""from\s*["']\./(PdfStyle\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+P\s*\}"""), "J", "{}")
            // Footer (U)
            linkModule(Regex("""from\s*["']\./(PdfFooter4\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+P\s*\}"""), "U", "() => ({})")
            // Keys (K)
            linkModule(Regex("""from\s*["']\./(KeysValue\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+k\s*\}"""), "K", "(n) => n")
            // Stamp (mt)
            linkModule(Regex("""from\s*["']\./(Signed\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+S\s*\}"""), "mt", "\"\"")
            // Helpers (ct)
            linkModule(Regex("""from\s*["']\./(helpers\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*.*?(\w+)\s+as\s+e"""), "ct", "(...a) => a.join(' ')")
            // Locale (T) - Fixes "T is not defined"
            linkModule(Regex("""from\s*["']\./(ru\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+r\s*\}"""), "T", "{}")

            // 5. DUMMY OTHER IMPORTS
            // Mock everything else (Vue functions etc.) to prevent crashes
            val varsToMock = mutableSetOf<String>()
            val importRegex = Regex("""import\s*\{(.*?)\}\s*from\s*['"].*?['"];?""")
            importRegex.findAll(transcriptContent).forEach { match ->
                match.groupValues[1].split(",").forEach { item ->
                    val parts = item.trim().split(Regex("""\s+as\s+"""))
                    val varName = if (parts.size == 2) parts[1] else parts[0]
                    if (varName.isNotBlank()) varsToMock.add(varName.trim())
                }
            }
            // Remove manual links so we don't overwrite them
            varsToMock.removeAll(setOf("J", "U", "K", "mt", "ct", "T", "$"))

            val dummyScript = StringBuilder()
            dummyScript.append("const UniversalDummy = new Proxy(function(){}, { get: () => UniversalDummy, apply: () => UniversalDummy, construct: () => UniversalDummy });\n")
            if (varsToMock.isNotEmpty()) {
                dummyScript.append("var ")
                dummyScript.append(varsToMock.joinToString(",") { "$it = UniversalDummy" })
                dummyScript.append(";\n")
            }

            // 6. PREPARE TRANSCRIPT
            var cleanTranscript = transcriptContent
                .replace(importRegex, "") 
                .replace(Regex("""export\s+default"""), "const TranscriptModule =")
                .replace(Regex("""export\s*\{.*?\}"""), "")

            // 7. TRANSLATE (If English)
            if (language == "en") {
                cleanTranscript = translateToEnglish(cleanTranscript)
            }

            // 8. EXPOSE GENERATOR
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

    private fun translateToEnglish(script: String): String {
        var s = script
        val map = mapOf(
            "Учебный год" to "Academic Year", "Семестр" to "Semester", "Зарегистрировано кредитов -" to "Registered Credits -",
            "Дисциплины" to "Subjects", "Кредит" to "Credits", "Форма контроля" to "Control", "Баллы" to "Score",
            "Цифр. экв." to "Digit", "Букв.сист." to "Letter", "Трад. сист." to "Traditional",
            "МИНИСТЕРСТВО НАУКИ, ВЫСШЕГО ОБРАЗОВАНИЯ И ИННОВАЦИЙ КЫРГЫЗСКОЙ РЕСПУБЛИКИ" to "MINISTRY OF SCIENCE, HIGHER EDUCATION AND INNOVATION OF THE KYRGYZ REPUBLIC",
            "ОШСКИЙ ГОСУДАРСТВЕННЫЙ УНИВЕРСИТЕТ" to "OSH STATE UNIVERSITY", "ТРАНСКРИПТ" to "TRANSCRIPT",
            "ФИО:" to "Full Name:", "ID студента:" to "Student ID:", "Дата рождения:" to "Date of Birth:",
            "Направление:" to "Direction:", "Специальность:" to "Specialty:", "Форма обучения:" to "Form of Study:",
            "Общий GPA:" to "Total GPA:", "Всего зарегистрированых кредитов:" to "Total Registered Credits:",
            "ПРИМЕЧАНИЕ: 1 кредит составляет 30 академических часов." to "NOTE: 1 credit equals 30 academic hours.",
            "Достоверность данного документа можно проверить отсканировав QR-код" to "The authenticity of this document can be verified by scanning the QR code",
            "Ректор" to "Rector", "Методист / Офис регистратор" to "Registrar"
        )
        map.forEach { (ru, en) -> s = s.replace(ru, en) }
        s = s.replace("header:\"№\"", "header:\"#\"")
        s = s.replace("header:\"Б.Ч.\"", "header:\"Code\"")
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