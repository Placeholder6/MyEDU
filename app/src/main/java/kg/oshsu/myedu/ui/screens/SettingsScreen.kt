package kg.oshsu.myedu.ui.screens

import android.app.Activity
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kg.oshsu.myedu.MainActivity
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val appVersion = remember {
        try {
            val pInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else { context.packageManager.getPackageInfo(context.packageName, 0) }
            pInfo.versionName
        } catch (e: Exception) { "Unknown" }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.settings), 
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.desc_back))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors( // Updated to non-deprecated
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
            // --- SECTION: APPEARANCE ---
            item {
                SettingsSectionTitle(stringResource(R.string.appearance), Icons.Outlined.Palette)
                SettingsGroup {
                    Text(
                        stringResource(R.string.app_theme_title), 
                        style = MaterialTheme.typography.labelLarge, 
                        modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val options = listOf("system", "light", "dark")
                        val icons = listOf(Icons.Filled.BrightnessAuto, Icons.Filled.LightMode, Icons.Filled.DarkMode)
                        val labels = listOf(
                            stringResource(R.string.follow_system),
                            stringResource(R.string.light_mode),
                            stringResource(R.string.dark_mode)
                        )
                        
                        options.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = vm.appTheme == option,
                                onClick = { vm.setTheme(option) }, // Updates state, triggering smooth fade in MainActivity
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                icon = { 
                                    SegmentedButtonDefaults.Icon(active = vm.appTheme == option) {
                                        Icon(icons[index], null) 
                                    }
                                }
                            ) {
                                Text(labels[index])
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        stringResource(R.string.language), 
                        style = MaterialTheme.typography.labelLarge, 
                        modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val languages = listOf(
                            Triple("en", "Eng", Icons.Filled.Language),
                            Triple("ru", "Рус", Icons.Filled.Translate),
                            Triple("ky", "Кыр", Icons.Filled.Book)
                        )
                        
                        languages.forEachIndexed { index, (code, label, icon) ->
                            SegmentedButton(
                                selected = vm.language == code,
                                onClick = { 
                                    if (vm.language != code) {
                                        vm.setAppLanguage(code)
                                        // Standard activity recreation to apply new Locale
                                        (context as? Activity)?.recreate()
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = languages.size),
                                icon = {
                                    SegmentedButtonDefaults.Icon(active = vm.language == code) {
                                        Icon(icon, null)
                                    }
                                }
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }

            // --- SECTION: CONTENT ---
            item {
                SettingsSectionTitle(stringResource(R.string.documents), Icons.Outlined.Description)
                SettingsGroup {
                    Text(
                        stringResource(R.string.doc_viewer_title), 
                        style = MaterialTheme.typography.labelLarge, 
                        modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val options = listOf("IN_APP", "WEBSITE")
                        val labels = listOf(
                            stringResource(R.string.in_app_pdf),
                            stringResource(R.string.website_official)
                        )
                        val icons = listOf(Icons.Outlined.Description, Icons.Outlined.Web)

                        options.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = vm.downloadMode == option,
                                onClick = { vm.setDocMode(option) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                icon = {
                                     SegmentedButtonDefaults.Icon(active = vm.downloadMode == option) {
                                        Icon(icons[index], null)
                                     }
                                }
                            ) {
                                Text(labels[index])
                            }
                        }
                    }
                }
            }

            // --- SECTION: TOOLS ---
            item {
                SettingsSectionTitle(stringResource(R.string.tools_title), Icons.Outlined.Translate)
                SettingsGroup(padding = 0.dp) {
                   Row(
                       modifier = Modifier
                           .fillMaxWidth()
                           .clickable { vm.showDictionaryScreen = true }
                           .padding(horizontal = 16.dp, vertical = 16.dp),
                       verticalAlignment = Alignment.CenterVertically
                   ) {
                       Icon(
                           Icons.Filled.Translate, 
                           contentDescription = null, 
                           tint = MaterialTheme.colorScheme.primary 
                       )
                       Spacer(Modifier.width(16.dp))
                       Column(modifier = Modifier.weight(1f)) {
                           Text(stringResource(R.string.dict_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                           Text(stringResource(R.string.dict_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                       }
                       Icon(
                           Icons.AutoMirrored.Outlined.OpenInNew, 
                           contentDescription = null, 
                           modifier = Modifier.size(20.dp),
                           tint = MaterialTheme.colorScheme.onSurfaceVariant
                       )
                   }
                }
            }

            // --- SECTION: ABOUT ---
            item {
                SettingsSectionTitle(stringResource(R.string.about), Icons.Outlined.Info)
                SettingsGroup {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(stringResource(R.string.app_name_display), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.univ_name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(start = 16.dp)
                        ) {
                            Text(
                                "v$appVersion", 
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- HELPER COMPONENTS ---

@Composable
fun SettingsSectionTitle(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically, 
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    ) {
        Icon(
            icon, 
            contentDescription = null, 
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title, 
            style = MaterialTheme.typography.titleSmall, 
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SettingsGroup(
    padding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(padding),
            content = content
        )
    }
}