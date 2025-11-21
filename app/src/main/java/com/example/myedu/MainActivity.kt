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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebugViewModel : ViewModel() {
    var logs by mutableStateOf("Ready. Paste Token and click Fetch.\n")
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

    fun fetchData(tokenString: String) {
        if (tokenString.isBlank()) {
            log("Error: Token is empty.")
            return
        }

        val token = tokenString.removePrefix("Bearer ").trim()

        viewModelScope.launch {
            isRunning = true
            logs = "" // Clear previous logs
            log("--- STARTING DATA FETCH ---")
            
            NetworkClient.cookieJar.setDebugCookies(token)
            NetworkClient.interceptor.authToken = token
            log("Cookies & Headers Configured.")

            try {
                // --- PRE-STEP: DECODE TOKEN ---
                log(">>> EXTRACTING STUDENT ID...")
                val studentId = getStudentIdFromToken(token)
                if (studentId == 0L) { log("!!! FAIL: Could not decode Token"); return@launch }
                log("✔ Student ID: $studentId")

                // --- STEP 1: FETCH STUDENT DETAILS ---
                log("\n>>> STEP 1: Fetching Details (searchstudentinfo)...")
                val infoRaw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getStudentInfo(studentId).string()
                }
                log("RESPONSE 1 (Snippet): ${infoRaw.take(100)}...")
                
                val infoJson = try { JSONObject(infoRaw) } catch(e:Exception) {
                     log("!!! FAIL: Step 1 is not JSON"); return@launch
                }
                
                // Extract Movement ID
                val movementObj = infoJson.optJSONObject("lastStudentMovement")
                val movementId = movementObj?.optLong("id") ?: 0L

                if (movementId == 0L) { log("!!! FAIL: No 'lastStudentMovement.id' found."); return@launch }
                log("✔ Movement ID Found: $movementId")

                // --- STEP 2: FETCH TRANSCRIPT ---
                log("\n>>> STEP 2: Fetching Transcript (studenttranscript)...")
                val transcriptRaw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getTranscriptData(studentId, movementId).string()
                }
                
                if (transcriptRaw.isEmpty()) {
                    log("!!! FAIL: Transcript response is empty.")
                    return@launch
                }
                
                log("✔ SUCCESS! Data Length: ${transcriptRaw.length} chars")
                log("\n--- TRANSCRIPT DATA (COPY THIS) ---")
                log(transcriptRaw) // PRINT THE FULL JSON
                log("--- END OF DATA ---\n")

            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string() ?: "No body"
                log("💥 HTTP ERROR ${e.code()}: $errorBody")
            } catch (e: Exception) {
                log("💥 EXCEPTION: ${e.message}")
                e.printStackTrace()
            } finally {
                isRunning = false
                log("--- FETCH COMPLETE ---")
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
        Text("DATA FETCH DEBUGGER", color = Color.Cyan)
        
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
                onClick = { vm.fetchData(tokenInput) },
                enabled = !vm.isRunning,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow, contentColor = Color.Black)
            ) {
                Text(if (vm.isRunning) "FETCHING..." else "GET DATA")
            }
            
            Spacer(Modifier.width(8.dp))
            
            Button(
                onClick = { clipboardManager.setText(AnnotatedString(vm.logs)) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("COPY OUTPUT")
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