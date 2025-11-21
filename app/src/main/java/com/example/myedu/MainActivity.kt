package com.example.myedu

import android.os.Bundle
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
    var logs by mutableStateOf("Ready. Enter IDs and click Run.\n")
    var isRunning by mutableStateOf(false)

    fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        logs += "[$time] $msg\n"
    }

    fun runDebug(tokenString: String, studentIdString: String, movementIdString: String) {
        if (tokenString.isBlank() || studentIdString.isBlank() || movementIdString.isBlank()) {
            log("Error: Token, Student ID, or Movement ID empty.")
            return
        }

        val token = tokenString.removePrefix("Bearer ").trim()
        val studentId = studentIdString.trim().toLongOrNull() ?: 0L
        val movementId = movementIdString.trim().toLongOrNull() ?: 0L

        viewModelScope.launch {
            isRunning = true
            log("--- STARTING DEBUG SEQUENCE ---")
            log("Student ID: $studentId | Movement ID: $movementId")
            
            NetworkClient.cookieJar.setDebugCookies(token)
            NetworkClient.interceptor.authToken = token
            log("Cookies & Headers Configured.")

            try {
                // --- STEP 1: GET KEY ---
                log(">>> STEP 1: Requesting Key (form13link)...")
                val step1Raw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getTranscriptLink(DocIdRequest(studentId)).string()
                }
                log("RAW RESPONSE 1: $step1Raw")

                val keyJson = try { JSONObject(step1Raw) } catch(e:Exception) { 
                    log("!!! FAIL: Step 1 is not JSON"); return@launch 
                }
                
                val key = keyJson.optString("key")
                if (key.isEmpty()) {
                    log("!!! FAILURE: No 'key' found in Step 1 response.")
                    return@launch
                }
                log("KEY ACQUIRED: $key")

                // --- STEP 2: TRIGGER GENERATION ---
                log(">>> STEP 2: Triggering Generation (form13)...")
                
                // FIX: Sending 'pdf = true'
                val request2 = TranscriptRequest(
                    id = movementId,
                    id_student = studentId, 
                    id_movement = movementId,
                    pdf = true
                )
                
                val step2Raw = withContext(Dispatchers.IO) {
                    NetworkClient.api.generateTranscript(request2).string()
                }
                log("RAW RESPONSE 2: $step2Raw")

                log("Waiting 3 seconds...")
                delay(3000)

                // --- STEP 3: RESOLVE LINK ---
                log(">>> STEP 3: Resolving Link (showlink)...")
                val step3Raw = withContext(Dispatchers.IO) {
                    NetworkClient.api.resolveDocLink(DocKeyRequest(key)).string()
                }
                log("RAW RESPONSE 3: $step3Raw")
                
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
                e.printStackTrace()
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
    var studentIdInput by remember { mutableStateOf("") }
    var movementIdInput by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Text("TRANSCRIPT DEBUGGER v3", color = Color.Cyan)
        
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
        
        Spacer(Modifier.height(8.dp))
        
        Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = studentIdInput,
                onValueChange = { studentIdInput = it },
                label = { Text("Student ID (71001)") },
                modifier = Modifier.weight(1f).padding(end = 4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedBorderColor = Color.Cyan,
                    unfocusedBorderColor = Color.Gray
                )
            )
            OutlinedTextField(
                value = movementIdInput,
                onValueChange = { movementIdInput = it },
                label = { Text("Move ID (33779)") },
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedBorderColor = Color.Cyan,
                    unfocusedBorderColor = Color.Gray
                )
            )
        }

        Spacer(Modifier.height(16.dp))

        Row {
            Button(
                onClick = { vm.runDebug(tokenInput, studentIdInput, movementIdInput) },
                enabled = !vm.isRunning,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Green, contentColor = Color.Black)
            ) {
                Text(if (vm.isRunning) "RUNNING..." else "RUN DEBUG")
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