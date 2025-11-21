package com.example.myedu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfGenerator(private val context: Context) {

    // A4 Dimensions in PostScript points (approx 72 dpi)
    // Width: 595, Height: 842
    private val PAGE_WIDTH = 595
    private val PAGE_HEIGHT = 842
    private val MARGIN_LEFT = 40f
    private val MARGIN_RIGHT = 25f
    private val MARGIN_TOP = 25f
    private val MARGIN_BOTTOM = 40f
    private val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT

    // Paint Objects
    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 9f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        isAntiAlias = true
    }
    private val boldPaint = Paint().apply {
        color = Color.BLACK
        textSize = 9f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        isAntiAlias = true
    }
    private val headerPaint = Paint().apply {
        color = Color.BLACK
        textSize = 11f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val linePaint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 0.5f
        style = Paint.Style.STROKE
    }

    // Column Configuration (Matches JS 'widths')
    // [10, 40, "*", 30, 45, 25, 25, 20, 35]
    // We convert these proportional units to actual widths
    private val colWeights = floatArrayOf(10f, 40f, 180f, 30f, 45f, 35f, 35f, 30f, 45f)
    private val totalWeight = colWeights.sum()
    private val colWidths = colWeights.map { (it / totalWeight) * CONTENT_WIDTH }.toFloatArray()
    
    // Headers from JS
    private val headers = listOf(
        "№", "Б.Ч.", "Дисциплины", "Кредит", "Форма\nконтроля",
        "Баллы", "Цифр.\nэкв.", "Букв.\nсист.", "Трад.\nсист."
    )

    fun generateTranscriptPdf(jsonData: String, studentName: String, studentId: Long, studentInfoJson: String): File? {
        val pdfDocument = PdfDocument()
        var pageNumber = 1
        
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        var y = MARGIN_TOP

        // --- Helper to Start New Page ---
        fun startNewPage() {
            drawFooter(canvas, pageNumber)
            pdfDocument.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            y = MARGIN_TOP + 20 // Slight top margin on new pages
        }

        try {
            // 1. DRAW HEADER (Only on Page 1)
            val headerLines = listOf(
                "МИНИСТЕРСТВО НАУКИ, ВЫСШЕГО ОБРАЗОВАНИЯ И ИННОВАЦИЙ КЫРГЫЗСКОЙ РЕСПУБЛИКИ",
                "ОШСКИЙ ГОСУДАРСТВЕННЫЙ УНИВЕРСИТЕТ",
                "Международный медицинский факультет", // Hardcoded based on PDF, can be dynamic
                "ТРАНСКРИПТ"
            )
            
            headerLines.forEachIndexed { index, line ->
                if (index == 3) y += 5 // Extra space before "TRANSCRIPT"
                canvas.drawText(line, PAGE_WIDTH / 2f, y, headerPaint)
                y += 15
            }
            y += 20

            // 2. DRAW STUDENT INFO (2 Columns)
            // We parse the extra studentInfoJson if available, or use basic params
            drawStudentInfoBlock(canvas, y, studentName, studentId.toString(), studentInfoJson)
            y += 120 // Approx height of info block

            // 3. DRAW TABLES
            val data = JSONArray(jsonData)
            
            for (i in 0 until data.length()) {
                val eduYearObj = data.getJSONObject(i)
                val year = eduYearObj.optString("edu_year", "")
                
                // Year Header
                if (y + 20 > PAGE_HEIGHT - MARGIN_BOTTOM) startNewPage()
                canvas.drawText("Учебный год $year", PAGE_WIDTH / 2f, y, boldPaint.apply { textAlign = Paint.Align.CENTER })
                y += 15

                val semesters = eduYearObj.optJSONArray("semesters") ?: continue
                
                for (j in 0 until semesters.length()) {
                    val semesterObj = semesters.getJSONObject(j)
                    val semesterName = semesterObj.optString("semester", "")
                    
                    // Semester Header
                    if (y + 20 > PAGE_HEIGHT - MARGIN_BOTTOM) startNewPage()
                    
                    // Gray background for semester
                    val semPaint = Paint().apply { color = Color.LTGRAY }
                    canvas.drawRect(MARGIN_LEFT, y - 10, PAGE_WIDTH - MARGIN_RIGHT, y + 5, semPaint)
                    canvas.drawText(semesterName, PAGE_WIDTH / 2f, y, boldPaint)
                    y += 15

                    // Table Header Row
                    if (y + 30 > PAGE_HEIGHT - MARGIN_BOTTOM) startNewPage()
                    drawTableRow(canvas, y, headers, true)
                    y += 25 // Header height

                    val subjects = semesterObj.optJSONArray("subjects") ?: continue
                    
                    // Subjects Rows
                    for (k in 0 until subjects.length()) {
                        val subj = subjects.getJSONObject(k)
                        val rowData = listOf(
                            (k + 1).toString(),
                            subj.optString("code", ""),
                            subj.optString("subject", ""),
                            subj.optString("credit", ""),
                            subj.optString("exam", ""),
                            subj.optJSONObject("mark_list")?.optString("total", "") ?: "",
                            subj.optJSONObject("exam_rule")?.optString("digital", "") ?: "",
                            subj.optJSONObject("exam_rule")?.optString("alphabetic", "") ?: "",
                            subj.optJSONObject("exam_rule")?.optString("word_ru", "") ?: ""
                        )

                        // Calculate row height based on text wrapping (mostly for Subject name)
                        val rowHeight = calculateRowHeight(rowData[2]) 
                        
                        if (y + rowHeight > PAGE_HEIGHT - MARGIN_BOTTOM) startNewPage()
                        
                        drawTableRow(canvas, y, rowData, false, rowHeight)
                        y += rowHeight
                    }
                    
                    // Semester Footer (GPA)
                    if (y + 20 > PAGE_HEIGHT - MARGIN_BOTTOM) startNewPage()
                    val gpa = semesterObj.optString("gpa", "0")
                    val totalCredits = subjects.length() * 0 // Logic implies calculating sum, simplified here
                    
                    canvas.drawText("GPA: $gpa", PAGE_WIDTH - MARGIN_RIGHT - 50, y + 10, boldPaint)
                    y += 20
                }
            }

            // 4. FOOTER & QR CODE
            y += 30
            if (y + 100 > PAGE_HEIGHT - MARGIN_BOTTOM) startNewPage()
            
            canvas.drawText("ПРИМЕЧАНИЕ: 1 кредит составляет 30 академических часов.", MARGIN_LEFT, y, textPaint.apply { textAlign = Paint.Align.LEFT })
            y += 40
            
            // Signature placeholders
            canvas.drawText("Ректор _________________", PAGE_WIDTH - MARGIN_RIGHT, y, boldPaint.apply { textAlign = Paint.Align.RIGHT })
            y += 20
            canvas.drawText("Методист / Офис регистратор _________________", PAGE_WIDTH - MARGIN_RIGHT, y, boldPaint)

        } catch (e: Exception) {
            e.printStackTrace()
        }

        drawFooter(canvas, pageNumber)
        pdfDocument.finishPage(page)

        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "transcript_generated.pdf")
        try {
            pdfDocument.writeTo(FileOutputStream(file))
        } catch (e: IOException) {
            e.printStackTrace()
            pdfDocument.close()
            return null
        }

        pdfDocument.close()
        return file
    }

    private fun drawStudentInfoBlock(canvas: Canvas, startY: Float, name: String, id: String, json: String) {
        // Parse extra details if available
        var dob = ""
        var direction = ""
        var speciality = ""
        var eduForm = ""
        
        try {
            val obj = org.json.JSONObject(json)
            dob = obj.optString("birthday", "")
            val spec = obj.optJSONObject("speciality")
            speciality = spec?.optString("name_ru", "") ?: ""
            val dir = spec?.optJSONObject("direction")
            direction = dir?.optString("name_ru", "") ?: ""
            eduForm = obj.optJSONObject("lastStudentMovement")?.optJSONObject("edu_form")?.optString("name_ru", "") ?: ""
        } catch(e: Exception) {}

        var y = startY
        val labelX = MARGIN_LEFT
        val valueX = MARGIN_LEFT + 100f
        
        val info = listOf(
            "ФИО:" to name,
            "ID студента:" to id,
            "Дата рождения:" to dob,
            "Направление:" to direction,
            "Специальность:" to speciality,
            "Форма обучения:" to eduForm
        )

        info.forEach { (label, value) ->
            canvas.drawText(label, labelX, y, boldPaint.apply { textAlign = Paint.Align.LEFT })
            canvas.drawText(value, valueX, y, textPaint)
            y += 12
        }
    }

    private fun drawTableRow(canvas: Canvas, y: Float, row: List<String>, isHeader: Boolean, height: Float = 20f) {
        var currentX = MARGIN_LEFT
        val paint = if (isHeader) boldPaint else textPaint
        
        // Draw horizontal line top
        canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint)
        
        row.forEachIndexed { index, text ->
            val width = colWidths[index]
            
            // Draw vertical line left
            canvas.drawLine(currentX, y, currentX, y + height, linePaint)
            
            // Text handling
            val textX = if (index == 2) currentX + 2 else currentX + width / 2 // Left align subject, center others
            val align = if (index == 2) Paint.Align.LEFT else Paint.Align.CENTER
            paint.textAlign = align
            
            // Simple text wrapping for Subject column
            if (index == 2 && text.length > 30) {
                val split = text.chunked(30)
                var textY = y + 10
                split.forEach { line ->
                    canvas.drawText(line, textX, textY, paint)
                    textY += 10
                }
            } else {
                canvas.drawText(text, textX, y + 12, paint)
            }
            
            currentX += width
        }
        // Draw last vertical line
        canvas.drawLine(currentX, y, currentX, y + height, linePaint)
        // Draw bottom line
        canvas.drawLine(MARGIN_LEFT, y + height, PAGE_WIDTH - MARGIN_RIGHT, y + height, linePaint)
    }

    private fun calculateRowHeight(subject: String): Float {
        return if (subject.length > 30) 30f else 20f // Expand height for long text
    }

    private fun drawFooter(canvas: Canvas, pageNum: Int) {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        val dateStr = sdf.format(Date())
        
        val paint = Paint().apply { 
            color = Color.BLACK
            textSize = 8f
        }
        val y = PAGE_HEIGHT - 20f
        
        canvas.drawText("MYEDU $dateStr", MARGIN_LEFT, y, paint)
        canvas.drawText("Страница $pageNum", PAGE_WIDTH - MARGIN_RIGHT - 50, y, paint)
    }
}