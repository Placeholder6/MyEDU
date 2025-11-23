package com.example.myedu

import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
    // Default Dictionary URL
    var dictionaryUrl by mutableStateOf("https://gist.githubusercontent.com/Placeholder6/71c6a6638faf26c7858d55a1e73b7aef/raw/myedudictionary.json")
    
    var transcriptList = mutableStateListOf<TranscriptItem>()
    var logs = mutableStateListOf<String>()
    var isBusy by mutableStateOf(false)

    private var cachedStudentId: Long = 0
    private var cachedInfoJson: String? = null
    private var cachedTranscriptJson: String? = null
    private var cachedRefStudentInfo: String? = null
    private var cachedLicenseJson: String? = null
    private var cachedUnivInfo: String? = null
    private var cachedRefLinkId: Long = 0
    private var cachedRefQrUrl: String = ""
    
    private var cachedResourcesRu: PdfResources? = null
    private var cachedResourcesEn: PdfResources? = null
    private var cachedRefResourcesRu: PdfResources? = null
    private var cachedRefResourcesEn: PdfResources? = null
    
    private var cachedDictionary: Map<String, String> = emptyMap()

    private val jsFetcher = JsResourceFetcher()
    private val refJsFetcher = ReferenceJsFetcher()
    private val dictUtils = DictionaryUtils() // Assumes DictionaryUtils exists

    fun log(msg: String) {
        viewModelScope.launch(Dispatchers.Main) { logs.add(msg) }
    }

    private fun getStudentIdFromToken(token: String): Long {
        try {
            val parts = token.split(".")
            if (parts.size < 2) return 0L
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE))
            return JSONObject(payload).optLong("sub", 0L)
        } catch (e: Exception) { return 0L }
    }

    private suspend fun fetchDictionaryIfNeeded() {
        if (cachedDictionary.isEmpty() && dictionaryUrl.isNotBlank()) {
            log("Fetching Dictionary...")
            cachedDictionary = dictUtils.fetchDictionary(dictionaryUrl)
            log("Dictionary loaded: ${cachedDictionary.size} entries")
        }
    }

    // --- TRANSCRIPT ---
    fun fetchTranscriptData() {
        if (tokenInput.isBlank()) { log("Error: Token is empty"); return }
        viewModelScope.launch {
            isBusy = true
            logs.clear()
            transcriptList.clear()
            try {
                fetchDictionaryIfNeeded()
                val token = tokenInput.removePrefix("Bearer ").trim()
                NetworkClient.cookieJar.setDebugCookies(token)
                NetworkClient.interceptor.authToken = token

                // Clear cache to force refresh with new dictionary if needed
                cachedResourcesRu = null
                cachedResourcesEn = null
                
                val sId = getStudentIdFromToken(token)
                if (sId == 0L) throw Exception("Invalid Token")
                cachedStudentId = sId

                log("1. Info...")
                val infoRaw = withContext(Dispatchers.IO) { NetworkClient.api.getStudentInfo(sId).string() }
                val infoJson = JSONObject(infoRaw)
                val fullName = "${infoJson.optString("last_name")} ${infoJson.optString("name")} ${infoJson.optString("father_name")}".replace("null", "").trim()
                infoJson.put("fullName", fullName)
                cachedInfoJson = infoJson.toString()
                
                val movementId = infoJson.optJSONObject("lastStudentMovement")?.optLong("id") ?: 0L
                log("2. Transcript...")
                val transcriptRaw = withContext(Dispatchers.IO) { NetworkClient.api.getTranscriptData(sId, movementId).string() }
                cachedTranscriptJson = transcriptRaw
                
                parseAndDisplayTranscript(transcriptRaw)
                log("Ready.")
            } catch (e: Throwable) {
                log("Error: ${e.message}")
                e.printStackTrace()
            } finally { isBusy = false }
        }
    }

    // --- REFERENCE ---
    fun fetchReferenceData() {
        if (tokenInput.isBlank()) { log("Error: Token is empty"); return }
        viewModelScope.launch {
            isBusy = true
            try {
                fetchDictionaryIfNeeded()
                val token = tokenInput.removePrefix("Bearer ").trim()
                NetworkClient.cookieJar.setDebugCookies(token)
                NetworkClient.interceptor.authToken = token
                cachedStudentId = getStudentIdFromToken(token)

                log("1. Reference Data...")
                cachedRefResourcesRu = null
                cachedRefResourcesEn = null

                val infoRaw = withContext(Dispatchers.IO) { NetworkClient.api.getStudentInfo(cachedStudentId).string() }
                cachedRefStudentInfo = infoRaw
                
                val info = JSONObject(infoRaw)
                var specId = info.optJSONObject("speciality")?.optInt("id") ?: 0
                if (specId == 0) specId = info.optJSONObject("lastStudentMovement")?.optJSONObject("speciality")?.optInt("id") ?: 0
                var eduFormId = info.optJSONObject("lastStudentMovement")?.optJSONObject("edu_form")?.optInt("id") ?: 0
                if (eduFormId == 0) eduFormId = info.optJSONObject("edu_form")?.optInt("id") ?: 0

                cachedLicenseJson = withContext(Dispatchers.IO) { NetworkClient.api.getSpecialityLicense(specId, eduFormId).string() }
                cachedUnivInfo = withContext(Dispatchers.IO) { NetworkClient.api.getUniversityInfo().string() }
                
                val linkRaw = withContext(Dispatchers.IO) { NetworkClient.api.getReferenceLink(DocIdRequest(cachedStudentId)).string() }
                val linkJson = JSONObject(linkRaw)
                cachedRefLinkId = linkJson.optLong("id")
                cachedRefQrUrl = linkJson.optString("url")
                
                log("Ref Data Ready.")
            } catch (e: Exception) {
                log("Ref Error: ${e.message}")
                e.printStackTrace()
            } finally { isBusy = false }
        }
    }

    fun generateReferencePdf(refGenerator: ReferencePdfGenerator, filesDir: File, language: String, onPdfReady: (File) -> Unit) {
        if (cachedRefStudentInfo == null) { log("Ref Data missing."); return }
        viewModelScope.launch {
            isBusy = true
            try {
                var resources = if (language == "en") cachedRefResourcesEn else cachedRefResourcesRu
                if (resources == null) {
                    log("Fetching $language resources...")
                    resources = refJsFetcher.fetchResources({ log(it) }, language, cachedDictionary)
                    if (language == "en") cachedRefResourcesEn = resources else cachedRefResourcesRu = resources
                }

                val info = JSONObject(cachedRefStudentInfo!!)
                val fullName = "${info.optString("last_name")} ${info.optString("name")} ${info.optString("father_name")}".replace("null", "").trim()
                info.put("fullName", fullName)

                val bytes = refGenerator.generatePdf(
                    info.toString(), cachedLicenseJson!!, cachedUnivInfo!!, cachedRefLinkId, cachedRefQrUrl, resources!!, language, cachedDictionary
                ) { log(it) }

                val fileName = "reference_${language}.pdf"
                val file = File(filesDir, fileName)
                withContext(Dispatchers.IO) { FileOutputStream(file).use { it.write(bytes) } }
                log("SAVED: $fileName")
                onPdfReady(file)
                uploadPdf(file, cachedRefLinkId, "ref")
            } catch(e: Exception) {
                log("Gen Error: ${e.message}")
                e.printStackTrace()
            } finally { isBusy = false }
        }
    }

    fun generateTranscriptPdf(webGenerator: WebPdfGenerator, filesDir: File, language: String, onPdfReady: (File) -> Unit) {
        if (cachedInfoJson == null) { log("Transcript Data missing."); return }
        viewModelScope.launch {
            isBusy = true
            try {
                var resources = if (language == "en") cachedResourcesEn else cachedResourcesRu
                if (resources == null) {
                    log("Fetching $language resources...")
                    // IMPORTANT: Assuming JsResourceFetcher has been updated similarly to ReferenceJsFetcher to accept dictionary.
                    // If not, you must update JsResourceFetcher.fetchResources signature.
                    // For now, we pass the dictionary assuming you updated it or will update it.
                    // resources = jsFetcher.fetchResources({ log(it) }, language, cachedDictionary) 
                    // Fallback to simple call if you haven't updated JsResourceFetcher signature yet:
                    resources = jsFetcher.fetchResources({ log(it) }, language) 
                    
                    if (language == "en") cachedResourcesEn = resources else cachedResourcesRu = resources
                }

                val linkRaw = withContext(Dispatchers.IO) { NetworkClient.api.getTranscriptLink(DocIdRequest(cachedStudentId)).string() }
                val json = JSONObject(linkRaw)
                val linkId = json.optLong("id")
                val qrUrl = json.optString("url")

                val bytes = webGenerator.generatePdf(
                    cachedInfoJson!!, cachedTranscriptJson!!, linkId, qrUrl, resources!!, language, cachedDictionary
                ) { log(it) }

                val fileName = "transcript_${language}.pdf"
                val file = File(filesDir, fileName)
                withContext(Dispatchers.IO) { FileOutputStream(file).use { it.write(bytes) } }
                log("SAVED: $fileName")
                onPdfReady(file)
                uploadPdf(file, linkId, "transcript")
            } catch (e: Throwable) {
                log("Gen Error: ${e.message}")
                e.printStackTrace()
            } finally { isBusy = false }
        }
    }

    private suspend fun uploadPdf(file: File, linkId: Long, type: String) {
        try {
            log("Uploading $type...")
            val plainText = "text/plain".toMediaTypeOrNull()
            val idBody = linkId.toString().toRequestBody(plainText)
            val studentIdBody = cachedStudentId.toString().toRequestBody(plainText)
            val fileBody = file.asRequestBody("application/pdf".toMediaTypeOrNull())
            val pdfPart = MultipartBody.Part.createFormData("pdf", file.name, fileBody)

            val response = withContext(Dispatchers.IO) {
                if (type == "ref") NetworkClient.api.uploadReferencePdf(idBody, studentIdBody, pdfPart).string()
                else NetworkClient.api.uploadPdf(idBody, studentIdBody, pdfPart).string()
            }
            log("UPLOAD SUCCESS: $response")
        } catch(e: Exception) { log("UPLOAD FAILED: ${e.message}") }
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
                        items.add(TranscriptItem(
                            sub.optString("subject", "?"),
                            sub.optString("credit", "0"),
                            sub.optJSONObject("mark_list")?.optString("finally") ?: "-",
                            sub.optJSONObject("exam_rule")?.optString("alphabetic") ?: "-"
                        ))
                    }
                }
            }
            transcriptList.addAll(items)
        } catch (e: Exception) { log("Parse Error") }
    }
}

