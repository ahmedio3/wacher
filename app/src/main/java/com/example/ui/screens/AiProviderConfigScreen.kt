package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.AiChatViewModel
import com.example.data.ai.AiModel
import com.example.data.ai.AiProvider
import com.example.data.ai.AiProviderManager

// Auto-complete data
private data class AutoCompletePreset(
    val endpoint: String,
    val displayName: String,
    val models: List<String>
)

private val AUTO_COMPLETE_PRESETS = listOf(
    AutoCompletePreset(
        endpoint = "https://router.bynara.id",
        displayName = "Bynara",
        models = listOf("mimo-v2.5-free", "mimo-v2.5-pro-free")
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProviderConfigScreen(
    viewModel: AiChatViewModel,
    onBack: () -> Unit
) {
    val providers by viewModel.providers.collectAsState()
    var editingProviderId by remember { mutableStateOf<String?>(null) }
    var showAddForm by remember { mutableStateOf(providers.isEmpty()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إعدادات المزودين", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Existing providers list
            providers.forEach { provider ->
                ProviderCard(
                    provider = provider,
                    isSelected = provider.id == editingProviderId,
                    onEdit = { editingProviderId = provider.id },
                    onDelete = { viewModel.deleteProvider(provider.id) },
                    onSetDefault = {
                        viewModel.updateProvider(provider.copy(isDefault = true))
                    }
                )
            }

            // Add new provider button
            if (!showAddForm && editingProviderId == null) {
                OutlinedButton(
                    onClick = { showAddForm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إضافة مزود جديد")
                }
            }

            // Add / Edit form
            if (showAddForm || editingProviderId != null) {
                val existingProvider = editingProviderId?.let { id -> providers.find { it.id == id } }

                var displayName by remember(editingProviderId) {
                    mutableStateOf(existingProvider?.displayName ?: "")
                }
                var endpoint by remember(editingProviderId) {
                    mutableStateOf(existingProvider?.endpoint ?: "")
                }
                var apiKey by remember(editingProviderId) {
                    mutableStateOf(existingProvider?.apiKey ?: "")
                }
                var modelsText by remember(editingProviderId) {
                    mutableStateOf(existingProvider?.models?.joinToString("\n") { it.name } ?: "")
                }
                // Model configs: name -> (thinkingEffort, webSearch)
                var modelConfigs by remember(editingProviderId) {
                    mutableStateOf(
                        existingProvider?.models?.associate { it.name to Pair(it.thinkingEffort, it.webSearch) } ?: emptyMap()
                    )
                }
                var isDefault by remember(editingProviderId) {
                    mutableStateOf(existingProvider?.isDefault ?: false)
                }

                // Auto-complete: watch endpoint field
                LaunchedEffect(endpoint) {
                    AUTO_COMPLETE_PRESETS.find { preset ->
                        endpoint.trim().startsWith(preset.endpoint)
                    }?.let { preset ->
                        if (displayName.isBlank()) displayName = preset.displayName
                        if (modelsText.isBlank()) modelsText = preset.models.joinToString("\n")
                        // Also update model configs if empty
                        if (modelConfigs.isEmpty()) {
                            modelConfigs = preset.models.associateWith { Pair(false, false) }
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (editingProviderId != null) "تعديل المزود" else "مزود جديد",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )

                        // Display Name
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("الاسم (مثال: Bynara)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )

                        // Endpoint
                        OutlinedTextField(
                            value = endpoint,
                            onValueChange = { endpoint = it },
                            label = { Text("الرابط (Endpoint)") },
                            placeholder = { Text("https://router.bynara.id") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next
                            )
                        )

                        // Auto-complete hint
                        if (endpoint.isBlank() || !AUTO_COMPLETE_PRESETS.any { endpoint.trim().startsWith(it.endpoint) }) {
                            Text(
                                text = "مزودين معروفين:\n${AUTO_COMPLETE_PRESETS.joinToString("\n") { "• ${it.endpoint} ← ${it.displayName}" }}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }

                        // API Key
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("مفتاح API") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )

                        // Models
                        OutlinedTextField(
                            value = modelsText,
                            onValueChange = { modelsText = it },
                            label = { Text("الموديلز (كل موديل في سطر)") },
                            placeholder = { Text("mimo-v2.5-free\nmimo-v2.5-pro-free") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            minLines = 2,
                            maxLines = 5,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                        )

                        // Model configurations (thinking effort, web search per model)
                        val modelNames = modelsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        if (modelNames.isNotEmpty()) {
                            Text(
                                text = "إعدادات كل موديل:",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            modelNames.forEach { modelName ->
                                val config = modelConfigs[modelName] ?: Pair(false, false)
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = modelName,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = config.first,
                                                onCheckedChange = { checked ->
                                                    modelConfigs = modelConfigs + (modelName to Pair(checked, config.second))
                                                }
                                            )
                                            Text("تفعيل التفكير العميق (Thinking)", fontSize = 12.sp, modifier = Modifier.weight(1f))
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = config.second,
                                                onCheckedChange = { checked ->
                                                    modelConfigs = modelConfigs + (modelName to Pair(config.first, checked))
                                                }
                                            )
                                            Text("البحث في الويب (Web Search)", fontSize = 12.sp, modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        // Default toggle
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isDefault,
                                onCheckedChange = { isDefault = it }
                            )
                            Text("تعيين كمزود افتراضي", fontSize = 14.sp)
                        }

                        // Save / Cancel buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showAddForm = false
                                    editingProviderId = null
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("إلغاء")
                            }

                            Button(
                                onClick = {
                                    val models = modelNames.map { name ->
                                        val config = modelConfigs[name] ?: Pair(false, false)
                                        AiModel(
                                            name = name,
                                            thinkingEffort = config.first,
                                            webSearch = config.second
                                        )
                                    }
                                    val provider = AiProvider(
                                        id = editingProviderId ?: java.util.UUID.randomUUID().toString(),
                                        displayName = displayName,
                                        endpoint = endpoint.trimEnd('/'),
                                        apiKey = apiKey,
                                        models = models,
                                        isDefault = isDefault
                                    )
                                    if (editingProviderId != null) {
                                        viewModel.updateProvider(provider)
                                    } else {
                                        viewModel.addProvider(provider)
                                    }
                                    showAddForm = false
                                    editingProviderId = null
                                },
                                enabled = displayName.isNotBlank() && endpoint.isNotBlank() && apiKey.isNotBlank() && modelNames.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("حفظ")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderCard(
    provider: AiProvider,
    isSelected: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit
) {
    Card(
        onClick = onEdit,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = provider.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (provider.isDefault) {
                        Spacer(modifier = Modifier.width(8.dp))
                        SuggestionChip(
                            onClick = {},
                            label = { Text("افتراضي", fontSize = 10.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = provider.endpoint,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1
                )
                Text(
                    text = "${provider.models.size} موديل | ${provider.apiKey.take(8)}...",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }

            // Set default
            if (!provider.isDefault) {
                IconButton(onClick = onSetDefault) {
                    Icon(Icons.Default.Check, contentDescription = "تعيين افتراضي", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // Delete
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFFF4444))
            }
        }
    }
}
