package com.example.myedu

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WebPdfGenerator(private val context: Context) {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun generateAndUpload(token: String): String = suspendCancellableCoroutine { continuation ->
        
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true // Required for some SPAs

            // 1. SETUP COOKIES (Crucial for Login)
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            val domain = "myedu.oshsu.kg"
            
            // Set JWT Token
            cookieManager.setCookie(domain, "myedu-jwt-token=$token; Domain=$domain; Path=/")
            
            // Set Update Timestamp (Mimic browser)
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.000000'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val dateStr = sdf.format(Date())
            cookieManager.setCookie(domain, "my_edu_update=$dateStr; Domain=$domain; Path=/")
            
            cookieManager.flush()

            // 2. JS BRIDGE
            webView.addJavascriptInterface(object : Any() {
                @JavascriptInterface
                fun onSuccess(key: String) {
                    if (continuation.isActive) continuation.resume(key)
                }

                @JavascriptInterface
                fun onError(msg: String) {
                    if (continuation.isActive) continuation.resumeWithException(Exception(msg))
                }
                
                @JavascriptInterface
                fun log(msg: String) {
                    println("WebViewJS: $msg")
                }
            }, "AndroidBridge")

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Inject automation script once page loads
                    injectAutomationScript(view)
                }
            }

            // 3. LOAD THE TRANSCRIPT PAGE
            webView.loadUrl("https://myedu.oshsu.kg/#/Transcript")
        }
    }

    private fun injectAutomationScript(view: WebView?) {
        val script = """
            (function() {
                var attempts = 0;
                
                // 1. Poll for the PDF Button
                var checkBtn = setInterval(function() {
                    attempts++;
                    if (attempts > 30) { // 30 seconds timeout
                        clearInterval(checkBtn);
                        AndroidBridge.onError("Timeout: PDF Button not found.");
                        return;
                    }
                    
                    // Look for button with 'PDF' text or specific icon class
                    var btns = document.querySelectorAll('button');
                    for (var i = 0; i < btns.length; i++) {
                        // Check if it's the PDF button and NOT disabled (data loaded)
                        if (btns[i].innerText.includes('PDF') && !btns[i].disabled) {
                            AndroidBridge.log("Button found. Clicking...");
                            btns[i].click();
                            clearInterval(checkBtn);
                            startUrlWatcher();
                            return;
                        }
                    }
                    AndroidBridge.log("Waiting for data/button... " + attempts);
                }, 1000);

                // 2. Watch for Navigation (Success -> Redirects to /document/KEY)
                function startUrlWatcher() {
                    var lastUrl = window.location.href;
                    var checkUrl = setInterval(function() {
                        var currentUrl = window.location.href;
                        
                        if (currentUrl.includes('/document/')) {
                            clearInterval(checkUrl);
                            // Extract Key: .../document/m_t_XXXXX_71001
                            var parts = currentUrl.split('/');
                            var key = parts[parts.length - 1];
                            AndroidBridge.onSuccess(key);
                        }
                        
                        // If we navigated away but not to document (e.g. login page = failure)
                        if (currentUrl !== lastUrl && !currentUrl.includes('/Transcript') && !currentUrl.includes('/document/')) {
                             // Might have been kicked to login
                             AndroidBridge.log("Navigated to unexpected URL: " + currentUrl);
                        }
                    }, 500);
                }
            })();
        """.trimIndent()
        
        view?.evaluateJavascript(script, null)
    }
}