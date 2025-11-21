package com.example.myedu

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel : ViewModel() {
    // Using 'logs' instead of complex state to keep it simple for the console
    var logs by mutableStateOf("Ready. Paste token and click Run.\n")
    var isRunning by mutableStateOf(false)

    fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        logs += "[$time] $msg\n"
    }

    fun runDebug(tokenString: String, studentIdString: String) {
        if (tokenString.isBlank() || studentIdString.isBlank()) {
            log("Error: Token or ID empty.")
            return
        }

        val token = tokenString.trim()
        val studentId = studentIdString.trim().toLongOrNull() ?: 0L

        viewModelScope.launch {
            isRunning = true
            log("--- STARTING DEBUG SEQUENCE ---")
            log("Target ID: $studentId")
            
            // 1. Setup Session
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
                val step2Raw = withContext(Dispatchers.IO) {
                    NetworkClient.api.generateTranscript(DocIdRequest(studentId)).string()
                }
                log("RAW RESPONSE 2: $step2Raw")
                // Expected: "Ok :)" or similar text

                // Wait loop
                log("Waiting 3 seconds for server to generate PDF...")
                delay(3000)

                // --- STEP 3: RESOLVE LINK ---
                log(">>> STEP 3: Resolving Link (showlink)...")
                val step3Raw = withContext(Dispatchers.IO) {
                    NetworkClient.api.resolveDocLink(DocKeyRequest(key)).string()
                }
                log("RAW RESPONSE 3: $step3Raw")
                
                if (step3Raw.trim().isEmpty()) {
                    log("!!! FAILURE: Empty response in Step 3.")
                    return@launch
                }

                try {
                    val urlJson = JSONObject(step3Raw)
                    val url = urlJson.optString("url")
                    if (url.isNotEmpty()) {
                        log("✅ SUCCESS! URL: $url")
                    } else {
                        log("!!! FAILURE: JSON valid but 'url' is empty.")
                    }
                } catch (e: Exception) {
                    log("!!! FAILURE: Could not parse Step 3 JSON. Check Raw Response above.")
                }

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