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

    // Helper to extract Student ID from JWT 'sub' claim
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
            log("--- STARTING AUTO-FETCH SEQUENCE ---")
            
            // 1. Configure Session
            NetworkClient.cookieJar.setDebugCookies(token)
            NetworkClient.interceptor.authToken = token
            log("Cookies & Headers Configured.")

            try {
                // --- STEP 0: AUTO-FETCH IDS ---
                log(">>> STEP 0: Extracting Student ID from Token...")
                val studentId = getStudentIdFromToken(token)
                if (studentId == 0L) {
                    log("!!! FAIL: Could not decode Student ID from token.")
                    return@launch
                }
                log("✔ Student ID found: $studentId")

                log(">>> STEP 0.5: Fetching Movement ID (searchstudentinfo)...")
                val infoRaw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getStudentInfo(studentId).string()
                }
                val infoJson = JSONObject(infoRaw)
                val movementObj = infoJson.optJSONObject("lastStudentMovement")
                val movementId = movementObj?.optLong("id") ?: 0L

                if (movementId == 0L) {
                    log("!!! FAIL: Could not find 'lastStudentMovement.id' in response.")
                    return@launch
                }
                log("✔ Movement ID found: $movementId")

                // --- STEP 1: GET KEY & LINK ID ---
                log(">>> STEP 1: Requesting Key (form13link)...")
                val step1Raw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getTranscriptLink(DocIdRequest(studentId)).string()
                }
                log("RAW 1: $step1Raw")

                val keyJson = try { JSONObject(step1Raw) } catch(e:Exception) { 
                    log("!!! FAIL: Step 1 is not JSON"); return@launch 
                }
                
                val key = keyJson.optString("key")
                val linkId = keyJson.optLong("id") 
                
                if (key.isEmpty() || linkId == 0L) {
                    log("!!! FAILURE: Missing 'key' or 'id' in Step 1.")
                    return@launch
                }
                log("✔ Key: $key | Link ID: $linkId")

                // --- STEP 2: TRIGGER GENERATION ---
                log(">>> STEP 2: Triggering Generation (form13)...")
                val request2 = TranscriptRequest(
                    id = linkId,
                    id_student = studentId, 
                    id_movement = movementId,
                    pdf = true
                )
                
                val step2Raw = withContext(Dispatchers.IO) {
                    NetworkClient.api.generateTranscript(request2).string()
                }
                log("RAW 2: $step2Raw")

                log("Waiting 3 seconds for PDF generation...")
                delay(3000)

                // --- STEP 3: RESOLVE LINK ---
                log(">>> STEP 3: Resolving Link (showlink)...")
                val step3Raw = withContext(Dispatchers.IO) {
                    NetworkClient.api.resolveDocLink(DocKeyRequest(key)).string()
                }
                log("RAW 3: $step3Raw")
                
                try {
                    val urlJson = JSONObject(step3Raw)
                    val url = urlJson.optString("url")
                    if (url.isNotEmpty()) {
                        log("✅ SUCCESS! URL: $url")
                    } else {
                        log("!!! FAILURE: JSON valid but 'url' is empty.")
                    }
                } catch (e: Exception) {
                    log("!!! FAILURE: Could not parse Step 3 JSON.")
                }

            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string() ?: "No error body"
                log("💥 HTTP EXCEPTION ${e.code()}: $errorBody")
            } catch (e: Exception) {
                log("💥 CRITICAL EXCEPTION: ${e.message}")
                e.printStackTrace()
            } finally {
                isRunning = false
                log("--- END DEBUG ---")
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
        Text("MYEDU AUTO-DEBUGGER", color = Color.Cyan)
        Text("Just paste the token. IDs will be fetched automatically.", color = Color.Gray, fontSize = 10.sp)
        
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
                Text(if (vm.isRunning) "WORKING..." else "START AUTO-DEBUG")
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