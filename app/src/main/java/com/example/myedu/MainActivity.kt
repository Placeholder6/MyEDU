package com.example.myedu

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    var isLoading by mutableStateOf(false)
    var statusMessage by mutableStateOf("Please Log In")
    var token by mutableStateOf<String?>(null)

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            isLoading = true
            statusMessage = "Connecting..."
            try {
                val request = LoginRequest(email, pass)
                val response = NetworkClient.api.login(request)
                
                // FIX: Access the token inside 'authorisation' object
                val receivedToken = response.authorisation.token
                
                if (receivedToken.isNotEmpty()) {
                    token = receivedToken
                    statusMessage = "Login Success! Student: ${response.authorisation.is_student}"
                    Log.d("API_SUCCESS", "Token: $receivedToken")
                } else {
                    statusMessage = "Login Failed: Token empty"
                }
            } catch (e: Exception) {
                statusMessage = "Error: ${e.message}"
                Log.e("API_ERROR", "Login failed", e)
            } finally {
                isLoading = false
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { OshSuApp() } }
    }
}

@Composable
fun OshSuApp(vm: MainViewModel = viewModel()) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("MyEDU Login Fixed", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(Modifier.height(24.dp))
        
        var e by remember { mutableStateOf("") } // Default empty
        var p by remember { mutableStateOf("") } // Default empty

        OutlinedTextField(value = e, onValueChange = { e = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = p, onValueChange = { p = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
        
        Spacer(Modifier.height(16.dp))
        
        Text(vm.statusMessage, color = if (vm.token != null) Color.Green else Color.Red)

        Spacer(Modifier.height(24.dp))
        
        Button(onClick = { vm.login(e, p) }, enabled = !vm.isLoading, modifier = Modifier.fillMaxWidth()) {
            Text(if (vm.isLoading) "Logging in..." else "Login")
        }
    }
}
