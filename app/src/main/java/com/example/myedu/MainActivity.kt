package com.example.myedu

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- VIEWMODEL (Fetching & Storing Real Data) ---
class MainViewModel : ViewModel() {
    var appState by mutableStateOf("LOGIN")
    var isLoading by mutableStateOf(false)
    var errorMsg by mutableStateOf<String?>(null)
    
    // DATA HOLDERS
    var userData by mutableStateOf<UserData?>(null)
    var profileData by mutableStateOf<StudentInfoResponse?>(null)

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            isLoading = true
            errorMsg = null
            try {
                // 1. Login to get Token
                val loginResp = withContext(Dispatchers.IO) {
                    NetworkClient.api.login(LoginRequest(email, pass))
                }
                val token = loginResp.authorisation?.token
                
                if (token != null) {
                    NetworkClient.interceptor.authToken = token
                    
                    // 2. Fetch Basic User Data (Name, Email)
                    val userResp = withContext(Dispatchers.IO) { NetworkClient.api.getUser() }
                    userData = userResp.user
                    
                    // 3. Fetch Detailed Profile (Passport, Faculty, etc)
                    val profileResp = withContext(Dispatchers.IO) { NetworkClient.api.getProfile() }
                    profileData = profileResp
                    
                    appState = "PROFILE"
                } else {
                    errorMsg = "Login rejected (No token)"
                }
            } catch (e: Exception) {
                errorMsg = if(e.message?.contains("401") == true) "Access Denied" else "Error: ${e.message}"
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }
    
    fun logout() {
        appState = "LOGIN"
        userData = null
        profileData = null
    }
}

// --- THEME & SETUP ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { MyEduTheme { AppContent() } }
    }
}

@Composable
fun MyEduTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) darkColorScheme() else lightColorScheme()
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
fun AppContent(vm: MainViewModel = viewModel()) {
    AnimatedContent(
        targetState = vm.appState,
        transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(500)) },
        label = "Nav"
    ) { state ->
        if (state == "LOGIN") LoginScreen(vm) else ProfileScreen(vm)
    }
}

// --- SCREEN 1: LOGIN ---
@Composable
fun LoginScreen(vm: MainViewModel) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).systemBarsPadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.School, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            Text("MyEDU Portal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(48.dp))

            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = pass, onValueChange = { pass = it },
                label = { Text("Password") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
            )

            if (vm.errorMsg != null) {
                Spacer(Modifier.height(16.dp))
                Text(vm.errorMsg!!, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { vm.login(email, pass) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !vm.isLoading
            ) {
                if (vm.isLoading) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                else Text("Sign In")
            }
        }
    }
}

// --- SCREEN 2: DYNAMIC PROFILE ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: MainViewModel) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val user = vm.userData
    val profile = vm.profileData
    
    // Extracting Real Data
    val fullName = "${user?.last_name ?: ""} ${user?.name ?: ""}".trim().ifEmpty { "Student" }
    val avatarUrl = profile?.avatar
    val groupName = profile?.studentMovement?.avn_group_name ?: "No Group"
    
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Profile") },
                actions = { IconButton(onClick = { vm.logout() }) { Icon(Icons.Outlined.Logout, null) } },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Header Card (Dynamic Data)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(130.dp)
                            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)), CircleShape)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    ) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(fullName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(user?.email ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    SuggestionChip(onClick = {}, label = { Text("ID: ${user?.id ?: "Unknown"}") })
                }
            }

            Spacer(Modifier.height(24.dp))
            
            // 2. Dynamic Info Cards
            SectionHeader("Academic")
            DetailCard(Icons.Outlined.Groups, "Group", groupName)
            DetailCard(Icons.Outlined.School, "Faculty", profile?.studentMovement?.faculty?.get() ?: "-")
            DetailCard(Icons.Outlined.Book, "Speciality", profile?.studentMovement?.speciality?.get() ?: "-")
            DetailCard(Icons.Outlined.CastForEducation, "Form", profile?.studentMovement?.edu_form?.get() ?: "-")

            Spacer(Modifier.height(24.dp))
            SectionHeader("Personal")
            DetailCard(Icons.Outlined.Badge, "Passport", profile?.pdsstudentinfo?.passport_number ?: "-")
            DetailCard(Icons.Outlined.Cake, "Birthday", profile?.pdsstudentinfo?.birthday ?: "-")
            DetailCard(Icons.Outlined.Phone, "Phone", profile?.pdsstudentinfo?.phone ?: "-")
            DetailCard(Icons.Outlined.Home, "Address", profile?.pdsstudentinfo?.address ?: "-")
            
            Spacer(Modifier.height(24.dp))
            SectionHeader("Parents")
            DetailCard(Icons.Outlined.Person, "Father", profile?.pdsstudentinfo?.father_full_name ?: "-")
            DetailCard(Icons.Outlined.Person2, "Mother", profile?.pdsstudentinfo?.mother_full_name ?: "-")
            
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp, start = 8.dp)
    )
}

@Composable
fun DetailCard(icon: ImageVector, title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = null
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
