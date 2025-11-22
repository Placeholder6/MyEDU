package com.example.myedu

import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
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

    private var cachedStudentId: Long = 0
    private var cachedInfoJson: String? = null
    private var cachedTranscriptJson: String? = null
    private var cachedResources: PdfResources? = null

    private val jsFetcher = JsResourceFetcher()

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
            statusMessage = "Error: Please enter a token."
            return
        }

        viewModelScope.launch {
            isBusy = true
            statusMessage = "Initializing..."
            transcriptList.clear()
            
            val token = tokenInput.removePrefix("Bearer ").trim()

            try {
                NetworkClient.cookieJar.setDebugCookies(token)
                NetworkClient.interceptor.authToken = token

                // 1. Fetch JS Resources
                statusMessage = "Fetching App Resources..."
                cachedResources = jsFetcher.fetchResources()

                // 2. Fetch Student Info
                statusMessage = "Fetching Student Info..."
                val studentId = getStudentIdFromToken(token)
                if (studentId == 0L) throw Exception("Invalid Token: Could not extract ID")
                
                val infoRaw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getStudentInfo(studentId).string()
                }
                val infoJson = JSONObject(infoRaw)
                val movementId = infoJson.optJSONObject("lastStudentMovement")?.optLong("id") ?: 0L
                
                cachedStudentId = studentId
                cachedInfoJson = infoRaw

                // 3. Fetch Transcript Data
                statusMessage = "Downloading Grades..."
                val transcriptRaw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getTranscriptData(studentId, movementId).string()
                }
                cachedTranscriptJson = transcriptRaw

                // 4. Parse and Display
                parseAndDisplayTranscript(transcriptRaw)
                statusMessage = "Transcript Fetched Successfully"

            } catch (e: Exception) {
                statusMessage = "Error: ${e.message}"
                e.printStackTrace()
            } finally {
                isBusy = false
            }
        }
    }

    fun generatePdf(webGenerator: WebPdfGenerator, filesDir: File, onPdfReady: (File) -> Unit) {
        if (cachedInfoJson == null || cachedTranscriptJson == null || cachedResources == null) {
            statusMessage = "Error: Fetch data first!"
            return
        }

        viewModelScope.launch {
            isBusy = true
            statusMessage = "Getting Document Key..."
            
            try {
                val linkReq = DocIdRequest(cachedStudentId)
                val linkRaw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getTranscriptLink(linkReq).string()
                }
                val keyJson = JSONObject(linkRaw)
                val linkId = keyJson.optLong("id")
                val qrUrl = keyJson.optString("url")

                statusMessage = "Generating PDF (JavaScript)..."
                val pdfBytes = webGenerator.generatePdf(
                    cachedInfoJson!!,
                    cachedTranscriptJson!!,
                    linkId,
                    qrUrl,
                    cachedResources!!
                )

                val pdfFile = File(filesDir, "transcript.pdf")
                withContext(Dispatchers.IO) {
                    FileOutputStream(pdfFile).use { it.write(pdfBytes) }
                }

                statusMessage = "PDF Saved: ${pdfFile.absolutePath}"
                onPdfReady(pdfFile)

            } catch (e: Exception) {
                statusMessage = "PDF Error: ${e.message}"
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
                val semesters = yearObj.optJSONArray("semesters")
                
                if (semesters != null) {
                    for (j in 0 until semesters.length()) {
                        val semObj = semesters.optJSONObject(j)
                        val subjects = semObj.optJSONArray("subjects")
                        
                        if (subjects != null) {
                            for (k in 0 until subjects.length()) {
                                val sub = subjects.optJSONObject(k)
                                
                                // --- FIXED PARSING LOGIC ---
                                val name = sub.optString("subject") // Was "subject_name"
                                val credit = sub.optString("credit")
                                
                                val examRule = sub.optJSONObject("exam_rule")
                                val markList = sub.optJSONObject("mark_list")
                                
                                // Prioritize 'finally' score, then 'total'
                                val total = markList?.optString("finally") 
                                    ?: markList?.optString("total") 
                                    ?: "-"
                                    
                                val letter = examRule?.optString("alphabetic") ?: "-" // Was "letter"

                                items.add(TranscriptItem(name, credit, total, letter))
                            }
                        }
                    }
                }
            }
            transcriptList.addAll(items)
        } catch (e: Exception) {
            statusMessage = "Parsing Error: ${e.message}"
            e.printStackTrace()
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webGenerator = WebPdfGenerator(this)
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = viewModel.tokenInput,
            onValueChange = { viewModel.tokenInput = it },
            label = { Text("Paste Bearer Token") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(onClick = {
            clipboardManager.getText()?.text?.let { viewModel.tokenInput = it }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Paste from Clipboard")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = viewModel.statusMessage,
            color = if (viewModel.statusMessage.contains("Error")) Color.Red else Color.Blue,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.fetchTranscriptData() },
                enabled = !viewModel.isBusy,
                modifier = Modifier.weight(1f)
            ) {
                Text("1. Fetch Grades")
            }

            Button(
                onClick = {
                    viewModel.generatePdf(webGenerator, filesDir) { file ->
                        Toast.makeText(context, "Saved: ${file.name}", Toast.LENGTH_LONG).show()
                    }
                },
                enabled = !viewModel.isBusy && viewModel.transcriptList.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text("2. Make PDF")
            }
        }

        Divider(modifier = Modifier.padding(vertical = 12.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text("Subject", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold)
            Text("Cr", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold)
            Text("Scr", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold)
            Text("Grd", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold)
        }

        if (viewModel.isBusy && viewModel.transcriptList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(viewModel.transcriptList) { item ->
                    Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.subject, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium)
                            Text(item.credit, modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodyMedium)
                            Text(item.total, modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodyMedium)
                            Text(item.grade, modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}