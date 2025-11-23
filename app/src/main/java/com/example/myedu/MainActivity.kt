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
    private var cachedResourcesRu: PdfResources? = null
    private var cachedResourcesEn: PdfResources? = null
    
    // Reference Cache
    private var cachedRefStudentInfo: String? = null
    private var cachedLicenseJson: String? = null
    private var cachedUnivInfo: String? = null
    private var cachedRefLinkId: Long = 0
    private var cachedRefQrUrl: String = ""
    private var cachedRefResources: PdfResources? = null

    private val jsFetcher = JsResourceFetcher()
    private val refJsFetcher = ReferenceJsFetcher()

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

    // --- TRANSCRIPT FETCHING ---
    fun fetchTranscriptData() {
        if (tokenInput.isBlank()) { log("Error: Token is empty"); return }

        viewModelScope.launch {
            isBusy = true
            logs.clear()
            transcriptList.clear()
            log("--- Transcript Fetch ---")
            
            val token = tokenInput.removePrefix("Bearer ").trim()

            try {
                NetworkClient.cookieJar.setDebugCookies(token)
                NetworkClient.interceptor.authToken = token

                log("1. Resources (RU)...")
                cachedResourcesRu = jsFetcher.fetchResources({ log(it) }, "ru")

                log("2. Student Info...")
                val sId = getStudentIdFromToken(token)
                if (sId == 0L) throw Exception("Invalid Token")
                val infoRaw = withContext(Dispatchers.IO) { NetworkClient.api.getStudentInfo(sId).string() }
                
                val infoJson = JSONObject(infoRaw)
                val fullName = "${infoJson.optString("last_name")} ${infoJson.optString("name")} ${infoJson.optString("father_name")}".replace("null", "").trim()
                infoJson.put("fullName", fullName)
                
                val movementId = infoJson.optJSONObject("lastStudentMovement")?.optLong("id") ?: 0L
                cachedStudentId = sId; cachedInfoJson = infoJson.toString()
                log("Student: $fullName")

                log("3. Grades...")
                val transcriptRaw = withContext(Dispatchers.IO) { NetworkClient.api.getTranscriptData(sId, movementId).string() }
                cachedTranscriptJson = transcriptRaw
                
                parseAndDisplayTranscript(transcriptRaw)
                log("Transcript Ready.")

            } catch (e: Throwable) {
                log("Transcript Error: ${e.message}")
                e.printStackTrace()
            } finally { isBusy = false }
        }
    }

    // --- REFERENCE FETCHING ---
    fun fetchReferenceData() {
        if (tokenInput.isBlank()) { log("Error: Token is empty"); return }
        
        viewModelScope.launch {
            isBusy = true
            log("--- Reference Fetch ---")
            val token = tokenInput.removePrefix("Bearer ").trim()

            try {
                NetworkClient.cookieJar.setDebugCookies(token)
                NetworkClient.interceptor.authToken = token
                val sId = getStudentIdFromToken(token)
                if (sId == 0L) throw Exception("Invalid Token")
                cachedStudentId = sId

                log("1. Student Info...")
                val infoRaw = withContext(Dispatchers.IO) { NetworkClient.api.getStudentInfo(sId).string() }
                cachedRefStudentInfo = infoRaw
                val info = JSONObject(infoRaw)

                val lastMove = info.optJSONObject("lastStudentMovement")
                
                var specId = info.optJSONObject("speciality")?.optInt("id") ?: 0
                if (specId == 0) specId = lastMove?.optJSONObject("speciality")?.optInt("id") ?: 0
                if (specId == 0) specId = info.optInt("id_speciality")
                if (specId == 0) specId = lastMove?.optInt("id_speciality") ?: 0

                var eduFormId = lastMove?.optJSONObject("edu_form")?.optInt("id") ?: 0
                if (eduFormId == 0) eduFormId = info.optJSONObject("edu_form")?.optInt("id") ?: 0
                if (eduFormId == 0) eduFormId = info.optInt("id_edu_form")
                if (eduFormId == 0) eduFormId = lastMove?.optInt("id_edu_form") ?: 0

                if (specId == 0 || eduFormId == 0) throw Exception("IDs not found. Spec: $specId, Edu: $eduFormId")
                log("IDs found: Spec=$specId, Edu=$eduFormId")

                log("2. License Info...")
                val licRaw = withContext(Dispatchers.IO) { 
                    NetworkClient.api.getSpecialityLicense(specId, eduFormId).string() 
                }
                cachedLicenseJson = licRaw

                log("3. University Info...")
                val univRaw = withContext(Dispatchers.IO) {
                    NetworkClient.api.getUniversityInfo().string()
                }
                cachedUnivInfo = univRaw

                log("4. Link (Form 8)...")
                val linkRaw = withContext(Dispatchers.IO) { 
                    NetworkClient.api.getReferenceLink(DocIdRequest(cachedStudentId)).string() 
                }
                val linkJson = JSONObject(linkRaw)
                cachedRefLinkId = linkJson.optLong("id")
                cachedRefQrUrl = linkJson.optString("url")

                log("5. Resources...")
                cachedRefResources = refJsFetcher.fetchResources({ log(it) }, "ru")
                
                log("Reference Ready. Key: $cachedRefLinkId")

            } catch (e: Exception) {
                log("Ref Fetch Error: ${e.message}")
                e.printStackTrace()
            } finally {
                isBusy = false
            }
        }
    }

    fun generateReferencePdf(refGenerator: ReferencePdfGenerator, filesDir: File, onPdfReady: (File) -> Unit) {
        if (cachedRefStudentInfo == null || cachedLicenseJson == null || cachedRefResources == null || cachedUnivInfo == null) { 
            log("Ref Data missing."); return 
        }
        
        viewModelScope.launch {
            isBusy = true
            try {
                log("Generating Ref PDF...")
                val info = JSONObject(cachedRefStudentInfo!!)
                val fullName = "${info.optString("last_name")} ${info.optString("name")} ${info.optString("father_name")}".replace("null", "").trim()
                info.put("fullName", fullName)

                val bytes = refGenerator.generatePdf(
                    info.toString(), 
                    cachedLicenseJson!!, 
                    cachedUnivInfo!!,
                    cachedRefLinkId, 
                    cachedRefQrUrl, 
                    cachedRefResources!!, 
                    "ru" 
                ) { log(it) }

                val fileName = "reference_form8.pdf"
                val file = File(filesDir, fileName)
                withContext(Dispatchers.IO) { FileOutputStream(file).use { it.write(bytes) } }
                
                log("SAVED: $fileName")
                onPdfReady(file)
                
                uploadReferencePdf(file, cachedRefLinkId)
                
            } catch(e: Exception) {
                log("Ref Gen Error: ${e.message}")
                e.printStackTrace()
            } finally { isBusy = false }
        }
    }

    private suspend fun uploadReferencePdf(file: File, linkId: Long) {
        try {
            log("Uploading Ref...")
            val plainText = "text/plain".toMediaTypeOrNull()
            val idBody = linkId.toString().toRequestBody(plainText)
            val studentIdBody = cachedStudentId.toString().toRequestBody(plainText)
            val fileBody = file.asRequestBody("application/pdf".toMediaTypeOrNull())
            val pdfPart = MultipartBody.Part.createFormData("pdf", "transcript.pdf", fileBody)

            val response = withContext(Dispatchers.IO) {
                NetworkClient.api.uploadReferencePdf(idBody, studentIdBody, pdfPart).string()
            }
            log("UPLOAD SUCCESS: $response")
        } catch(e: Exception) {
            log("UPLOAD FAILED: ${e.message}")
        }
    }

    // --- TRANSCRIPT PDF ---
    fun generatePdf(webGenerator: WebPdfGenerator, filesDir: File, language: String, onPdfReady: (File) -> Unit) {
        if (cachedInfoJson == null) { log("Fetch Transcript Data first."); return }
        
        viewModelScope.launch {
            isBusy = true
            try {
                var resources = if (language == "en") cachedResourcesEn else cachedResourcesRu
                if (resources == null) {
                    log("Fetching $language resources...")
                    resources = jsFetcher.fetchResources({ log(it) }, language)
                    if (language == "en") cachedResourcesEn = resources else cachedResourcesRu = resources
                }

                log("Getting New Key...")
                val linkRaw = withContext(Dispatchers.IO) { NetworkClient.api.getTranscriptLink(DocIdRequest(cachedStudentId)).string() }
                val json = JSONObject(linkRaw)
                val linkId = json.optLong("id")
                val qrUrl = json.optString("url")

                log("Generating PDF ($language)...")
                val bytes = webGenerator.generatePdf(cachedInfoJson!!, cachedTranscriptJson!!, linkId, qrUrl, resources!!, language) { log(it) }

                val fileName = "transcript_$language.pdf"
                val file = File(filesDir, fileName)
                withContext(Dispatchers.IO) { FileOutputStream(file).use { it.write(bytes) } }
                
                log("SAVED: $fileName")
                onPdfReady(file)

                uploadGeneratedPdf(file, linkId)

            } catch (e: Throwable) { 
                log("PDF Error: ${e.message}") 
                e.printStackTrace()
            }
            finally { isBusy = false }
        }
    }

    private suspend fun uploadGeneratedPdf(file: File, linkId: Long) {
        try {
            log("Uploading Transcript...")
            val plainText = "text/plain".toMediaTypeOrNull()
            val idBody = linkId.toString().toRequestBody(plainText)
            val studentIdBody = cachedStudentId.toString().toRequestBody(plainText)
            val fileBody = file.asRequestBody("application/pdf".toMediaTypeOrNull())
            val pdfPart = MultipartBody.Part.createFormData("pdf", file.name, fileBody)

            val response = withContext(Dispatchers.IO) {
                NetworkClient.api.uploadPdf(idBody, studentIdBody, pdfPart).string()
            }
            log("UPLOAD SUCCESS: $response")
        } catch(e: Exception) {
            log("UPLOAD FAILED: ${e.message}")
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
                        val name = sub.optString("subject", "?")
                        val cr = sub.optString("credit", "0")
                        val mark = sub.optJSONObject("mark_list")
                        val rule = sub.optJSONObject("exam_rule")
                        val total = mark?.optString("finally")?.takeIf { it != "0" && it != "null" } ?: mark?.optString("total") ?: "-"
                        val grade = rule?.optString("alphabetic") ?: "-"
                        items.add(TranscriptItem(name, cr, total, grade))
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
            val refGenerator = ReferencePdfGenerator(this)
            setContent {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        MainScreen(webGenerator, refGenerator, filesDir)
                    }
                }
            }
        } catch (e: Throwable) { e.printStackTrace() }
    }
}

