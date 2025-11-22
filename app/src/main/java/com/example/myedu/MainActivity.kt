package com.example.myedu

import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- Data Models for UI ---
data class SubjectModel(
    val name: String,
    val credit: String,
    val grade: String
)

data class SemesterModel(
    val name: String,
    val subjects: List<SubjectModel>
)

// --- Activity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize helper classes
        val webGenerator = WebPdfGenerator(this)
        val filesDir = this.filesDir

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: MainViewModel = viewModel()
                    MainScreen(viewModel, webGenerator, filesDir)
                }
            }
        }
    }
}

// --- UI Composable ---
@Composable
fun MainScreen(viewModel: MainViewModel, webGenerator: WebPdfGenerator, filesDir: File) {
    var tokenInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 1. Token Input
        OutlinedTextField(
            value = tokenInput,
            onValueChange = { tokenInput = it },
            label = { Text("Paste JWT Token") },
            placeholder = { Text("eyJ...") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.fetchGrades(tokenInput) },
                enabled = !viewModel.isBusy,
                modifier = Modifier.weight(1f)
            ) {
                Text("1. Get Grades")
            }

            Button(
                onClick = { viewModel.generatePdf(webGenerator, filesDir) },
                enabled = !viewModel.isBusy && viewModel.canGeneratePdf,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("2. Make PDF")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Status & Logs
        Text(
            text = viewModel.statusMessage,
            color = if (viewModel.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // 4. Content Area (List or Logs)
        if (viewModel.semesters.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(viewModel.semesters) { semester ->
                    SemesterCard(semester)
                }
            }
        } else {
            // Show logs if no data yet
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    text = viewModel.logs,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun SemesterCard(semester: SemesterModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = semester.name,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Divider(color = Color.LightGray)
            Spacer(modifier = Modifier.height(4.dp))
            
            semester.subjects.forEach { subject ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = subject.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(0.7f)
                    )
                    Text(
                        text = "${subject.grade} (${subject.credit})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(0.3f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            }
        }
    }
}

// --- ViewModel ---
class MainViewModel : ViewModel() {
    // UI State
    var statusMessage by mutableStateOf("Ready")
    var logs by mutableStateOf("")
    var isBusy by mutableStateOf(false)
    var isError by mutableStateOf(false)
    
    var semesters by mutableStateOf<List<SemesterModel>>(emptyList())
    var canGeneratePdf by mutableStateOf(false)

    // Internal Data Cache for PDF Generation
    private var cachedResources: PdfResources? = null
    private var cachedInfoRaw: String? = null
    private var cachedTranscriptRaw: String? = null
    private var cachedStudentId: Long = 0L

    private val jsFetcher = JsResourceFetcher()

    private fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        logs += "[$time] $msg\n"
    }

    // Step 1: Fetch and Display Grades
    fun fetchGrades(tokenInput: String) {
        if (tokenInput.isBlank()) {
            statusMessage = "Please enter a token"
            isError = true
            return
        }

        val token = tokenInput.removePrefix("Bearer ").trim()

        viewModelScope.launch {
            try {
                isBusy = true
                isError = false
                canGeneratePdf = false
                semesters = emptyList()
                logs = ""
                statusMessage = "Fetching Data..."

                // Configure Networking
                NetworkClient.cookieJar.setDebugCookies(token)
                NetworkClient.interceptor.authToken = token

                // A. Fetch JS (Required for PDF later, doing it now to ensure readiness)
                log("Fetching JS Resources...")
                cachedResources = jsFetcher.fetchResources()
                log("JS Fetched.")

                // B. Get Student ID from Token
                cachedStudentId = getStudentIdFromToken(token)
                if (cachedStudentId == 0L) throw Exception("Could not extract Student ID from token")

                // C. Fetch Student Info (to get Movement ID)
                log("Fetching Student Info ($cachedStudentId)...")
                cachedInfoRaw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getStudentInfo(cachedStudentId).string()
                }
                
                val infoJson = JSONObject(cachedInfoRaw!!)
                val movementId = infoJson.optJSONObject("lastStudentMovement")?.optLong("id") ?: 0L
                
                // D. Fetch Transcript Data
                log("Fetching Transcript (Mov: $movementId)...")
                cachedTranscriptRaw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getTranscriptData(cachedStudentId, movementId).string()
                }
                log("Transcript Downloaded.")

                // E. Parse for UI
                parseAndSetDisplayData(cachedTranscriptRaw!!)
                
                statusMessage = "Grades Loaded. Ready to PDF."
                canGeneratePdf = true

            } catch (e: Exception) {
                e.printStackTrace()
                isError = true
                statusMessage = "Error: ${e.message}"
                log("Crash: ${e.message}")
            } finally {
                isBusy = false
            }
        }
    }

    // Step 2: Generate PDF
    fun generatePdf(webGenerator: WebPdfGenerator, filesDir: File) {
        if (cachedResources == null || cachedInfoRaw == null || cachedTranscriptRaw == null) {
            statusMessage = "Data missing. Fetch grades first."
            return
        }

        viewModelScope.launch {
            try {
                isBusy = true
                statusMessage = "Generating PDF..."
                log("Starting PDF Generation...")

                // A. Get Document Key/Link (Required for QR code in PDF)
                val linkResponse = withContext(Dispatchers.IO) {
                    NetworkClient.api.getTranscriptLink(DocIdRequest(cachedStudentId)).string()
                }
                val linkJson = JSONObject(linkResponse)
                val linkId = linkJson.optLong("id")
                val qrUrl = linkJson.optString("url")
                log("Link ID: $linkId, QR Generated.")

                // B. Run Generation inside WebView
                val pdfBytes = webGenerator.generatePdf(
                    cachedInfoRaw!!,
                    cachedTranscriptRaw!!,
                    linkId,
                    qrUrl,
                    cachedResources!!
                )

                // C. Save to File
                val fileName = "transcript_${System.currentTimeMillis()}.pdf"
                val file = File(filesDir, fileName)
                withContext(Dispatchers.IO) {
                    FileOutputStream(file).use { it.write(pdfBytes) }
                }

                log("PDF Created: ${file.absolutePath}")
                statusMessage = "Saved: $fileName"

            } catch (e: Exception) {
                e.printStackTrace()
                isError = true
                statusMessage = "PDF Failed: ${e.message}"
                log("PDF Error: ${e.message}")
            } finally {
                isBusy = false
            }
        }
    }

    private fun parseAndSetDisplayData(json: String) {
        try {
            val result = mutableListOf<SemesterModel>()
            val yearsArray = JSONArray(json)

            for (i in 0 until yearsArray.length()) {
                val yearObj = yearsArray.getJSONObject(i)
                val semestersArray = yearObj.optJSONArray("semesters") ?: continue

                for (j in 0 until semestersArray.length()) {
                    val semObj = semestersArray.getJSONObject(j)
                    val semName = semObj.optJSONObject("semester")?.optString("name") ?: "Unknown Semester"
                    val subjectsArray = semObj.optJSONArray("subjects") ?: continue
                    
                    val subjectsList = mutableListOf<SubjectModel>()

                    for (k in 0 until subjectsArray.length()) {
                        val subObj = subjectsArray.getJSONObject(k)
                        
                        // Name might be in a nested object or direct
                        val name = subObj.optJSONObject("subject")?.optString("name") 
                            ?: subObj.optString("name", "Subject")
                        
                        val credit = subObj.optString("credit", "0")
                        
                        // Grade logic
                        val examRule = subObj.optJSONObject("exam_rule")
                        val grade = examRule?.let {
                            it.optString("literal").takeIf { s -> s.isNotEmpty() }
                            ?: it.optString("digital")
                        } ?: "-"

                        subjectsList.add(SubjectModel(name, credit, grade))
                    }
                    if (subjectsList.isNotEmpty()) {
                        result.add(SemesterModel(semName, subjectsList))
                    }
                }
            }
            semesters = result
        } catch (e: Exception) {
            log("Parse Error: ${e.message}")
        }
    }

    private fun getStudentIdFromToken(token: String): Long {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return 0L
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE))
            JSONObject(payload).optLong("sub", 0L)
        } catch (e: Exception) {
            0L
        }
    }
}