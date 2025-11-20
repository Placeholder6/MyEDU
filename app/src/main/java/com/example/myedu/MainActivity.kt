package com.example.myedu

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
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
    var appState by mutableStateOf("LOGIN")
    var debugLog by mutableStateOf("Initializing Chameleon Mode...")
    var userName by mutableStateOf("")
    var userEmail by mutableStateOf("")

    fun onLoginSuccess(cookies: String, ua: String) {
        // Prevent double-firing
        if (appState == "LOADING" || appState == "DASHBOARD") return
        
        appState = "LOADING"
        CredentialStore.cookies = cookies
        CredentialStore.userAgent = ua
        debugLog = "Session Captured. Verifying with API..."
        
        fetchUserData()
    }

    private fun fetchUserData() {
        viewModelScope.launch {
            try {
                val rawJson = withContext(Dispatchers.IO) {
                    NetworkClient.api.getUser().string()
                }
                
                val json = JSONObject(rawJson)
                val user = json.optJSONObject("user")
                userName = user?.optString("name") ?: "Unknown"
                userEmail = user?.optString("email") ?: ""
                
                appState = "DASHBOARD"
            } catch (e: Exception) {
                debugLog = "API Failed: ${e.message}\nCheck credentials."
                // If API fails, maybe the cookie was empty? Go back to login.
                // appState = "LOGIN" 
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { HybridApp() } }
    }
}

@Composable
fun HybridApp(vm: MainViewModel = viewModel()) {
    when (vm.appState) {
        "LOGIN" -> WebViewLoginScreen(vm)
        "LOADING" -> LoadingScreen(vm)
        "DASHBOARD" -> DashboardScreen(vm)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewLoginScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize()) {
        // Hidden status bar to debug URLs
        Text("V13: Chameleon Mode", Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        
                        // CRITICAL: SPOOF USER AGENT (Pretend to be the Chrome that worked for you)
                        userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Mobile Safari/537.36"
                    }
                    
                    // CRITICAL: ENABLE 3RD PARTY COOKIES (For API calls)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    CookieManager.getInstance().setAcceptCookie(true)

                    webChromeClient = WebChromeClient() // Better JS handling
                    
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            Log.d("WEBVIEW", "Loading: $url")
                            checkCookies(view, url, vm)
                        }
                        
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            checkCookies(view, url, vm)
                        }
                    }
                    loadUrl("https://myedu.oshsu.kg/#/login")
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

fun checkCookies(view: WebView?, url: String?, vm: MainViewModel) {
    if (url != null && (url.contains("/main") || url.contains("dashboard"))) {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(url)
        val ua = view?.settings?.userAgentString ?: ""
        
        Log.d("WEBVIEW_CHECK", "URL: $url | Cookies: $cookies")

        // Only trigger if we actually have the token
        if (cookies != null && cookies.contains("myedu-jwt-token")) {
            vm.onLoginSuccess(cookies, ua)
        }
    }
}

@Composable
fun LoadingScreen(vm: MainViewModel) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(vm.debugLog, color = Color.Blue)
    }
}

@Composable
fun DashboardScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("SUCCESS!", style = MaterialTheme.typography.displayMedium, color = Color(0xFF2E7D32))
        Spacer(Modifier.height(24.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Student:", style = MaterialTheme.typography.labelMedium)
                Text(vm.userName, style = MaterialTheme.typography.headlineMedium, color = Color.Black)
                Spacer(Modifier.height(8.dp))
                Text(vm.userEmail, style = MaterialTheme.typography.bodyLarge, color = Color.DarkGray)
            }
        }
        
        Spacer(Modifier.height(32.dp))
        Text("Now we can fetch grades via API.", style = MaterialTheme.typography.bodySmall)
    }
}
