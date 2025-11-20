package com.example.myedu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

class MainViewModel : ViewModel() {
    var isLoading by mutableStateOf(false)
    var logText by mutableStateOf("Ready (V10: Double-Lock Bypass)\n")

    fun appendLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        logText += "[$time] $msg\n"
    }

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            isLoading = true
            appendLog("--- STARTING V10 ---")
            
            try {
                // 1. LOGIN
                val loginResp = withContext(Dispatchers.IO) {
                    NetworkClient.api.login(LoginRequest(email, pass))
                }
                
                val token = loginResp.authorisation?.token
                if (token == null) {
                    appendLog("Login Failed: No token")
                    return@launch
                }
                
                // SAVE TOKEN GLOBALLY SO INTERCEPTOR CAN USE IT
                TokenStore.jwtToken = token
                appendLog("Token Saved. Injecting into Cookies...")

                // 2. GET USER (Interceptor will add headers automatically)
                appendLog("Fetching '/user'...")
                
                val rawJson = withContext(Dispatchers.IO) {
                    NetworkClient.api.getUser().string()
                }
                
                appendLog("RESPONSE 200 OK!")
                
                val json = JSONObject(rawJson)
                val userObj = json.optJSONObject("user")
                val name = userObj?.optString("name") ?: "Unknown"
                
                appendLog("========================")
                appendLog("SUCCESS: $name")
                appendLog("========================")
                
            } catch (e: retrofit2.HttpException) {
                appendLog("HTTP ERROR: ${e.code()}")
                if(e.code() == 401) appendLog("Still blocked. Check token format.")
            } catch (e: Exception) {
                appendLog("ERROR: ${e.message}")
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { LoggerScreen() } }
    }
}

@Composable
fun LoggerScreen(vm: MainViewModel = viewModel()) {
    Column(Modifier.fillMaxSize().background(Color.Black).padding(16.dp)) {
        Text("INJECTOR V10", color = Color.Green, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        var e by remember { mutableStateOf("") }
        var p by remember { mutableStateOf("") }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = e, onValueChange = { e = it }, label = { Text("Email") },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.Green, unfocusedBorderColor = Color.Gray)
            )
            OutlinedTextField(
                value = p, onValueChange = { p = it }, label = { Text("Pass") },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.Green, unfocusedBorderColor = Color.Gray)
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.login(e, p) }, enabled = !vm.isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Green, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (vm.isLoading) "CONNECTING..." else "LOGIN")
        }

        Spacer(Modifier.height(16.dp))
        Divider(color = Color.DarkGray)
        
        val scroll = rememberScrollState()
        LaunchedEffect(vm.logText) { scroll.animateScrollTo(scroll.maxValue) }

        Text(
            text = vm.logText, color = Color.Green,
            fontFamily = FontFamily.Monospace, fontSize = 12.sp,
            modifier = Modifier.fillMaxSize().verticalScroll(scroll)
        )
    }
}
