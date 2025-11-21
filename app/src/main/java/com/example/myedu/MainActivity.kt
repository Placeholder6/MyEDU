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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebugViewModel : ViewModel() {
    var logs by mutableStateOf("Ready. Paste Token and click Run.\n")
    var isRunning by mutableStateOf(false)

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

    fun runDebug(tokenString: String, generator: PdfGenerator) {
        if (tokenString.isBlank()) {
            log("Error: Token is empty.")
            return
        }
        val token = tokenString.removePrefix("Bearer ").trim()

        viewModelScope.launch {
            isRunning = true
            log("--- STARTING CLIENT-SIDE PDF GEN ---")
            
            NetworkClient.cookieJar.setDebugCookies(token)
            NetworkClient.interceptor.authToken = token
            NetworkClient.interceptor.currentReferer = "https://myedu.oshsu.kg/"
            log("Configured.")

            try {
                // 1. INFO
                val studentId = getStudentIdFromToken(token)
                if (studentId == 0L) { log("!!! FAIL: Bad Token"); return@launch }
                
                val infoRaw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getStudentInfo(studentId).string()
                }
                val infoJson = JSONObject(infoRaw)
                val movementId = infoJson.optJSONObject("lastStudentMovement")?.optLong("id") ?: 0L
                val studentName = infoJson.optString("name", "Student")
                log("✔ IDs: $studentId / $movementId")

                // 2. DATA
                val transcriptJsonRaw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getTranscriptData(studentId, movementId).string()
                }
                log("✔ Data: ${transcriptJsonRaw.length} chars")

                // 3. KEY
                val step1Raw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getTranscriptLink(DocIdRequest(studentId)).string()
                }
                val keyJson = JSONObject(step1Raw)
                val key = keyJson.optString("key")
                val linkId = keyJson.optLong("id")
                log("✔ Key: $key")

                // 4. GENERATE PDF (LOCALLY)
                log(">>> GENERATING PDF...")
                val pdfFile = withContext(Dispatchers.Default) {
                    generator.generateTranscriptPdf(transcriptJsonRaw, studentName, studentId, infoRaw)
                }
                
                if (pdfFile == null || !pdfFile.exists()) {
                    log("!!! FAIL: PDF Generation Failed")
                    return@launch
                }
                log("✔ PDF Ready: ${pdfFile.length()} bytes")

                // 5. UPLOAD
                log(">>> UPLOADING...")
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
                log("✔ RESPONSE: $step2Raw")

                delay(2000)

                // 6. RESOLVE
                val step3Raw = withContext(Dispatchers.IO) {
                    NetworkClient.api.resolveDocLink(DocKeyRequest(key)).string()
                }
                val url = JSONObject(step3Raw).optString("url")
                log("✅ URL: $url")

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val generator = PdfGenerator(applicationContext)
        setContent { DebugScreen(generator) }
    }
}

@Composable
fun DebugScreen(generator: PdfGenerator, vm: DebugViewModel = viewModel()) {
    var tokenInput by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Text("MYEDU PDF GENERATOR", color = Color.Cyan)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = tokenInput, 
            onValueChange = { tokenInput = it }, 
            label = { Text("Paste Token") }, 
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                focusedBorderColor = Color.Cyan,
                unfocusedBorderColor = Color.Gray
            )
        )
        Spacer(Modifier.height(16.dp))
        Row {
            Button(onClick = { vm.runDebug(tokenInput, generator) }, enabled = !vm.isRunning, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Green, contentColor = Color.Black)) { Text(if (vm.isRunning) "..." else "GENERATE") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { clipboardManager.setText(AnnotatedString(vm.logs)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) { Text("COPY") }
        }
        Spacer(Modifier.height(16.dp))
        Divider(color = Color.DarkGray)
        SelectionContainer(Modifier.fillMaxSize()) { Text(text = vm.logs, color = Color.Green, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.verticalScroll(scrollState)) }
        LaunchedEffect(vm.logs) { scrollState.animateScrollTo(scrollState.maxValue) }
    }
}