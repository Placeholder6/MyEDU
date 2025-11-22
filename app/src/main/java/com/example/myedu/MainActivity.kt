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
            log("--- Starting Fetch ---")
            
            val token = tokenInput.removePrefix("Bearer ").trim()

            try {
                NetworkClient.cookieJar.setDebugCookies(token)
                NetworkClient.interceptor.authToken = token

                log("1. JS Resources...")
                cachedResources = jsFetcher.fetchResources { log(it) }

                log("2. Student Info...")
                val sId = getStudentIdFromToken(token)
                if (sId == 0L) throw Exception("Invalid Token")
                
                val infoRaw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getStudentInfo(sId).string()
                }
                
                // FIX: Construct fullName for the PDF generator
                val infoJson = JSONObject(infoRaw)
                val lName = infoJson.optString("last_name", "")
                val fName = infoJson.optString("name", "")
                val pName = infoJson.optString("father_name", "")
                infoJson.put("fullName", "$lName $fName $pName".trim())
                
                val movementId = infoJson.optJSONObject("lastStudentMovement")?.optLong("id") ?: 0L
                
                cachedStudentId = sId
                cachedInfoJson = infoJson.toString() // Save the modified JSON
                log("Student: ${infoJson.optString("fullName")}")

                log("3. Downloading Grades...")
                val transcriptRaw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getTranscriptData(sId, mId).string()
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
        if (cachedInfoJson == null || cachedResources == null) {
            log("Error: Fetch data first.")
            return
        }

        viewModelScope.launch {
            isBusy = true
            try {
                log("A. Requesting Key...")
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

                log("SUCCESS: Saved to ${file.name}")
                onPdfReady(file)

            } catch (e: Throwable) {
                log("PDF ERROR: ${e.message}")
                e.printStackTrace()
            } finally {
                isBusy = false
            }
        }
    }

    private fun parseAndDisplayTranscript(jsonString: String) {
        try {
            val items = mutableListOf<TranscriptItem>()
            val arr = JSONArray(jsonString)

            for (i in 0 until arr.length()) {
                val yearObj = arr.optJSONObject(i)
                val sems = yearObj?.optJSONArray("semesters") ?: continue
                
                for (j in 0 until sems.length()) {
                    val semObj = sems.optJSONObject(j)
                    val subs = semObj?.optJSONArray("subjects") ?: continue
                    
                    for (k in 0 until subs.length()) {
                        val sub = subs.optJSONObject(k)
                        
                        val name = sub.optString("subject", "Unknown")
                        val credit = sub.optString("credit", "0")
                        
                        val mark = sub.optJSONObject("mark_list")
                        val rule = sub.optJSONObject("exam_rule")
                        
                        // Handle nulls gracefully for UI
                        val total = mark?.optString("finally")?.takeIf { it != "0" && it != "null" }
                            ?: mark?.optString("total") ?: "-"
                            
                        val grade = rule?.optString("alphabetic") ?: "-"

                        items.add(TranscriptItem(name, credit, total, grade))
                    }
                }
            }
            transcriptList.addAll(items)
            log("Parsed ${items.size} grades.")
        } catch (e: Exception) {
            log("Parse Error: ${e.message}")
        }
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
            Toast.makeText(this, "Init Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun MainScreen(webGenerator: WebPdfGenerator, filesDir: File) {
    val viewModel: MainViewModel = viewModel()
    val clipboard = LocalClipboardManager.current
    val state = rememberLazyListState()
    
    LaunchedEffect(viewModel.logs.size) { if(viewModel.logs.isNotEmpty()) state.animateScrollToItem(viewModel.logs.size - 1) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = viewModel.tokenInput, 
            onValueChange = { viewModel.tokenInput = it }, 
            label = { Text("Token") }, 
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { clipboard.getText()?.text?.let { viewModel.tokenInput = it } }, Modifier.fillMaxWidth()) {
             Text("Paste Token") 
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.fetchTranscriptData() }, Modifier.weight(1f)) { Text("1. Fetch") }
            Button(onClick = { viewModel.generatePdf(webGenerator, filesDir) {} }, Modifier.weight(1f)) { Text("2. PDF") }
        }
        
        Divider(Modifier.padding(vertical = 8.dp))

        LazyColumn(Modifier.weight(1f)) {
            items(viewModel.transcriptList) { t -> 
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text(t.subject, Modifier.weight(1f), fontSize = 12.sp)
                    Text(t.credit, Modifier.width(30.dp), fontSize = 12.sp)
                    Text(t.total, Modifier.width(30.dp), fontSize = 12.sp)
                    Text(t.grade, Modifier.width(30.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
                Divider(thickness = 0.5.dp, color = Color.LightGray)
            }
        }
        
        Text("Console", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Box(Modifier.height(150.dp).fillMaxWidth().background(Color(0xFF1E1E1E)).padding(4.dp)) {
            LazyColumn(state = state) {
                items(viewModel.logs) { Text("> $it", color = Color.Green, fontSize = 10.sp) }
            }
        }
    }
}