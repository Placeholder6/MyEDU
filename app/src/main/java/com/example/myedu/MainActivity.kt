package com.example.myedu

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
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
import java.util.TimeZone

class MainViewModel : ViewModel() {
    var logText by mutableStateOf("Select a method to start.")
    var showWebView by mutableStateOf(false)

    init {
        DebugLog.onUpdate = { logText = it }
    }

    // METHOD A: AUTO COOKIES (NATIVE)
    fun runMethodA(e: String, p: String) {
        viewModelScope.launch {
            DebugLog.clear()
            DebugLog.log("--- RUNNING METHOD A (CookieJar) ---")
            SessionConfig.useCookieJar = true
            SessionConfig.useManualCookie = null
            SessionConfig.cookieJar.clear()

            try {
                val resp = withContext(Dispatchers.IO) { NetworkClient.api.login(LoginRequest(e, p)) }
                DebugLog.log("Login Status: ${resp.status}")
                
                // IMPORTANT: Manually inject Bearer for next request if CookieJar captured cookie
                val token = resp.authorisation?.token
                if (token != null) {
                    DebugLog.log("Token acquired. Testing /user...")
                    // For Method A, we normally rely on CookieJar, but let's try manual Bearer too
                    // because sometimes Jar misses it
                    SessionConfig.useManualCookie = "myedu-jwt-token=$token" // Hybrid hack
                    
                    val user = withContext(Dispatchers.IO) { NetworkClient.api.getUser().string() }
                    DebugLog.log("USER DATA: ${user.take(100)}...")
                } else {
                    DebugLog.log("FAIL: No token in login response.")
                }
            } catch (e: Exception) { DebugLog.log("ERROR: ${e.message}") }
        }
    }

    // METHOD B: MANUAL BAKING
    fun runMethodB(e: String, p: String) {
        viewModelScope.launch {
            DebugLog.clear()
            DebugLog.log("--- RUNNING METHOD B (Manual Bake) ---")
            SessionConfig.useCookieJar = false
            
            try {
                // 1. Login to get token
                val resp = withContext(Dispatchers.IO) { NetworkClient.api.login(LoginRequest(e, p)) }
                val token = resp.authorisation?.token
                
                if (token != null) {
                    DebugLog.log("Token found. Baking cookies...")
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.000000'Z'", Locale.US)
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                    val ts = sdf.format(Date())
                    
                    val cookieStr = "myedu-jwt-token=$token; my_edu_update=$ts"
                    SessionConfig.useManualCookie = cookieStr
                    
                    val user = withContext(Dispatchers.IO) { NetworkClient.api.getUser().string() }
                    DebugLog.log("SUCCESS? Body: ${user.take(100)}")
                }
            } catch (e: Exception) { DebugLog.log("ERROR: ${e.message}") }
        }
    }

    // METHOD C: VISIBLE WEBVIEW
    fun startMethodC() {
        DebugLog.clear()
        DebugLog.log("--- RUNNING METHOD C (Visual WebView) ---")
        showWebView = true
    }
    
    fun onWebViewCookies(cookies: String) {
        showWebView = false
        DebugLog.log("WebView Cookies Captured!")
        viewModelScope.launch {
            SessionConfig.useCookieJar = false
            SessionConfig.useManualCookie = cookies
            try {
                val user = withContext(Dispatchers.IO) { NetworkClient.api.getUser().string() }
                DebugLog.log("API TEST RESULT: ${user.take(100)}...")
            } catch (e: Exception) { DebugLog.log("API Fail: ${e.message}") }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { DebugUI() } }
    }
}

@Composable
fun DebugUI(vm: MainViewModel = viewModel()) {
    val clipboard = LocalClipboardManager.current
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    if (vm.showWebView) {
        WebViewDialog(vm)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("THE ULTIMATE DEBUGGER (V24)", style = MaterialTheme.typography.titleLarge, color = Color.Red)
        Spacer(Modifier.height(8.dp))
        
        Row {
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(4.dp))
            OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Pass") }, modifier = Modifier.weight(1f))
        }
        
        Spacer(Modifier.height(8.dp))
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { vm.runMethodA(email, pass) }, modifier = Modifier.weight(1f)) { Text("A: Native") }
            Spacer(Modifier.width(4.dp))
            Button(onClick = { vm.runMethodB(email, pass) }, modifier = Modifier.weight(1f)) { Text("B: Bake") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { vm.startMethodC() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C))) { Text("C: WEBVIEW") }
        }

        Spacer(Modifier.height(8.dp))
        Divider()
        
        // LOG CONSOLE
        SelectionContainer(Modifier.weight(1f).background(Color.Black).padding(8.dp)) {
            val scroll = rememberScrollState()
            Text(vm.logText, color = Color.Green, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.verticalScroll(scroll))
        }
        
        Button(
            onClick = { clipboard.setText(AnnotatedString(vm.logText)) }, 
            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("COPY LOGS TO CLIPBOARD")
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewDialog(vm: MainViewModel) {
    Dialog(onDismissRequest = { vm.showWebView = false }) {
        Card(Modifier.fillMaxWidth().height(500.dp)) {
            AndroidView(factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Mobile Safari/537.36"
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            val cookies = CookieManager.getInstance().getCookie(url)
                            if (cookies != null && cookies.contains("myedu-jwt-token")) {
                                vm.onWebViewCookies(cookies)
                            }
                        }
                    }
                    loadUrl("https://myedu.oshsu.kg/#/login")
                }
            })
        }
    }
}
