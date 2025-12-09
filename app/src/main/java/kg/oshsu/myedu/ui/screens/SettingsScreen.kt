package kg.oshsu.myedu.ui.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kg.oshsu.myedu.MainActivity
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.ui.components.InfoSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    
    val appVersion = remember {
        try {
            val pInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else { context.packageManager.getPackageInfo(context.packageName, 0) }
            pInfo.versionName
        } catch (e: Exception) { "Unknown" }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { 
                    IconButton(onClick = onClose) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") 
                    } 
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            
            // Appearance Section
            SettingsDropdown(
                label = "Appearance",
                options = listOf(
                    "Follow System" to "system", 
                    "Light Mode" to "light", 
                    "Dark Mode" to "dark"
                ),
                currentValue = vm.appTheme, 
                onOptionSelected = { vm.setTheme(it) }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Documents Download Section (Placeholder)
            SettingsDropdown(
                label = "Docs Download",
                options = listOf(
                    "In-App Viewer" to "IN_APP", 
                    "Official Website" to "WEBSITE"
                ),
                currentValue = vm.downloadMode, 
                onOptionSelected = { vm.setDocMode(it) }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Language Section
            SettingsDropdown(
                label = "Language",
                options = listOf("English" to "en", "Русский" to "ru", "Кыргызча" to "ky"),
                currentValue = vm.language,
                onOptionSelected = { selectedLang -> 
                    if (vm.language != selectedLang) { 
                        vm.setAppLanguage(selectedLang)
                        // Simple restart to apply language changes
                        (context as? MainActivity)?.recreate() 
                    } 
                }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Dictionary Tools Section
            InfoSection("Dictionary Tools")
            Card(
                modifier = Modifier.fillMaxWidth().clickable { vm.showDictionaryScreen = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Open Dictionary", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Access offline dictionary tools", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            // About Section
            InfoSection("About")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("MyEDU Student", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "Version $appVersion", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun SettingsDropdown(label: String, options: List<Pair<String, String>>, currentValue: String, onOptionSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var dropdownWidth by remember { mutableStateOf(0) }
    val displayValue = options.find { it.second == currentValue }?.first ?: options.firstOrNull()?.first ?: ""
    
    Column {
        InfoSection(label)
        Box(modifier = Modifier.fillMaxWidth().wrapContentHeight().onSizeChanged { size -> dropdownWidth = size.width }) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = displayValue, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                    Icon(imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DropdownMenu(
                expanded = expanded, 
                onDismissRequest = { expanded = false }, 
                modifier = Modifier.width(with(LocalDensity.current) { dropdownWidth.toDp() }).background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                options.forEach { (name, value) ->
                    DropdownMenuItem(
                        text = { Text(text = name, color = MaterialTheme.colorScheme.onSurface, fontWeight = if(value == currentValue) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { onOptionSelected(value); expanded = false }
                    )
                }
            }
        }
    }
}