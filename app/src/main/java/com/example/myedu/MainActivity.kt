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

    fun runDebug(tokenString: String) {
        if (tokenString.isBlank()) {
            log("Error: Token is empty.")
            return
        }
        val token = tokenString.removePrefix("Bearer ").trim()

        viewModelScope.launch {
            isRunning = true
            log("--- STARTING FINAL SEQUENCE ---")
            
            // 1. SETUP
            NetworkClient.cookieJar.setDebugCookies(token)
            NetworkClient.interceptor.authToken = token
            NetworkClient.interceptor.currentReferer = "https://myedu.oshsu.kg/" // Default
            log("Configured.")

            try {
                // --- STEP 0: IDS ---
                log(">>> STEP 0: Fetching IDs...")
                val studentId = getStudentIdFromToken(token)
                if (studentId == 0L) { log("!!! FAIL: Bad Token"); return@launch }
                
                val infoRaw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getStudentInfo(studentId).string()
                }
                val movementId = JSONObject(infoRaw).optJSONObject("lastStudentMovement")?.optLong("id") ?: 0L
                log("✔ Student: $studentId | Movement: $movementId")

                // --- STEP 0.8: TRANSCRIPT JSON ---
                log(">>> STEP 0.8: Fetching Transcript Data...")
                val transcriptJsonRaw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getTranscriptData(studentId, movementId).string()
                }
                if (transcriptJsonRaw.isEmpty()) { log("!!! FAIL: Empty Data"); return@launch }
                log("✔ Data Loaded (${transcriptJsonRaw.length} chars)")

                // --- STEP 1: GET KEY ---
                log(">>> STEP 1: Requesting Key...")
                val step1Raw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getTranscriptLink(DocIdRequest(studentId)).string()
                }
                val keyJson = JSONObject(step1Raw)
                val key = keyJson.optString("key")
                val linkId = keyJson.optLong("id")
                log("✔ Key: $key | Link ID: $linkId")

                // --- STEP 1.5: UPDATE REFERER ---
                // Crucial Step: The server expects us to be ON the document page
                val newReferer = "https://myedu.oshsu.kg/#/document/$key"
                NetworkClient.interceptor.currentReferer = newReferer
                log("✔ Referer Updated: $newReferer")

                // --- STEP 2: GENERATE PDF ---
                log(">>> STEP 2: Generating PDF (Multipart)...")
                
                val textType = "text/plain".toMediaTypeOrNull()
                val jsonType = "application/json".toMediaTypeOrNull()
                val pdfType = "application/pdf".toMediaTypeOrNull()

                val idBody = linkId.toString().toRequestBody(textType)
                val studentBody = studentId.toString().toRequestBody(textType)
                val movementBody = movementId.toString().toRequestBody(textType)
                val contentsBody = transcriptJsonRaw.toRequestBody(jsonType) // The Payload
                
                val emptyBytes = ByteArray(0)
                val fileReq = emptyBytes.toRequestBody(pdfType)
                val pdfPart = MultipartBody.Part.createFormData("pdf", "generated.pdf", fileReq)

                val step2Raw = withContext(Dispatchers.IO) {
                    NetworkClient.api.generateTranscript(
                        id = idBody, 
                        idStudent = studentBody, 
                        idMovement = movementBody, 
                        contents = contentsBody, 
                        pdf = pdfPart
                    ).string()
                }
                log("RAW 2: $step2Raw")
                
                log("Waiting 3 seconds...")
                delay(3000)

                // --- STEP 3: RESOLVE URL ---
                log(">>> STEP 3: Resolving Link...")
                val step3Raw = withContext(Dispatchers.IO) {
                    NetworkClient.api.resolveDocLink(DocKeyRequest(key)).string()
                }
                log("RAW 3: $step3Raw")
                
                val url = JSONObject(step3Raw).optString("url")
                if (url.isNotEmpty()) log("✅ FINAL URL: $url") else log("!!! FAIL: No URL")

            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string() ?: "No body"
                log("💥 HTTP ERROR ${e.code()}: $errorBody")
            } catch (e: Exception) {
                log("💥 EXCEPTION: ${e.message}")
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
        setContent { DebugScreen() }
    }
}

@Composable
fun DebugScreen(vm: DebugViewModel = viewModel()) {
    var tokenInput by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Text("MYEDU FINAL SOLUTION", color = Color.Cyan)
        
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
            Button(
                onClick = { vm.runDebug(tokenInput) },
                enabled = !vm.isRunning,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Green, contentColor = Color.Black)
            ) {
                Text(if (vm.isRunning) "WORKING..." else "RUN FINAL")
            }
            
            Spacer(Modifier.width(8.dp))
            
            Button(
                onClick = { clipboardManager.setText(AnnotatedString(vm.logs)) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("COPY LOGS")
            }
        }

        Spacer(Modifier.height(16.dp))
        Divider(color = Color.DarkGray)
        
        SelectionContainer(Modifier.fillMaxSize()) {
            Text(
                text = vm.logs,
                color = Color.Green,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.verticalScroll(scrollState)
            )
        }
        
        LaunchedEffect(vm.logs) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
}