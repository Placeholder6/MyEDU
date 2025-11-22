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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

// Data Model
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

    // Cache
    private var cachedStudentId: Long = 0
    private var cachedInfoJson: String? = null
    private var cachedTranscriptJson: String? = null
    private var cachedResources: PdfResources? = null

    private val jsFetcher = JsResourceFetcher()

    fun log(msg: String) {
        viewModelScope.launch(Dispatchers.Main) {
            logs.add(msg)
        }
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
            log("--- Starting Data Fetch ---")
            
            val token = tokenInput.removePrefix("Bearer ").trim()

            try {
                NetworkClient.cookieJar.setDebugCookies(token)
                NetworkClient.interceptor.authToken = token

                // 1. Fetch JS Resources
                log("Step 1: Fetching Website Scripts...")
                cachedResources = jsFetcher.fetchResources { log(it) }
                
                if (cachedResources?.logicCode.isNullOrEmpty()) {
                     log("WARNING: Failed to extract PDF logic.")
                } else {
                     log("Success: Extracted PDF logic (${cachedResources?.logicCode?.length} chars).")
                }

                // 2. Fetch Student Info
                log("Step 2: Fetching Student Profile...")
                val studentId = getStudentIdFromToken(token)
                if (studentId == 0L) throw Exception("Invalid Token: ID not found")
                
                val infoRaw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getStudentInfo(studentId).string()
                }
                val infoJson = JSONObject(infoRaw)
                val movementId = infoJson.optJSONObject("lastStudentMovement")?.optLong("id") ?: 0L
                
                cachedStudentId = studentId
                cachedInfoJson = infoRaw
                log("Student ID: $studentId")

                // 3. Fetch Transcript JSON
                log("Step 3: Downloading Grades...")
                val transcriptRaw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getTranscriptData(studentId, movementId).string()
                }
                cachedTranscriptJson = transcriptRaw

                // 4. Parse
                log("Step 4: Parsing Data...")
                parseAndDisplayTranscript(transcriptRaw)
                log("--- Ready to Generate PDF ---")

            } catch (e: Exception) {
                log("ERROR: ${e.message}")
                e.printStackTrace()
            } finally {
                isBusy = false
            }
        }
    }

    fun generatePdf(webGenerator: WebPdfGenerator, filesDir: File, onPdfReady: (File) -> Unit) {
        if (cachedInfoJson == null || cachedTranscriptJson == null || cachedResources == null) {
            log("Error: Data missing. Click 'Fetch Data' first.")
            return
        }

        viewModelScope.launch {
            isBusy = true
            log("--- Starting PDF Generation ---")
            
            try {
                // 1. Get QR Code Link
                log("Step A: Requesting Document Key...")
                val linkReq = DocIdRequest(cachedStudentId)
                val linkRaw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getTranscriptLink(linkReq).string()
                }
                val keyJson = JSONObject(linkRaw)
                val linkId = keyJson.optLong("id")
                val qrUrl = keyJson.optString("url")
                log("Key acquired: ID $linkId")

                // 2. Run JS Generator
                log("Step B: Running JavaScript Engine...")
                val pdfBytes = webGenerator.generatePdf(
                    cachedInfoJson!!,
                    cachedTranscriptJson!!,
                    linkId,
                    qrUrl,
                    cachedResources!!
                ) { msg -> log(msg) }

                // 3. Save File
                log("Step C: Saving PDF...")
                val pdfFile = File(filesDir, "transcript.pdf")
                withContext(Dispatchers.IO) {
                    FileOutputStream(pdfFile).use { it.write(pdfBytes) }
                }

                log("SUCCESS: Saved to ${pdfFile.name}")
                onPdfReady(pdfFile)

            } catch (e: Exception) {
                log("FAILURE: ${e.message}")
                e.printStackTrace()
            } finally {
                isBusy = false
            }
        }
    }

    private fun parseAndDisplayTranscript(jsonString: String) {
        try {
            val items = mutableListOf<TranscriptItem>()
            val yearsArray = JSONArray(jsonString)

            for (i in 0 until yearsArray.length()) {
                val yearObj = yearsArray.optJSONObject(i)
                val semesters = yearObj.optJSONArray("semesters") ?: continue
                
                for (j in 0 until semesters.length()) {
                    val semObj = semesters.optJSONObject(j)
                    val subjects = semObj.optJSONArray("subjects") ?: continue
                    
                    for (k in 0 until subjects.length()) {
                        val sub = subjects.optJSONObject(k)
                        
                        // --- FIX: Updated Keys based on textfile (2).txt ---
                        val name = sub.optString("subject", "Unknown")
                        val credit = sub.optString("credit", "0")
                        
                        val examRule = sub.optJSONObject("exam_rule")
                        val markList = sub.optJSONObject("mark_list")
                        
                        val total = markList?.optString("finally")?.takeIf { it != "0" && it != "null" }
                            ?: markList?.optString("total") 
                            ?: "-"
                            
                        val letter = examRule?.optString("alphabetic") ?: "-" // Was 'letter'

                        items.add(TranscriptItem(name, credit, total, letter))
                    }
                }
            }
            transcriptList.addAll(items)
            log("Parsed ${items.size} grades.")
        } catch (e: Exception) {
            log("Parsing Failed: ${e.message}")
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webGenerator = WebPdfGenerator(this)
        
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(webGenerator, filesDir)
                }
            }
        }
    }
}

@Composable
fun MainScreen(webGenerator: WebPdfGenerator, filesDir: File) {
    val viewModel: MainViewModel = viewModel()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel.logs.size) {
        if(viewModel.logs.isNotEmpty()) listState.animateScrollToItem(viewModel.logs.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Top Controls
        Column(modifier = Modifier.weight(0.6f)) {
            OutlinedTextField(
                value = viewModel.tokenInput,
                onValueChange = { viewModel.tokenInput = it },
                label = { Text("Bearer Token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { clipboardManager.getText()?.text?.let { viewModel.tokenInput = it } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Paste from Clipboard")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.fetchTranscriptData() },
                    enabled = !viewModel.isBusy,
                    modifier = Modifier.weight(1f)
                ) { Text("1. Fetch Data") }

                Button(
                    onClick = {
                        viewModel.generatePdf(webGenerator, filesDir) { file ->
                            Toast.makeText(context, "PDF Saved!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !viewModel.isBusy && viewModel.transcriptList.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("2. Generate PDF") }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Table Header
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text("Subject", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Cr", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Scr", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Gr", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            // Table List
            if (viewModel.isBusy && viewModel.transcriptList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(viewModel.transcriptList) { item ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(item.subject, modifier = Modifier.weight(1f), fontSize = 12.sp, lineHeight = 14.sp)
                            Text(item.credit, modifier = Modifier.width(30.dp), fontSize = 12.sp)
                            Text(item.total, modifier = Modifier.width(30.dp), fontSize = 12.sp)
                            Text(item.grade, modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Divider(color = Color.LightGray, thickness = 0.5.dp)
                    }
                }
            }
        }
        
        // Debug Console
        Text("Logs", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Box(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .padding(8.dp)
        ) {
            LazyColumn(state = listState) {
                items(viewModel.logs) { log ->
                    Text(text = "> $log", color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
        }
    }
}