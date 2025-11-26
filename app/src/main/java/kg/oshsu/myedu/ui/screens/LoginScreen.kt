package kg.oshsu.myedu.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.ui.components.OshSuLogo

@Composable
fun LoginScreen(vm: MainViewModel) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.fillMaxSize().widthIn(max = 600.dp).padding(24.dp).systemBarsPadding(), 
                verticalArrangement = Arrangement.Center, 
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OshSuLogo(modifier = Modifier.width(260.dp).height(100.dp))
                Spacer(Modifier.height(48.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                if (vm.errorMsg != null) { Spacer(Modifier.height(16.dp)); Text(vm.errorMsg!!, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(32.dp))
                Button(onClick = { vm.login(email, pass) }, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = !vm.isLoading) { if (vm.isLoading) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary) else Text("Sign In") }
            }
        }
    }
}