package com.example.myedu

import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class TranscriptItem(
    val subject: String,
    val credit: String,
    val total: String,
    val grade: String
)

class MainViewModel : ViewModel() {
    var tokenInput by mutableStateOf("")
    var statusMessage by mutableStateOf("Ready")
    var isBusy by mutableStateOf(false)
    var transcriptList = mutableStateListOf<TranscriptItem>()
    var logs = mutableStateListOf<String>()

    private var cachedStudentId: Long = 0
    private var cachedInfoJson: String? = null
    private var cachedTranscriptJson: String? = null
    private var cachedResources: PdfResources? = null

    private val jsFetcher = JsResourceFetcher()

    fun log(msg: String) {
        viewModelScope.launch(Dispatchers.Main) { logs.add(msg) }
    }

    private fun getStudentIdFromToken(token: String): Long {
        try {
            val parts = token.split(".")
            if (parts.size < 2) return 0L
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE))
            val json = JSONObject(payload)
            return json.optLong("sub", 0L)
        } catch (e: Exception) {
            return 0L
        }
    }

    fun fetchTranscriptData() {
        if (tokenInput.isBlank()) {
            log("Error: Token is empty")
            return
        }

        viewModelScope.launch {
            isBusy = true
            logs.clear()
            transcriptList.clear()
            log("--- Fetch Started ---")
            
            val token = tokenInput.removePrefix("Bearer ").trim()

            try {
                NetworkClient.cookieJar.setDebugCookies(token)
                NetworkClient.interceptor.authToken = token

                log("1. Resources...")
                cachedResources = jsFetcher.fetchResources { log(it) }

                log("2. Student Info...")
                val sId = getStudentIdFromToken(token)
                if (sId == 0L) throw Exception("Invalid Token")
                
                val infoRaw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getStudentInfo(sId).string()
                }
                
                // Clean Name Logic
                val infoJson = JSONObject(infoRaw)
                fun clean(key: String): String {
                    val v = infoJson.optString(key, "")
                    return if (v == "null" || v == "null ") "" else v
                }
                val fullName = "${clean("last_name")} ${clean("name")} ${clean("father_name")}".trim()
                infoJson.put("fullName", fullName)
                
                val movementId = infoJson.optJSONObject("lastStudentMovement")?.optLong("id") ?: 0L
                
                cachedStudentId = sId
                cachedInfoJson = infoJson.toString()
                log("Student: $fullName")

                log("3. Grades...")
                val transcriptRaw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getTranscriptData(sId, movementId).string()
                }
                cachedTranscriptJson = transcriptRaw
                
                parseAndDisplayTranscript(transcriptRaw)
                log("Fetch Complete.")

            } catch (e: Throwable) {
                log("ERROR: ${e.message}")
                e.printStackTrace()
            } finally {
                isBusy = false
            }
        }
    }

    fun generatePdf(webGenerator: WebPdfGenerator, filesDir: File, onPdfReady: (File) -> Unit) {
        if (cachedInfoJson == null) { log("Fetch data first."); return }
        viewModelScope.launch {
            isBusy = true
            try {
                log("A. Key...")
                val linkRaw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getTranscriptLink(DocIdRequest(cachedStudentId)).string()
                }
                val json = JSONObject(linkRaw)
                val linkId = json.optLong("id")
                val qrUrl = json.optString("url")

                log("B. Generating PDF...")
                val bytes = webGenerator.generatePdf(
                    cachedInfoJson!!,
                    cachedTranscriptJson!!,
                    linkId,
                    qrUrl,
                    cachedResources!!
                ) { msg -> log(msg) }

                log("C. Saving...")
                val file = File(filesDir, "transcript.pdf")
                withContext(Dispatchers.IO) {
                    FileOutputStream(file).use { it.write(bytes) }
                }

                log("SUCCESS: Saved locally.")
                onPdfReady(file)

                // --- UPLOAD STEP ---
                uploadGeneratedPdf(file, linkId)

            } catch (e: Throwable) { 
                log("PDF ERROR: ${e.message}") 
                e.printStackTrace()
            }
            finally { isBusy = false }
        }
    }

    private suspend fun uploadGeneratedPdf(file: File, linkId: Long) {
        try {
            log("D. Uploading to Server...")
            
            val plainText = "text/plain".toMediaTypeOrNull()
            val idBody = linkId.toString().toRequestBody(plainText)
            val studentIdBody = cachedStudentId.toString().toRequestBody(plainText)
            
            val pdfType = "application/pdf".toMediaTypeOrNull()
            val fileBody = file.asRequestBody(pdfType)
            val pdfPart = MultipartBody.Part.createFormData("pdf", "transcript.pdf", fileBody)

            val response = withContext(Dispatchers.IO) {
                NetworkClient.api.uploadPdf(idBody, studentIdBody, pdfPart).string()
            }
            log("UPLOAD COMPLETE!")
            log("Server Response: $response")
            
        } catch(e: Exception) {
            log("UPLOAD FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun parseAndDisplayTranscript(jsonString: String) {
        try {
            val items = mutableListOf<TranscriptItem>()
            val arr = JSONArray(jsonString)

            for (i in 0 until arr.length()) {
                val sems = arr.optJSONObject(i)?.optJSONArray("semesters") ?: continue
                for (j in 0 until sems.length()) {
                    val subs = sems.optJSONObject(j)?.optJSONArray("subjects") ?: continue
                    for (k in 0 until subs.length()) {
                        val sub = subs.optJSONObject(k)
                        
                        val name = sub.optString("subject", "Unknown")
                        val credit = sub.optString("credit", "0")
                        val mark = sub.optJSONObject("mark_list")
                        val rule = sub.optJSONObject("exam_rule")
                        
                        val total = mark?.optString("finally")?.takeIf { it != "0" && it != "null" }
                            ?: mark?.optString("total") ?: "-"
                        val grade = rule?.optString("alphabetic") ?: "-"
                        
                        items.add(TranscriptItem(name, credit, total, grade))
                    }
                }
            }
            transcriptList.addAll(items)
            log("Parsed ${items.size} grades.")
        } catch (e: Exception) { log("Parse Error: ${e.message}") }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val webGenerator = WebPdfGenerator(this)
            setContent {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        MainScreen(webGenerator, filesDir)
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}

@Composable
fun MainScreen(webGenerator: WebPdfGenerator, filesDir: File) {
    val viewModel: MainViewModel = viewModel()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val state = rememberLazyListState()
    
    LaunchedEffect(viewModel.logs.size) { if(viewModel.logs.isNotEmpty()) state.animateScrollToItem(viewModel.logs.size - 1) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(value = viewModel.tokenInput, onValueChange = { viewModel.tokenInput = it }, label = { Text("Token") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { clipboard.getText()?.text?.let { viewModel.tokenInput = it } }, Modifier.fillMaxWidth()) { Text("Paste Token") }
        
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.fetchTranscriptData() }, Modifier.weight(1f)) { Text("1. Fetch") }
            Button(onClick = { viewModel.generatePdf(webGenerator, filesDir) {} }, Modifier.weight(1f)) { Text("2. PDF & Upload") }
        }
        
        LazyColumn(Modifier.weight(1f)) {
            items(viewModel.transcriptList) { t -> 
                Text("${t.subject} | ${t.credit} | ${t.total} | ${t.grade}", fontSize = 12.sp)
                Divider()
            }
        }
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Console", fontWeight = FontWeight.Bold)
            Button(onClick = {
                clipboard.setText(AnnotatedString(viewModel.logs.joinToString("\n")))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }) { Text("Copy Logs") }
        }
        Box(Modifier.height(150.dp).fillMaxWidth().background(Color.Black).padding(4.dp)) {
            LazyColumn(state = state) {
                items(viewModel.logs) { Text("> $it", color = Color.Green, fontSize = 10.sp) }
            }
        }
    }
}