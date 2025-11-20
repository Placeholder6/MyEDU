package com.example.myedu

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext 
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import okhttp3.ResponseBody

class MainViewModel : ViewModel() {
    var appState by mutableStateOf("LOGIN") 
    var isLoading by mutableStateOf(false)
    var status by mutableStateOf("Ready (V21: The Mixer)")
    var userName by mutableStateOf("")
    var scanResults by mutableStateOf("Waiting for scan...")
    
    fun checkSavedToken(context: Context) {
        val prefs = context.getSharedPreferences("MyEduPrefs", Context.MODE_PRIVATE)
        val savedToken = prefs.getString("jwt_token", null)
        if (savedToken != null) {
            TokenStore.jwtToken = savedToken
            verifyToken()
        }
    }

    fun login(email: String, pass: String, context: Context) {
        viewModelScope.launch {
            isLoading = true
            status = "Logging in..."
            try {
                // 1. API Login
                val resp = withContext(Dispatchers.IO) {
                    NetworkClient.api.login(LoginRequest(email, pass))
                }
                
                val token = resp.authorisation?.token
                if (token != null) {
                    // 2. Set Global Token (Interceptor will inject it as Cookie)
                    TokenStore.jwtToken = token
                    
                    context.getSharedPreferences("MyEduPrefs", Context.MODE_PRIVATE)
                        .edit().putString("jwt_token", token).apply()
                        
                    status = "Token Acquired. Verifying..."
                    verifyToken()
                } else {
                    status = "Login Failed: No token."
                }
            } catch (e: Exception) {
                status = "Login Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    private fun verifyToken() {
        viewModelScope.launch {
            try {
                val raw = withContext(Dispatchers.IO) { NetworkClient.api.getUser().string() }
                val json = JSONObject(raw)
                val user = json.optJSONObject("user")
                
                if (user != null) {
                    userName = user.optString("name", "Student")
                    appState = "DASHBOARD"
                } else {
                    status = "Token Rejected (401)"
                    appState = "LOGIN"
                }
            } catch (e: Exception) {
                status = "Verification Failed: ${e.message}"
                appState = "LOGIN"
            }
        }
    }
    
    fun scanForGrades() {
        viewModelScope.launch {
            scanResults = "Scanning..."
            var log = ""
            
            log += checkEndpoint("studentSession") { NetworkClient.api.scanSession() }
            log += checkEndpoint("studentCurricula") { NetworkClient.api.scanCurricula() }
            log += checkEndpoint("student_mark_list") { NetworkClient.api.scanMarkList() }
            log += checkEndpoint("student/transcript") { NetworkClient.api.scanTranscript() }
            
            scanResults = log
        }
    }

    private suspend fun checkEndpoint(name: String, call: suspend () -> ResponseBody): String {
        return try {
            val res = withContext(Dispatchers.IO) { call().string() }
            if (res.length > 50 && res.contains("{")) "✅ $name: SUCCESS (${res.length}B)\n" 
            else "❌ $name: Empty\n"
        } catch (e: Exception) { "❌ $name: Error\n" }
    }
    
    fun logout(context: Context) {
        context.getSharedPreferences("MyEduPrefs", Context.MODE_PRIVATE).edit().clear().apply()
        appState = "LOGIN"
        status = "Logged Out"
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AppUI() } }
    }
}

@Composable
fun AppUI(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.checkSavedToken(context) }

    if (vm.appState == "LOGIN") {
        LoginScreen(vm, context)
    } else {
        DashboardScreen(vm, context)
    }
}

@Composable
fun LoginScreen(vm: MainViewModel, context: Context) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("MyEDU Mixer", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
        Spacer(Modifier.height(32.dp))
        
        var e by remember { mutableStateOf("") }
        var p by remember { mutableStateOf("") }
        
        OutlinedTextField(value = e, onValueChange = { e = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = p, onValueChange = { p = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        
        Button(
            onClick = { vm.login(e, p, context) }, 
            enabled = !vm.isLoading,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(if (vm.isLoading) "Connecting..." else "Log In")
        }
        Spacer(Modifier.height(16.dp))
        Text(vm.status, color = if(vm.status.contains("Error")) Color.Red else Color.Gray)
    }
}

@Composable
fun DashboardScreen(vm: MainViewModel, context: Context) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(vm.userName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { vm.logout(context) }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                Text("LOGOUT")
            }
        }
        Divider(Modifier.padding(vertical = 16.dp))
        
        Button(onClick = { vm.scanForGrades() }, modifier = Modifier.fillMaxWidth()) {
            Text("SCAN SERVER")
        }
        Spacer(Modifier.height(16.dp))
        
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF212121)), modifier = Modifier.fillMaxWidth().weight(1f)) {
            Text(vm.scanResults, color = Color.Green, modifier = Modifier.padding(16.dp))
        }
    }
}
