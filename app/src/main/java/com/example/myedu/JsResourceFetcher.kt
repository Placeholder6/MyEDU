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
            
            suspend fun linkModule(importRegex: Regex, exportRegex: Regex, varName: String) {
                val fileNameMatch = importRegex.find(transcriptContent) ?: importRegex.find(mainJsContent)
                if (fileNameMatch != null) {
                    val fileName = fileNameMatch.groupValues[1]
                    logger("Linking $varName from $fileName")
                    var fileContent = fetchString("$baseUrl/assets/$fileName")
                    
                    // --- TRANSLATION FOR FOOTER ---
                    if (language == "en" && varName == "U") {
                        fileContent = fileContent
                            .replace("Страница", "Page")
                            .replace("из", "of")
                    }

                    val internalVarMatch = exportRegex.find(fileContent)
                    if (internalVarMatch != null) {
                        val internalVar = internalVarMatch.groupValues[1]
                        val cleanContent = fileContent.replace(Regex("""export\s*\{.*?\}"""), "")
                        dependencies.append("const $varName = (() => {\n$cleanContent\nreturn $internalVar;\n})();\n")
                    }
                }
            }

            // Link all modules
            linkModule(Regex("""from\s*["']\./(PdfStyle\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+P\s*\}"""), "J")
            linkModule(Regex("""from\s*["']\./(PdfFooter4\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+P\s*\}"""), "U")
            linkModule(Regex("""from\s*["']\./(KeysValue\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+k\s*\}"""), "K")
            linkModule(Regex("""from\s*["']\./(Signed\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*(\w+)\s+as\s+S\s*\}"""), "mt")
            linkModule(Regex("""from\s*["']\./(helpers\.[^"']+\.js)["']"""), Regex("""export\s*\{\s*.*?(\w+)\s+as\s+e"""), "ct")

            // 5. PREPARE TRANSCRIPT.JS
            var cleanTranscript = transcriptContent
                .replace(Regex("""import\s*\{.*?\}\s*from\s*['"].*?['"];?"""), "")
                .replace(Regex("""export\s+default"""), "const TranscriptModule =")
                .replace(Regex("""export\s*\{.*?\}"""), "")

            // --- TRANSLATION ENGINE ---
            if (language == "en") {
                logger("Applying English Translations...")
                cleanTranscript = translateToEnglish(cleanTranscript)
            }

            // 6. EXPOSE GENERATOR
            val funcNameMatch = findMatch(cleanTranscript, """(\w+)\s*=\s*\(C,a,c,d\)""")
            val exposeCode = if (funcNameMatch != null) "\nwindow.PDFGenerator = $funcNameMatch;" else ""

            // 7. COMBINE
            val finalScript = dependencies.toString() + "\n" + cleanTranscript + exposeCode
            return@withContext PdfResources(finalScript)

        } catch (e: Exception) {
            logger("Fetch Error: ${e.message}")
            e.printStackTrace()
            return@withContext PdfResources("")
        }
    }

    private fun translateToEnglish(script: String): String {
        var s = script
        // Map of Russian -> English phrases found in the original script
        val map = mapOf(
            "Учебный год" to "Academic Year",
            "Семестр" to "Semester",
            "Зарегистрировано кредитов -" to "Registered Credits -",
            "Дисциплины" to "Subjects",
            "Кредит" to "Credits",
            "Форма контроля" to "Control",
            "Баллы" to "Score",
            "Цифр. экв." to "Digit",
            "Букв.сист." to "Letter",
            "Трад. сист." to "Traditional",
            "МИНИСТЕРСТВО НАУКИ, ВЫСШЕГО ОБРАЗОВАНИЯ И ИННОВАЦИЙ КЫРГЫЗСКОЙ РЕСПУБЛИКИ" to "MINISTRY OF SCIENCE, HIGHER EDUCATION AND INNOVATION OF THE KYRGYZ REPUBLIC",
            "ОШСКИЙ ГОСУДАРСТВЕННЫЙ УНИВЕРСИТЕТ" to "OSH STATE UNIVERSITY",
            "ТРАНСКРИПТ" to "TRANSCRIPT",
            "ФИО:" to "Full Name:",
            "ID студента:" to "Student ID:",
            "Дата рождения:" to "Date of Birth:",
            "Направление:" to "Direction:",
            "Специальность:" to "Specialty:",
            "Форма обучения:" to "Form of Study:",
            "Общий GPA:" to "Total GPA:",
            "Всего зарегистрированых кредитов:" to "Total Registered Credits:",
            "ПРИМЕЧАНИЕ: 1 кредит составляет 30 академических часов." to "NOTE: 1 credit equals 30 academic hours.",
            "Достоверность данного документа можно проверить отсканировав QR-код" to "The authenticity of this document can be verified by scanning the QR code",
            "Ректор" to "Rector",
            "Методист / Офис регистратор" to "Registrar"
        )
        
        map.forEach { (ru, en) -> 
            s = s.replace(ru, en) 
        }
        
        // Replace table headers "№" and "Б.Ч." safely
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