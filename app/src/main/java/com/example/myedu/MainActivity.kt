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
        
        Text("Transcript", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.fetchTranscriptData() }, Modifier.weight(1f)) { Text("1. Fetch Data") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.generatePdf(webGenerator, filesDir, "ru") {} }, Modifier.weight(1f)) { Text("PDF (RU)") }
            Button(onClick = { viewModel.generatePdf(webGenerator, filesDir, "en") {} }, Modifier.weight(1f)) { Text("PDF (EN)") }
        }
        
        Divider(Modifier.padding(vertical=8.dp))
        
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