package com.example.myedu

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainViewModel : ViewModel() {
    var appState by mutableStateOf("CHECKING_SAVED") 
    var userName by mutableStateOf("")
    var statusLog by mutableStateOf("Initializing...")

    // 1. CHECK FOR SAVED TOKEN ON STARTUP
    fun checkSavedToken(context: Context) {
        val prefs = context.getSharedPreferences("MyEduPrefs", Context.MODE_PRIVATE)
        val savedToken = prefs.getString("jwt_token", null)
        
        if (savedToken != null) {
            statusLog = "Saved token found. Verifying..."
            TokenStore.manualToken = savedToken
            verifyToken(savedToken, context)
        } else {
            appState = "LOGIN_WEBVIEW"
        }
    }

    // 2. CALLED WHEN WEBVIEW LOGS IN SUCCESSFULLY
    fun onWebViewLogin(token: String, context: Context) {
        if (appState == "DASHBOARD") return // Already done
        
        statusLog = "Login Detected! Verifying..."
        appState = "LOADING"
        
        TokenStore.manualToken = token
        // Save for next time
        context.getSharedPreferences("MyEduPrefs", Context.MODE_PRIVATE)
            .edit().putString("jwt_token", token).apply()
            
        verifyToken(token, context)
    }

    // 3. VERIFY TOKEN WITH API
    private fun verifyToken(token: String, context: Context) {
        viewModelScope.launch {
            try {
                val rawJson = withContext(Dispatchers.IO) {
                    NetworkClient.api.getUser().string()
                }
                val json = JSONObject(rawJson)
                val user = json.optJSONObject("user")
                
                if (user != null) {
                    userName = user.optString("name", "Student")
                    appState = "DASHBOARD"
                } else {
                    statusLog = "Token Invalid. Please Login again."
                    appState = "LOGIN_WEBVIEW"
                }
            } catch (e: Exception) {
                statusLog = "Connection Failed: ${e.message}"
                // If API fails, we might be offline or token expired. 
                // Stay on loading or go back to login if strictly necessary.
                appState = "LOGIN_WEBVIEW" 
            }
        }
    }
    
    fun logout(context: Context) {
        val prefs = context.getSharedPreferences("MyEduPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        
        // Clear WebView Cookies too
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        
        appState = "LOGIN_WEBVIEW"
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { MainApp() } }
    }
}

@Composable
fun MainApp(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        vm.checkSavedToken(context)
    }

    when (vm.appState) {
        "CHECKING_SAVED", "LOADING" -> LoadingScreen(vm)
        "LOGIN_WEBVIEW" -> WebViewScreen(vm, context)
        "DASHBOARD" -> DashboardScreen(vm, context)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(vm: MainViewModel, context: Context) {
    Column(Modifier.fillMaxSize()) {
        Text("Log in below (Auto-detects success)", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Mobile Safari/537.36"
                    }
                    
                    // CRITICAL: Fix for "Incorrect Password"
                    // We need to trick the server into thinking this is NOT an app.
                    
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            checkCookiesForToken(url, vm, context)
                        }
                        
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            checkCookiesForToken(url, vm, context)
                        }
                    }
                    
                    // Load with NO X-Requested-With header
                    val headers = mapOf("X-Requested-With" to "")
                    loadUrl("https://myedu.oshsu.kg/#/login", headers)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

fun checkCookiesForToken(url: String?, vm: MainViewModel, context: Context) {
    if (url == null) return
    
    val cookieManager = CookieManager.getInstance()
    val cookies = cookieManager.getCookie(url) ?: return
    
    if (cookies.contains("myedu-jwt-token")) {
        // EXTRACT TOKEN
        val token = cookies.split(";")
            .find { it.trim().startsWith("myedu-jwt-token=") }
            ?.substringAfter("=")
            ?.trim()
            
        if (!token.isNullOrEmpty()) {
            vm.onWebViewLogin(token, context)
        }
    }
}

@Composable
fun LoadingScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(vm.statusLog)
    }
}

@Composable
fun DashboardScreen(vm: MainViewModel, context: Context) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Welcome,", style = MaterialTheme.typography.labelLarge)
        Text(vm.userName, style = MaterialTheme.typography.headlineLarge, color = Color(0xFF2E7D32))
        
        Divider(Modifier.padding(vertical = 16.dp))
        
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))) {
            Column(Modifier.padding(16.dp)) {
                Text("Grades Module", style = MaterialTheme.typography.titleMedium)
                Text("Waiting for API Endpoint...", color = Color.Gray)
                // We need the CURL command for the Transcript page to build this part!
            }
        }
        
        Spacer(Modifier.weight(1f))
        
        Button(
            onClick = { vm.logout(context) }, 
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("LOGOUT")
        }
    }
}