@Composable
fun MainScreen(webGenerator: WebPdfGenerator, refGenerator: ReferencePdfGenerator, filesDir: File) {
    val viewModel: MainViewModel = viewModel()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val state = rememberLazyListState()
    LaunchedEffect(viewModel.logs.size) { if(viewModel.logs.isNotEmpty()) state.animateScrollToItem(viewModel.logs.size - 1) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(value = viewModel.tokenInput, onValueChange = { viewModel.tokenInput = it }, label = { Text("Token") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { clipboard.getText()?.text?.let { viewModel.tokenInput = it } }, Modifier.fillMaxWidth().padding(vertical=8.dp)) { Text("Paste Token") }
        
        // Transcript Section
        Text("Transcript", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.fetchTranscriptData() }, Modifier.weight(1f)) { Text("1. Fetch Data") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.generatePdf(webGenerator, filesDir, "ru") {} }, Modifier.weight(1f)) { Text("PDF (RU)") }
            Button(onClick = { viewModel.generatePdf(webGenerator, filesDir, "en") {} }, Modifier.weight(1f)) { Text("PDF (EN)") }
        }
        
        Divider(Modifier.padding(vertical=8.dp))
        
        // Reference Section
        Text("Reference (Form 8)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.fetchReferenceData() }, Modifier.weight(1f)) { Text("1. Fetch Ref Files") }
            Button(onClick = { viewModel.generateReferencePdf(refGenerator, filesDir) {} }, Modifier.weight(1f)) { Text("2. Ref PDF") }
        }

        Divider(Modifier.padding(vertical=8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(viewModel.transcriptList) { t -> 
                Row {
                    Text(t.subject, Modifier.weight(1f), fontSize = 12.sp)
                    Text(t.credit, Modifier.width(30.dp), fontSize = 12.sp)
                    Text(t.total, Modifier.width(30.dp), fontSize = 12.sp)
                    Text(t.grade, Modifier.width(30.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
                Divider(color=Color.LightGray, thickness=0.5.dp)
            }
        }
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Console", fontWeight = FontWeight.Bold)
            Button(onClick = {
                clipboard.setText(AnnotatedString(viewModel.logs.joinToString("\n")))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }) { Text("Copy Logs") }
        }
        Box(Modifier.height(150.dp).fillMaxWidth().background(Color(0xFF1E1E1E)).padding(4.dp)) {
            LazyColumn(state = state) { items(viewModel.logs) { Text("> $it", color = Color.Green, fontSize = 10.sp) } }
        }
    }
}