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

class PdfGenerator(private val context: Context) {

    fun generateTranscriptPdf(jsonData: String, studentName: String, studentId: Long): File? {
        val pdfDocument = PdfDocument()
        val pageHeight = 842
        val pageWidth = 595
        var pageNumber = 1
        
        // Initialize First Page
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        
        val paint = Paint()
        val titlePaint = Paint()

        // --- Styles ---
        paint.color = Color.BLACK
        paint.textSize = 10f
        titlePaint.color = Color.BLACK
        titlePaint.textSize = 14f
        titlePaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        titlePaint.textAlign = Paint.Align.CENTER

        var y = 40f // Vertical Cursor

        // --- Helper Function to Handle Page Breaks ---
        fun checkPageBreak() {
            if (y > 780) { // Leave some bottom margin
                pdfDocument.finishPage(page)
                
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 40f // Reset cursor for new page
            }
        }

        // --- Header ---
        canvas.drawText("MINISTRY OF EDUCATION AND SCIENCE OF KYRGYZ REPUBLIC", 297f, y, titlePaint)
        y += 20f
        canvas.drawText("OSH STATE UNIVERSITY", 297f, y, titlePaint)
        y += 30f
        canvas.drawText("TRANSCRIPT", 297f, y, titlePaint)
        y += 40f

        // --- Student Info ---
        paint.textSize = 12f
        canvas.drawText("Name: $studentName", 40f, y, paint)
        y += 15f
        canvas.drawText("Student ID: $studentId", 40f, y, paint)
        y += 30f

        // --- Drawing Tables ---
        try {
            val data = JSONArray(jsonData)
            
            // Loop through Academic Years
            for (i in 0 until data.length()) {
                checkPageBreak()

                val eduYearObj = data.getJSONObject(i)
                val year = eduYearObj.optString("edu_year", "Unknown Year")
                
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("Academic Year: $year", 40f, y, paint)
                y += 20f
                
                val semesters = eduYearObj.optJSONArray("semesters") ?: continue
                
                // Loop through Semesters
                for (j in 0 until semesters.length()) {
                    checkPageBreak()

                    val semesterObj = semesters.getJSONObject(j)
                    val semesterName = semesterObj.optString("semester", "Semester")
                    
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    canvas.drawText("  $semesterName", 40f, y, paint)
                    y += 15f
                    
                    // Draw Table Header
                    checkPageBreak()
                    drawRow(canvas, y, listOf("Subject", "Credit", "Score", "Grade"), true)
                    y += 15f
                    
                    val subjects = semesterObj.optJSONArray("subjects") ?: continue
                    
                    // Loop through Subjects
                    for (k in 0 until subjects.length()) {
                        checkPageBreak()

                        val subj = subjects.getJSONObject(k)
                        val name = subj.optString("subject", "Unknown")
                        val credit = subj.optString("credit", "-")
                        
                        val markList = subj.optJSONObject("mark_list")
                        val total = markList?.optString("total", "0") ?: "0"
                        
                        val examRule = subj.optJSONObject("exam_rule")
                        val grade = examRule?.optString("alphabetic", "F") ?: "-"

                        drawRow(canvas, y, listOf(name, credit, total, grade), false)
                        y += 15f
                    }
                    y += 10f // Space between semesters
                }
                y += 10f // Space between years
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Finish the last page
        pdfDocument.finishPage(page)

        // --- Save File ---
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

    private fun drawRow(canvas: Canvas, y: Float, cells: List<String>, isHeader: Boolean) {
        val paint = Paint()
        paint.color = Color.BLACK
        paint.textSize = 10f
        if (isHeader) paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        // Column Widths
        val xPositions = listOf(40f, 350f, 420f, 480f) 
        
        for (i in cells.indices) {
            var text = cells[i]
            // Simple truncation to prevent overlap
            if (text.length > 45 && i == 0) text = text.substring(0, 42) + "..."
            canvas.drawText(text, xPositions[i], y, paint)
        }
    }
}