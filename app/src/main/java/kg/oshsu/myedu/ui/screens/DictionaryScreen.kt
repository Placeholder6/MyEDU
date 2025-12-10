package kg.oshsu.myedu.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(vm: MainViewModel, onClose: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<Pair<String, String>?>(null) }
    var itemToDelete by remember { mutableStateOf<String?>(null) }

    // Filter combined dictionary
    val filteredList = remember(vm.combinedDictionary, searchQuery) {
        vm.combinedDictionary.entries
            .filter {
                it.key.contains(searchQuery, ignoreCase = true) ||
                it.value.contains(searchQuery, ignoreCase = true)
            }
            .sortedBy { it.key }
            .toList()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dict_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.desc_back))
                    }
                },
                actions = {
                    IconButton(onClick = { vm.resetDictionaryToDefault() }) {
                        Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.dict_reset_desc))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.dict_add_desc))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text(stringResource(R.string.dict_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                singleLine = true
            )

            if (filteredList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.dict_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 80.dp), modifier = Modifier.fillMaxSize()) {
                    items(filteredList) { entry ->
                        DictionaryItem(
                            original = entry.key,
                            translation = entry.value,
                            onEdit = { editingItem = entry.toPair() },
                            onDelete = { itemToDelete = entry.key }
                        )
                    }
                }
            }
        }
    }

    // ADD / EDIT DIALOG
    if (showAddDialog || editingItem != null) {
        val isEdit = editingItem != null
        var key by remember { mutableStateOf(editingItem?.first ?: "") }
        var value by remember { mutableStateOf(editingItem?.second ?: "") }

        Dialog(onDismissRequest = { showAddDialog = false; editingItem = null }) {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        if (isEdit) stringResource(R.string.dict_edit_title) else stringResource(R.string.dict_add_title),
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = key, onValueChange = { key = it },
                        label = { Text(stringResource(R.string.dict_label_original)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        readOnly = isEdit // Generally better not to change key when editing to avoid duplicates
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = value, onValueChange = { value = it },
                        label = { Text(stringResource(R.string.dict_label_translation)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showAddDialog = false; editingItem = null }) { Text(stringResource(R.string.dict_btn_cancel)) }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            if (key.isNotBlank()) {
                                vm.addOrUpdateDictionaryEntry(key, value)
                                showAddDialog = false
                                editingItem = null
                            }
                        }) { Text(stringResource(R.string.dict_btn_save)) }
                    }
                }
            }
        }
    }

    // DELETE DIALOG
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.dict_delete_title)) },
            text = { Text(stringResource(R.string.dict_delete_msg)) },
            confirmButton = {
                TextButton(
                    onClick = { vm.removeDictionaryEntry(itemToDelete!!); itemToDelete = null }, 
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.dict_delete_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { itemToDelete = null }) { Text(stringResource(R.string.dict_btn_cancel)) } }
        )
    }
}

@Composable
fun DictionaryItem(original: String, translation: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = original, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp).width(50.dp), color = MaterialTheme.colorScheme.primary.copy(alpha=0.5f))
                Text(text = translation, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.dict_edit_desc), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.dict_delete_desc), tint = MaterialTheme.colorScheme.error) }
        }
    }
}
