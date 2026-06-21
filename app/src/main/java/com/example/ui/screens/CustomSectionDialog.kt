package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.remote.CustomSectionItem
import com.example.data.remote.CustomSectionManager
import com.example.ui.viewmodel.MovieViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSectionDialog(
    viewModel: MovieViewModel,
    onDismiss: () -> Unit
) {
    var isAddingNew by remember { mutableStateOf(false) }
    val items by CustomSectionManager.getItems().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("إدارة قسم \"بتاع\"") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        TextButton(onClick = { isAddingNew = !isAddingNew }) {
                            Text(if (isAddingNew) "القائمة" else "إضافة جديد", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                )

                if (isAddingNew) {
                    AddNewCustomItemView(
                        viewModel = viewModel,
                        onSaved = { isAddingNew = false }
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        items(items) { item ->
                            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title.ifEmpty { "عنصر مخصص" }, fontWeight = FontWeight.Bold)
                                        Text(item.message, fontSize = 12.sp, maxLines = 1)
                                        Text("النوع: ${item.displayType} • الإجراء: ${item.targetAction}", fontSize = 10.sp, color = Color.Gray)
                                    }
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                CustomSectionManager.deleteItem(item.id)
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                        if (items.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("لا توجد عناصر. اضغط إضافة جديد.", color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddNewCustomItemView(
    viewModel: MovieViewModel,
    onSaved: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var searchQ by remember { mutableStateOf("") }
    var searchRes by remember { mutableStateOf<List<com.example.data.remote.TmdbMediaItem>?>(null) }
    
    var selectedMedia by remember { mutableStateOf<com.example.data.remote.TmdbMediaItem?>(null) }
    
    var msgText by remember { mutableStateOf("") }
    var displayType by remember { mutableStateOf("poster") } // poster, landscape, gradient
    var actionType by remember { mutableStateOf("details") } // details, link, etc.
    var linkTarget by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (selectedMedia == null && actionType == "details") {
            OutlinedTextField(
                value = searchQ,
                onValueChange = { searchQ = it },
                label = { Text("ابحث عن فيلم أو مسلسل..") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            try {
                                val r = viewModel.searchDirect(searchQ)
                                searchRes = r.results
                            } catch (e: Exception) {}
                        }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(searchRes.orEmpty()) { m ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedMedia = m
                            title = m.title ?: m.name ?: ""
                            imageUrl = "https://image.tmdb.org/t/p/w500${m.posterPath}"
                            linkTarget = "${m.mediaType ?: "movie"}:${m.id}"
                        }.padding(8.dp)
                    ) {
                        Text(m.title ?: m.name ?: "Unknown", modifier = Modifier.weight(1f))
                    }
                }
            }
            
            Button(onClick = { actionType = "link" }, modifier = Modifier.fillMaxWidth()) {
                Text("إضافة عنصر مخصص (بدون فيلم/مسلسل)")
            }
        } else {
            // Edit form
            Text("الرسالة (النص الظاهر):")
            OutlinedTextField(value = msgText, onValueChange = { msgText = it }, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(8.dp))
            if (actionType != "details") {
                Text("العنوان:")
                OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth())
                
                Text("رابط الصورة:")
                OutlinedTextField(value = imageUrl, onValueChange = { imageUrl = it }, modifier = Modifier.fillMaxWidth())
                
                Text("الإجراء (مثال: رابط ويب، أو 'downloads', 'chat'):")
                OutlinedTextField(value = linkTarget, onValueChange = { linkTarget = it }, modifier = Modifier.fillMaxWidth())
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("طريقة العرض:")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("poster", "landscape", "gradient").forEach { dt ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = displayType == dt, onClick = { displayType = dt })
                        Text(dt)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    coroutineScope.launch {
                        CustomSectionManager.saveItem(
                            CustomSectionItem(
                                displayType = displayType,
                                targetAction = if (actionType == "details") "details" else "link",
                                targetData = linkTarget,
                                message = msgText,
                                title = title,
                                imageUrl = if (displayType == "landscape" && selectedMedia?.backdropPath != null) 
                                    "https://image.tmdb.org/t/p/w780${selectedMedia?.backdropPath}" 
                                    else imageUrl
                            )
                        )
                        selectedMedia = null
                        onSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("حفظ العنصر")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { selectedMedia = null; actionType = "details" }, modifier = Modifier.fillMaxWidth()) {
                Text("إلغاء واختيار شيء آخر")
            }
        }
    }
}