@Composable
fun MainScreen(webGenerator: WebPdfGenerator, refGenerator: ReferencePdfGenerator, filesDir: File) {
    val viewModel: MainViewModel = viewModel()
    val clipboard = LocalClipboardManager.current
    val state = rememberLazyListState()
    LaunchedEffect(viewModel.logs.size) { if(viewModel.logs.isNotEmpty()) state.animateScrollToItem(viewModel.logs.size - 1) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(value = viewModel.dictionaryUrl, onValueChange = { viewModel.dictionaryUrl = it }, label = { Text("Dictionary URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = viewModel.tokenInput, onValueChange = { viewModel.tokenInput = it }, label = { Text("Token") }, modifier = Modifier.fillMaxWidth().padding(top=8.dp))
        Button(onClick = { clipboard.getText()?.text?.let { viewModel.tokenInput = it } }, Modifier.fillMaxWidth().padding(vertical=8.dp)) { Text("Paste Token") }
        
        Text("Transcript", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.fetchTranscriptData() }, Modifier.weight(1f)) { Text("Fetch Data") }
            Button(onClick = { viewModel.generateTranscriptPdf(webGenerator, filesDir, "en") {} }, Modifier.weight(1f)) { Text("PDF (EN)") }
        }
        
        Divider(Modifier.padding(vertical=8.dp))
        Text("Reference", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.fetchReferenceData() }, Modifier.weight(1f)) { Text("Fetch Data") }
            Button(onClick = { viewModel.generateReferencePdf(refGenerator, filesDir, "en") {} }, Modifier.weight(1f)) { Text("PDF (EN)") }
        }

        Divider(Modifier.padding(vertical=8.dp))
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
            LazyColumn(state = state, modifier = Modifier.padding(8.dp)) { 
                items(viewModel.logs) { Text("> $it", color = Color.Green, fontSize = 12.sp) } 
            }
        }
    }
}