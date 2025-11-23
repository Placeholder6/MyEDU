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
        } catch (e: Exception) { return 0L }
    }

    // --- TRANSCRIPT ---
    fun fetchTranscriptData() {
        if (tokenInput.isBlank()) return
        viewModelScope.launch {
            isBusy = true
            logs.clear()
            transcriptList.clear()
            try {
                val token = tokenInput.removePrefix("Bearer ").trim()
                NetworkClient.cookieJar.setDebugCookies(token)
                NetworkClient.interceptor.authToken = token
                
                log("1. Resources...")
                cachedResourcesRu = jsFetcher.fetchResources({ log(it) }, "ru")

                log("2. Student Info...")
                val sId = getStudentIdFromToken(token)
                val infoRaw = withContext(Dispatchers.IO) { NetworkClient.api.getStudentInfo(sId).string() }
                val infoJson = JSONObject(infoRaw)
                val fullName = "${infoJson.optString("last_name")} ${infoJson.optString("name")} ${infoJson.optString("father_name")}".replace("null", "").trim()
                infoJson.put("fullName", fullName)
                
                val movementId = infoJson.optJSONObject("lastStudentMovement")?.optLong("id") ?: 0L
                cachedStudentId = sId
                cachedInfoJson = infoJson.toString()

                log("3. Grades...")
                val transcriptRaw = withContext(Dispatchers.IO) { NetworkClient.api.getTranscriptData(sId, movementId).string() }
                cachedTranscriptJson = transcriptRaw
                parseAndDisplayTranscript(transcriptRaw)
                log("Done.")
            } catch (e: Exception) { log("Error: ${e.message}") }
            finally { isBusy = false }
        }
    }

    fun generatePdf(webGenerator: WebPdfGenerator, filesDir: File, language: String) {
        if (cachedInfoJson == null) { log("Fetch data first."); return }
        viewModelScope.launch {
            isBusy = true
            try {
                var resources = if (language == "en") cachedResourcesEn else cachedResourcesRu
                if (resources == null) {
                    resources = jsFetcher.fetchResources({ log(it) }, language)
                    if (language == "en") cachedResourcesEn = resources else cachedResourcesRu = resources
                }
                val linkRaw = withContext(Dispatchers.IO) { NetworkClient.api.getTranscriptLink(DocIdRequest(cachedStudentId)).string() }
                val json = JSONObject(linkRaw)
                val bytes = webGenerator.generatePdf(cachedInfoJson!!, cachedTranscriptJson!!, json.optLong("id"), json.optString("url"), resources!!, language) { log(it) }
                val file = File(filesDir, "transcript_$language.pdf")
                withContext(Dispatchers.IO) { FileOutputStream(file).use { it.write(bytes) } }
                log("Saved: ${file.name}")
                // Upload omitted for brevity
            } catch (e: Exception) { log("PDF Error: ${e.message}") }
            finally { isBusy = false }
        }
    }

    // --- REFERENCE ---
    fun fetchReferenceData() {
        if (tokenInput.isBlank()) return
        viewModelScope.launch {
            isBusy = true
            log("--- Ref Fetch ---")
            try {
                val token = tokenInput.removePrefix("Bearer ").trim()
                NetworkClient.cookieJar.setDebugCookies(token)
                NetworkClient.interceptor.authToken = token
                val sId = getStudentIdFromToken(token)
                cachedStudentId = sId

                log("1. Student Info...")
                val infoRaw = withContext(Dispatchers.IO) { NetworkClient.api.getStudentInfo(sId).string() }
                cachedRefStudentInfo = infoRaw
                val info = JSONObject(infoRaw)
                val lastMove = info.optJSONObject("lastStudentMovement")
                
                var specId = info.optInt("id_speciality")
                if(specId == 0) specId = lastMove?.optJSONObject("speciality")?.optInt("id") ?: 0
                
                var eduFormId = info.optInt("id_edu_form")
                if(eduFormId == 0) eduFormId = lastMove?.optJSONObject("edu_form")?.optInt("id") ?: 0
                
                log("IDs: Spec=$specId, Edu=$eduFormId")

                log("2. License & Univ...")
                val licRaw = withContext(Dispatchers.IO) { NetworkClient.api.getSpecialityLicense(specId, eduFormId).string() }
                cachedLicenseJson = licRaw
                cachedUnivInfo = withContext(Dispatchers.IO) { NetworkClient.api.getUniversityInfo().string() }

                log("3. Link...")
                val linkRaw = withContext(Dispatchers.IO) { NetworkClient.api.getReferenceLink(DocIdRequest(sId)).string() }
                val linkJson = JSONObject(linkRaw)
                cachedRefLinkId = linkJson.optLong("id")
                cachedRefQrUrl = linkJson.optString("url")

                log("4. Resources...")
                cachedRefResources = refJsFetcher.fetchResources({ log(it) }, "ru")
                log("Ready.")
            } catch (e: Exception) { log("Ref Error: ${e.message}") }
            finally { isBusy = false }
        }
    }

    fun generateReferencePdf(refGenerator: ReferencePdfGenerator, filesDir: File) {
        if (cachedRefStudentInfo == null || cachedLicenseJson == null || cachedUnivInfo == null) {
            log("Fetch Ref Data first.")
            return
        }
        viewModelScope.launch {
            isBusy = true
            try {
                val info = JSONObject(cachedRefStudentInfo!!)
                val fullName = "${info.optString("last_name")} ${info.optString("name")} ${info.optString("father_name")}".replace("null", "").trim()
                info.put("fullName", fullName)
                
                log("Gen Ref PDF...")
                val bytes = refGenerator.generatePdf(info.toString(), cachedLicenseJson!!, cachedUnivInfo!!, cachedRefLinkId, cachedRefQrUrl, cachedRefResources!!, "ru") { log(it) }
                val file = File(filesDir, "reference_form8.pdf")
                withContext(Dispatchers.IO) { FileOutputStream(file).use { it.write(bytes) } }
                log("Saved: ${file.name}")
                
                // Upload
                val body = MultipartBody.Part.createFormData("pdf", "transcript.pdf", file.asRequestBody("application/pdf".toMediaTypeOrNull()))
                val res = withContext(Dispatchers.IO) { 
                    NetworkClient.api.uploadReferencePdf(cachedRefLinkId.toString().toRequestBody(null), cachedStudentId.toString().toRequestBody(null), body).string() 
                }
                log("Uploaded: $res")
            } catch (e: Exception) { log("Gen Error: ${e.message}") }
            finally { isBusy = false }
        }
    }

    private fun parseAndDisplayTranscript(jsonString: String) {
        // ... (Same as previous logic)
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
                        val total = mark?.optString("finally")?.takeIf { it != "0" && it != "null" } ?: mark?.optString("total") ?: "-"
                        val grade = sub.optJSONObject("exam_rule")?.optString("alphabetic") ?: "-"
                        items.add(TranscriptItem(name, cr, total, grade))
                    }
                }
            }
            transcriptList.addAll(items)
        } catch(e:Exception){}
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webGen = WebPdfGenerator(this)
        val refGen = ReferencePdfGenerator(this)
        setContent {
            MaterialTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainScreen(webGen, refGen, filesDir) } }
        }
    }
}

