package com.example.myedu

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

// VIEW MODEL
class MainViewModel : ViewModel() {
    var isLoading by mutableStateOf(false)
    var statusMessage by mutableStateOf("Please Log In")
    var token by mutableStateOf<String?>(null)
    var studentInfo by mutableStateOf<StudentInfoResponse?>(null)

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            isLoading = true
            statusMessage = "Connecting..."
            try {
                val response = NetworkClient.api.login(LoginRequest(email, pass))
                val receivedToken = response.authorisation.token
                
                if (receivedToken.isNotEmpty()) {
                    token = "Bearer $receivedToken"
                    statusMessage = "Login Success! Fetching Profile..."
                    fetchProfile()
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

    private fun fetchProfile() {
        viewModelScope.launch {
            try {
                val info = NetworkClient.api.getStudentInfo(token!!)
                studentInfo = info
                statusMessage = "Welcome!"
            } catch (e: Exception) {
                statusMessage = "Profile Error: ${e.message}"
            }
        }
    }
}

// UI
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { OshSuApp() } }
    }
}

@Composable
fun OshSuApp(vm: MainViewModel = viewModel()) {
    if (vm.token == null) {
        LoginScreen(vm)
    } else {
        ProfileScreen(vm)
    }
}

@Composable
fun LoginScreen(vm: MainViewModel) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("MyEDU v3", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF1976D2))
        Spacer(Modifier.height(32.dp))
        
        var e by remember { mutableStateOf("") }
        var p by remember { mutableStateOf("") }

        OutlinedTextField(value = e, onValueChange = { e = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = p, onValueChange = { p = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
        
        Spacer(Modifier.height(16.dp))
        Text(vm.statusMessage, color = Color.Gray)
        Spacer(Modifier.height(24.dp))
        
        Button(onClick = { vm.login(e, p) }, enabled = !vm.isLoading, modifier = Modifier.fillMaxWidth()) {
            Text(if (vm.isLoading) "Loading..." else "Login")
        }
    }
}

@Composable
fun ProfileScreen(vm: MainViewModel) {
    val info = vm.studentInfo
    val mov = info?.studentMovement

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = info?.avatar,
                    contentDescription = "Profile",
                    modifier = Modifier.size(80.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(mov?.speciality?.name_en ?: "Student", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(mov?.avn_group_name ?: "Group Loading...", color = Color.Gray)
                    Text(mov?.faculty?.name_en ?: "Faculty", fontSize = 12.sp, lineHeight = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Academic Record", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        
        Box(
            modifier = Modifier.fillMaxWidth().height(100.dp).padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Grades coming soon...", color = Color.Gray)
        }
    }
}
