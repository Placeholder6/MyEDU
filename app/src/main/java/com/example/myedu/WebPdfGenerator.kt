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
            webView.settings.domStorageEnabled = true

            // 1. SETUP COOKIES
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            val domain = "myedu.oshsu.kg"
            
            cookieManager.setCookie(domain, "myedu-jwt-token=$token; Domain=$domain; Path=/")
            
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
                    // Inject the multi-step script once the page loads
                    if (url != null && !url.contains("/document/")) {
                        injectAutomationScript(view)
                    }
                }
            }

            // 3. LOAD THE MAIN PAGE (Start from root to find the menu)
            webView.loadUrl("https://myedu.oshsu.kg/")
        }
    }

    private fun injectAutomationScript(view: WebView?) {
        val script = """
            (function() {
                // Prevent multiple injections
                if (window.isAutomating) return;
                window.isAutomating = true;

                var attempts = 0;
                var step = 1; // 1 = Find Transcript, 2 = Find PDF, 3 = Wait for Key

                AndroidBridge.log("Automation Started. Step 1: Looking for 'Транскрипт'...");

                var timer = setInterval(function() {
                    attempts++;
                    if (attempts > 60) { // 60 seconds total timeout
                        clearInterval(timer);
                        AndroidBridge.onError("Timeout at Step " + step);
                        return;
                    }

                    // --- STEP 1: FIND AND CLICK 'Транскрипт' ---
                    if (step === 1) {
                        // Search all likely clickable elements for the Russian text
                        var elements = document.querySelectorAll('a, button, span, div.menu-item'); 
                        for (var i = 0; i < elements.length; i++) {
                            if (elements[i].innerText && elements[i].innerText.trim() === "Транскрипт") {
                                AndroidBridge.log("Found 'Транскрипт'. Clicking...");
                                elements[i].click();
                                step = 2;
                                attempts = 0; // Reset timeout for next step
                                AndroidBridge.log("Step 2: Waiting for 'PDF' button...");
                                return;
                            }
                        }
                    }

                    // --- STEP 2: FIND AND CLICK 'PDF' BUTTON ---
                    if (step === 2) {
                        var btns = document.querySelectorAll('button');
                        for (var i = 0; i < btns.length; i++) {
                            // Check for 'PDF' text and ensure it's not disabled (loaded)
                            if (btns[i].innerText.includes('PDF') && !btns[i].disabled) {
                                AndroidBridge.log("Found 'PDF' Button. Clicking...");
                                btns[i].click();
                                step = 3;
                                attempts = 0; // Reset timeout for next step
                                AndroidBridge.log("Step 3: Waiting for redirect...");
                                return;
                            }
                        }
                    }

                    // --- STEP 3: CHECK URL FOR SUCCESS ---
                    if (step === 3) {
                        var currentUrl = window.location.href;
                        if (currentUrl.includes('/document/')) {
                            clearInterval(timer);
                            // Extract Key: .../document/m_t_XXXXX_71001
                            var parts = currentUrl.split('/');
                            var key = parts[parts.length - 1];
                            AndroidBridge.log("Success! Key found: " + key);
                            AndroidBridge.onSuccess(key);
                        }
                    }

                }, 1000); // Check every second
            })();
        """.trimIndent()
        
        view?.evaluateJavascript(script, null)
    }
}