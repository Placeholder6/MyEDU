package com.example.myedu

import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.platform.LocalClipboardManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebugViewModel : ViewModel() {
    var logs by mutableStateOf("Ready. Paste Token and click Run.\n")
    var isRunning by mutableStateOf(false)
    private val jsFetcher = JsResourceFetcher()

    fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        logs += "[$time] $msg\n"
    }

    private fun getStudentIdFromToken(token: String): Long {
        try {
            val parts = token.split(".")
            if (parts.size < 2) return 0L
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE))
            val json = JSONObject(payload)
            return json.optLong("sub", 0L)
        } catch (e: Exception) {
            return 0L
        }
    }

    fun runDebug(tokenString: String, webGenerator: WebPdfGenerator, filesDir: File) {
        if (tokenString.isBlank()) {
            log("Error: Token is empty.")
            return
        }
        val token = tokenString.removePrefix("Bearer ").trim()

        viewModelScope.launch {
            isRunning = true
            log("--- STARTING FIXED HOST SEQUENCE ---")
            
            NetworkClient.cookieJar.setDebugCookies(token)
            NetworkClient.interceptor.authToken = token
            NetworkClient.interceptor.currentReferer = "https://myedu.oshsu.kg/"
            log("Configured.")

            try {
                // 0. FETCH JS RESOURCES
                log(">>> STEP 0: Fetching JS from myedu.oshsu.kg...")
                val resources = jsFetcher.fetchResources()
                log("✔ JS & Stamp Fetched")

                // 1. INFO
                val studentId = getStudentIdFromToken(token)
                if (studentId == 0L) { log("!!! FAIL: Bad Token"); return@launch }
                
                val infoRaw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getStudentInfo(studentId).string()
                }
                val infoJson = JSONObject(infoRaw)
                val movementId = infoJson.optJSONObject("lastStudentMovement")?.optLong("id") ?: 0L
                log("✔ Student ID: $studentId")

                // 2. DATA
                val transcriptJsonRaw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getTranscriptData(studentId, movementId).string()
                }
                log("✔ Data Loaded")

                // 3. KEY
                val step1Raw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getTranscriptLink(DocIdRequest(studentId)).string()
                }
                val keyJson = JSONObject(step1Raw)
                val key = keyJson.optString("key")
                val linkId = keyJson.optLong("id")
                val qrUrl = keyJson.optString("url")
                log("✔ Key: $key")

                // 4. GENERATE PDF
                log(">>> STEP 4: Generating PDF...")
                val pdfBytes = webGenerator.generatePdf(infoRaw, transcriptJsonRaw, linkId, qrUrl, resources)
                log("✔ PDF Generated: ${pdfBytes.size} bytes")

                val pdfFile = File(filesDir, "transcript.pdf")
                withContext(Dispatchers.IO) {
                    FileOutputStream(pdfFile).use { it.write(pdfBytes) }
                }

                // 5. UPLOAD
                log(">>> STEP 5: Uploading...")
                NetworkClient.interceptor.currentReferer = "https://myedu.oshsu.kg/#/document/$key"
                
                val plainType = "text/plain".toMediaTypeOrNull()
                val pdfType = "application/pdf".toMediaTypeOrNull()

                val idBody = linkId.toString().toRequestBody(plainType)
                val studentBody = studentId.toString().toRequestBody(plainType)
                val fileReq = pdfFile.asRequestBody(pdfType)
                val pdfPart = MultipartBody.Part.createFormData("pdf", "transcript.pdf", fileReq)

                val step2Raw = withContext(Dispatchers.IO) {
                    NetworkClient.api.uploadPdf(idBody, studentBody, pdfPart).string()
                }
                log("✔ UPLOAD: $step2Raw")

                delay(2000)

                // 6. RESOLVE
                val step3Raw = withContext(Dispatchers.IO) {
                    NetworkClient.api.resolveDocLink(DocKeyRequest(key)).string()
                }
                val url = JSONObject(step3Raw).optString("url")
                if (url.isNotEmpty()) log("✅ URL: $url") else log("!!! FAIL: No URL")

            } catch (e: Exception) {
                log("💥 ERROR: ${e.message}")
                e.printStackTrace()
            } finally {
                isRunning = false
                log("--- END ---")
            }
        }
    }
}