package com.example.myedu

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
    var status by mutableStateOf("Ready")
    var userName by mutableStateOf("")
    var scanResults by mutableStateOf("")
    
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
            status = "Authenticating..."
            try {
                val resp = withContext(Dispatchers.IO) {
                    NetworkClient.api.login(LoginRequest(email, pass))
                }
                
                val token = resp.authorisation?.token
                if (token != null) {
                    TokenStore.jwtToken = token
                    context.getSharedPreferences("MyEduPrefs", Context.MODE_PRIVATE)
                        .edit().putString("jwt_token", token).apply()
                        
                    status = "Token Acquired. Verifying..."
                    verifyToken()
                } else {
                    status = "Login Failed: Server sent no token."
                }
            } catch (e: Exception) {
                status = "Error: ${e.message}"
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
                    status = "Token Verification Failed (401)"
                    appState = "LOGIN"
                }
            } catch (e: Exception) {
                status = "Verification Error: ${e.message}"
                appState = "LOGIN"
            }
        }
    }
    
    // --- FIXED SCANNER ---
    fun scanForGrades() {
        viewModelScope.launch {
            scanResults = "Scanning..."
            var log = ""
            
            // We call them sequentially using a helper to handle the 'suspend' keyword correctly
            log += checkEndpoint("studentSession") { NetworkClient.api.scanSession() }
            log += checkEndpoint("studentCurricula") { NetworkClient.api.scanCurricula() }
            log += checkEndpoint("student_mark_list") { NetworkClient.api.scanMarkList() }
            log += checkEndpoint("student/transcript") { NetworkClient.api.scanTranscript() }
            
            scanResults = log
        }
    }

    // Helper function explicitly marked as suspend
    private suspend fun checkEndpoint(name: String, apiCall: suspend () -> ResponseBody): String {
        return try {
            val res = withContext(Dispatchers.IO) { apiCall().string() }
            if (res.length > 100 && res.contains("{")) {
                "✅ $name: SUCCESS (${res.length} bytes)\n"
            } else {
                "❌ $name: Empty/Invalid\n"
            }
        } catch (e: Exception) {
            "❌ $name: ${e.message}\n"
        }
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
        Text("MyEDU Portal", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
        Spacer(Modifier.height(32.dp))
        
        var e by remember { mutableStateOf("") }
        var p by remember { mutableStateOf("") }
        
        OutlinedTextField(
            value = e, onValueChange = { e = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = p, onValueChange = { p = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        
        Button(
            onClick = { vm.login(e, p, context) }, 
            enabled = !vm.isLoading,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (vm.isLoading) "Connecting..." else "Log In")
        }
        Spacer(Modifier.height(16.dp))
        Text(vm.status, color = if(vm.status.contains("Error")) Color.Red else Color.DarkGray)
    }
}

@Composable
fun DashboardScreen(vm: MainViewModel, context: Context) {
    val scrollState = rememberScrollState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(vm.userName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { vm.logout(context) }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                Text("LOGOUT")
            }
        }
        Divider(Modifier.padding(vertical = 16.dp))
        
        Text("Grade Scanner", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.scanForGrades() }, modifier = Modifier.fillMaxWidth()) {
            Text("SCAN SERVER")
        }
        Spacer(Modifier.height(16.dp))
        
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Column(Modifier.padding(16.dp).verticalScroll(scrollState)) {
                Text(vm.scanResults, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }
    }
}
