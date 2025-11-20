package com.example.myedu

import android.os.Bundle
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import org.jsoup.Jsoup

data class StudentGrade(
    val subjectRu: String,
    val grade: String,
    val credits: String
) {
    val subjectEn: String
        get() = when {
            subjectRu.contains("Биология", true) -> "Biology"
            subjectRu.contains("Химия", true) -> "Chemistry"
            subjectRu.contains("Анатомия", true) -> "Anatomy"
            else -> subjectRu
        }
}

class MainViewModel : ViewModel() {
    var isLoading by mutableStateOf(false)
    var isLoggedIn by mutableStateOf(false)
    var errorMsg by mutableStateOf<String?>(null)
    var grades by mutableStateOf<List<StudentGrade>>(emptyList())

    fun login(user: String, pass: String) {
        viewModelScope.launch {
            isLoading = true
            errorMsg = null
            try {
                val body = FormBody.Builder()
                    .add("login", user)
                    .add("password", pass)
                    .build()
                val html = NetworkClient.api.login(body)
                
                // Adjust this check based on what the site returns on success
                if (html.contains("Logout") || html.contains("Выход")) {
                    isLoggedIn = true
                    fetchGrades()
                } else {
                    errorMsg = "Login Failed (Check Credentials)"
                }
            } catch (e: Exception) {
                errorMsg = e.message
            } finally {
                isLoading = false
            }
        }
    }

    private fun fetchGrades() {
        viewModelScope.launch {
            try {
                val html = NetworkClient.api.getGradesPage()
                val parsed = withContext(Dispatchers.Default) {
                    val doc = Jsoup.parse(html)
                    val list = mutableListOf<StudentGrade>()
                    // IMPORTANT: Update selector based on Inspect Element
                    val rows = doc.select("table tbody tr") 
                    for (row in rows) {
                        val cols = row.select("td")
                        if (cols.size >= 3) {
                            list.add(StudentGrade(cols[0].text(), cols[2].text(), cols[1].text()))
                        }
                    }
                    list
                }
                grades = parsed
            } catch (e: Exception) {
                errorMsg = "Failed to load grades"
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { OshSuApp() } }
    }
}

@Composable
fun OshSuApp(vm: MainViewModel = viewModel()) {
    if (!vm.isLoggedIn) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
            Text("MyEDU Login", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            var u by remember { mutableStateOf("") }
            var p by remember { mutableStateOf("") }
            OutlinedTextField(value = u, onValueChange = { u = it }, label = { Text("Login") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = p, onValueChange = { p = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
            if (vm.errorMsg != null) Text(vm.errorMsg!!, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(24.dp))
            Button(onClick = { vm.login(u, p) }, enabled = !vm.isLoading, modifier = Modifier.fillMaxWidth()) {
                Text(if (vm.isLoading) "Loading..." else "Login")
            }
        }
    } else {
        LazyColumn(Modifier.padding(16.dp)) {
            item { Text("Grades", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom=16.dp)) }
            items(vm.grades) { g ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(g.subjectEn, style = MaterialTheme.typography.titleMedium)
                            Text(g.subjectRu, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(g.grade, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
    }
}