@Composable
fun MainScreen(webGenerator: WebPdfGenerator, refGenerator: ReferencePdfGenerator, filesDir: File) {
    val viewModel: MainViewModel = viewModel()
    val clipboard = LocalClipboardManager.current
    val state = rememberLazyListState()
    LaunchedEffect(viewModel.logs.size) { if(viewModel.logs.isNotEmpty()) state.animateScrollToItem(viewModel.logs.size-1) }

    Column(Modifier.padding(16.dp).fillMaxSize()) {
        OutlinedTextField(viewModel.tokenInput, { viewModel.tokenInput = it }, label={Text("Token")}, modifier=Modifier.fillMaxWidth())
        Button({ clipboard.getText()?.text?.let { viewModel.tokenInput = it } }, Modifier.fillMaxWidth().padding(vertical=4.dp)) { Text("Paste") }
        
        Text("Transcript", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ viewModel.fetchTranscriptData() }, Modifier.weight(1f)) { Text("Fetch") }
            Button({ viewModel.generatePdf(webGenerator, filesDir, "en") }, Modifier.weight(1f)) { Text("PDF") }
        }
        
        Text("Reference", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top=8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ viewModel.fetchReferenceData() }, Modifier.weight(1f)) { Text("Fetch") }
            Button({ viewModel.generateReferencePdf(refGenerator, filesDir) }, Modifier.weight(1f)) { Text("PDF") }
        }
        
        Divider(Modifier.padding(vertical=8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(viewModel.transcriptList) { 
                Row { Text(it.subject, Modifier.weight(1f), fontSize=12.sp); Text(it.grade, fontWeight=FontWeight.Bold, fontSize=12.sp) }
                Divider()
            }
        }
        Box(Modifier.height(120.dp).fillMaxWidth().background(Color.Black)) {
            LazyColumn(state=state) { items(viewModel.logs) { Text(">$it", color=Color.Green, fontSize=10.sp) } }
        }
    }
}