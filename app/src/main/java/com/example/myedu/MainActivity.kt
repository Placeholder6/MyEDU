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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
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

    fun runDebug(tokenString: String, webGenerator: WebPdfGenerator) {
        if (tokenString.isBlank()) {
            log("Error: Token is empty.")
            return
        }
        val token = tokenString.removePrefix("Bearer ").trim()

        viewModelScope.launch {
            isRunning = true
            log("--- STARTING AUTOMATION ---")
            log("Loading website in background...")

            try {
                // STEP 1: Automate the Website
                // This loads the real site, clicks the button, and waits for the success redirect.
                val key = webGenerator.generateAndUpload(token)
                log("✔ Website Success! Key: $key")

                // STEP 2: Resolve the Link
                log(">>> Resolving Final URL...")
                val step3Raw = withContext(Dispatchers.IO) {
                    NetworkClient.api.resolveDocLink(DocKeyRequest(key)).string()
                }
                
                val url = JSONObject(step3Raw).optString("url")
                if (url.isNotEmpty()) {
                    log("✅ FINAL PDF URL: $url")
                } else {
                    log("!!! FAIL: Key valid but URL empty.")
                }

            } catch (e: Exception) {
                log("💥 ERROR: ${e.message}")
                // Often means token expired or network blocked
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
        val webGenerator = WebPdfGenerator(this)
        setContent { DebugScreen(webGenerator) }
    }
}

@Composable
fun DebugScreen(webGenerator: WebPdfGenerator, vm: DebugViewModel = viewModel()) {
    var tokenInput by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Text("MYEDU AUTOMATION", color = Color.Cyan)
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
            Button(onClick = { vm.runDebug(tokenInput, webGenerator) }, enabled = !vm.isRunning, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Green, contentColor = Color.Black)) { Text(if (vm.isRunning) "RUNNING..." else "START") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { clipboardManager.setText(AnnotatedString(vm.logs)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) { Text("COPY") }
        }
        Spacer(Modifier.height(16.dp))
        Divider(color = Color.DarkGray)
        SelectionContainer(Modifier.fillMaxSize()) { Text(text = vm.logs, color = Color.Green, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.verticalScroll(scrollState)) }
        LaunchedEffect(vm.logs) { scrollState.animateScrollTo(scrollState.maxValue) }
    }
}