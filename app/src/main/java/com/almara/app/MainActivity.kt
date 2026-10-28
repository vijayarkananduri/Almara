package com.almara.app

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class LocalFile(val uri: android.net.Uri, val name: String, val mime: String, val bytes: Long, val modified: Long, val source: String) {
    val day: LocalDate get() = Instant.ofEpochMilli(modified).atZone(ZoneId.systemDefault()).toLocalDate()
}
private data class SavedView(val title: String, val source: String, val kind: String, val period: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AlmaraApp() }
    }
}

@Composable
private fun AlmaraApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("almara", 0) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    var files by remember { mutableStateOf<List<LocalFile>>(emptyList()) }
    var views by remember { mutableStateOf(loadViews(prefs)) }
    var query by remember { mutableStateOf("") }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var showCreate by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Your files stay on this phone. Nothing is uploaded.") }
    var indexing by remember { mutableStateOf(false) }

    val permission = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    fun scan() {
        indexing = true
        files = runCatching { readMedia(context) }.getOrElse { emptyList() }
        indexing = false
        status = if (files.isEmpty()) "No files found yet. Choose media access to begin." else "${files.size} files indexed on this device"
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) scan() else status = "Media access is off. You can enable it in Android settings."
    }
    LaunchedEffect(Unit) {
        if (permission.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) scan()
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFFE6A86C), secondary = Color(0xFFB5C9A5), background = Color(0xFF10100F), surface = Color(0xFF191817)) else lightColorScheme(primary = Color(0xFF86552D), secondary = Color(0xFF536B48), background = Color(0xFFFAF6EF), surface = Color(0xFFFFFCF7))) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Almara", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Smart Sedha.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Settings, "Settings") }
                }
                Text("Your files, organized — never relocated.", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                    Text("  ${month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { month = month.minusMonths(1); selectedDay = null }) { Text("‹") }
                    TextButton(onClick = { month = month.plusMonths(1); selectedDay = null }) { Text("›") }
                }
                Heatmap(month, files, selectedDay, onSelect = { selectedDay = if (selectedDay == it) null else it })
                Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Search · source:whatsapp type:pdf") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(18.dp))
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(onClick = { showCreate = true }) { Icon(Icons.Rounded.Add, "Create virtual folder") }
                }
                if (views.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        views.take(4).forEach { view -> FilterChip(selected = false, onClick = { query = "source:${view.source.lowercase()} type:${view.kind.lowercase()} date:${view.period.lowercase().replace(' ', '-') }" }, label = { Text(view.title, maxLines = 1) }, leadingIcon = { Icon(Icons.Rounded.Folder, null, Modifier.size(16.dp)) }) }
                    }
                }
                Text(if (selectedDay != null) "${selectedDay!!.format(DateTimeFormatter.ofPattern("MMM d"))} · files" else "Your files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 4.dp))
                if (indexing) LinearProgressIndicator(Modifier.fillMaxWidth())
                val shown = filterFiles(files, query, selectedDay)
                if (shown.isEmpty()) {
                    Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (files.isEmpty()) "Your almara is empty." else "No matching files.", style = MaterialTheme.typography.titleMedium)
                        Text(if (files.isEmpty()) "Tap the refresh button to grant media access." else "Try another search or date.", style = MaterialTheme.typography.bodyMedium)
                        if (files.isEmpty()) TextButton(onClick = { permissionLauncher.launch(permission) }) { Text("Allow media access") }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(shown, key = { it.uri.toString() }) { file -> FileRow(file, dark, onOpen = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(file.uri, file.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                                .onFailure { status = "Couldn't open this file. Try another app?" }
                        }, onShare = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(file.mime).putExtra(Intent.EXTRA_STREAM, file.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share file")) }) }
                    }
                }
            }
        }
    }
    if (showCreate) CreateViewDialog(onDismiss = { showCreate = false }, onCreate = { view -> views = views + view; saveViews(prefs, views); showCreate = false; status = "Maa would be proud." })
    if (showSettings) AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("Settings") }, text = { Column {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("AMOLED dark mode", Modifier.weight(1f)); Switch(dark, { dark = it; prefs.edit().putBoolean("dark", it).apply() }) }
        Text("Almara works offline. It has no internet permission and does not move your files.", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { permissionLauncher.launch(permission); showSettings = false }) { Text("Refresh media index") }
    } }, confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done") } })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Heatmap(month: YearMonth, files: List<LocalFile>, selected: LocalDate?, onSelect: (LocalDate) -> Unit) {
    val counts = remember(files, month) { files.filter { YearMonth.from(it.day) == month }.groupingBy { it.day }.eachCount() }
    val offset = (month.atDay(1).dayOfWeek.value - 1)
    val rows = ((offset + month.lengthOfMonth() + 6) / 7)
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth()) { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) } }
        repeat(rows) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val n = week * 7 + col - offset + 1
                    if (n in 1..month.lengthOfMonth()) {
                        val day = month.atDay(n); val count = counts[day] ?: 0
                        val alpha = when { count == 0 -> .08f; count <= 3 -> .24f; count <= 10 -> .46f; count <= 25 -> .7f; else -> 1f }
                        val color by animateColorAsState(if (selected == day) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary.copy(alpha = alpha), label = "heat")
                        Box(Modifier.weight(1f).padding(2.dp).height(34.dp).background(color, RoundedCornerShape(10.dp)).combinedClickable(onClick = { onSelect(day) }), contentAlignment = Alignment.Center) {
                            Text("$n", style = MaterialTheme.typography.labelSmall, color = if (count > 10 || selected == day) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                    } else Spacer(Modifier.weight(1f).padding(2.dp).height(34.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(file: LocalFile, dark: Boolean, onOpen: () -> Unit, onShare: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = { menu = true }), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(fileIcon(file.mime), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text("${file.source} · ${file.day.format(DateTimeFormatter.ofPattern("MMM d"))} · ${formatBytes(file.bytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            Box { TextButton(onClick = { menu = true }) { Text("•••") }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("Open") }, onClick = { menu = false; onOpen() }); DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; onShare() }) } }
        }
    }
}

@Composable
private fun CreateViewDialog(onDismiss: () -> Unit, onCreate: (SavedView) -> Unit) {
    var source by remember { mutableStateOf("All") }; var kind by remember { mutableStateOf("All") }; var period by remember { mutableStateOf("This month") }; var name by remember { mutableStateOf("") }
    val suggestion = "$source · $kind · $period"
    AlertDialog(onDismissRequest = onDismiss, title = { Text("New virtual folder") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Files remain exactly where they are.", style = MaterialTheme.typography.bodySmall)
        Text("Source", fontWeight = FontWeight.SemiBold); ChipChoices(listOf("All", "WhatsApp", "Telegram", "Camera", "Download"), source) { source = it }
        Text("Type", fontWeight = FontWeight.SemiBold); ChipChoices(listOf("All", "Images", "PDFs", "Docs", "Audio"), kind) { kind = it }
        Text("Time", fontWeight = FontWeight.SemiBold); ChipChoices(listOf("Today", "This week", "This month"), period) { period = it }
        OutlinedTextField(name, { name = it }, label = { Text("Folder name") }, placeholder = { Text(suggestion) }, singleLine = true)
        Text("Preview will update when your media index is refreshed.", style = MaterialTheme.typography.bodySmall)
    } }, confirmButton = { TextButton(onClick = { onCreate(SavedView(name.ifBlank { suggestion }, source, kind, period)) }) { Text("Create") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ChipChoices(values: List<String>, selected: String, choose: (String) -> Unit) {
    Column { values.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { row.forEach { value -> FilterChip(selected == value, { choose(value) }, label = { Text(value, maxLines = 1) }) } } } }
}

private fun readMedia(context: android.content.Context): List<LocalFile> {
    val resolver = context.contentResolver
    val result = mutableListOf<LocalFile>()
    val collections = listOf(
        Triple(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media._ID, "image/*"),
        Triple(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media._ID, "video/*"),
        Triple(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, MediaStore.Audio.Media._ID, "audio/*")
    )
    for ((collection, idCol, fallbackMime) in collections) {
        runCatching {
            resolver.query(collection, arrayOf(idCol, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.RELATIVE_PATH), null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { cursor ->
                val idIx = cursor.getColumnIndexOrThrow(idCol); val nameIx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME); val mimeIx = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE); val sizeIx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE); val dateIx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED); val pathIx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                while (cursor.moveToNext()) {
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(idIx))
                    val name = if (nameIx >= 0) cursor.getString(nameIx).orEmpty() else "Untitled"
                    val mime = if (mimeIx >= 0) cursor.getString(mimeIx) ?: fallbackMime else fallbackMime
                    val path = if (pathIx >= 0) cursor.getString(pathIx).orEmpty() else ""
                    val source = when { path.contains("WhatsApp", true) -> "WhatsApp"; path.contains("Telegram", true) -> "Telegram"; path.contains("Screenshots", true) -> "Screenshot"; path.contains("DCIM/Camera", true) -> "Camera"; path.contains("Download", true) -> "Download"; else -> "Other" }
                    result += LocalFile(uri, name, mime, if (sizeIx >= 0) cursor.getLong(sizeIx) else 0L, if (dateIx >= 0) cursor.getLong(dateIx) * 1000 else System.currentTimeMillis(), source)
                }
            }
        }
    }
    return result.sortedByDescending { it.modified }
}

private fun filterFiles(files: List<LocalFile>, query: String, day: LocalDate?): List<LocalFile> {
    val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return files.filter { file ->
        (day == null || file.day == day) && tokens.all { token ->
            val parts = token.split(":", limit = 2)
            if (parts.size == 1) file.name.contains(token, true) else when (parts[0].lowercase(Locale.ROOT)) {
                "name" -> file.name.contains(parts[1], true)
                "source" -> file.source.contains(parts[1], true)
                "type" -> when (parts[1].lowercase()) { "image", "images" -> file.mime.startsWith("image/"); "pdf", "pdfs" -> file.mime == "application/pdf"; "audio" -> file.mime.startsWith("audio/"); "doc", "docs" -> file.name.substringAfterLast('.', "").lowercase() in setOf("doc", "docx", "txt", "md"); else -> true }
                "date" -> when (parts[1].lowercase()) { "today" -> file.day == LocalDate.now(); "yesterday" -> file.day == LocalDate.now().minusDays(1); "this-week" -> file.day.isAfter(LocalDate.now().minusDays(7)); "this-month" -> YearMonth.from(file.day) == YearMonth.now(); else -> file.day.toString().startsWith(parts[1]) }
                "size" -> { val m = Regex("([<>])(\\d+)(kb|mb|gb)", RegexOption.IGNORE_CASE).matchEntire(parts[1]); if (m == null) true else { val factor = when (m.groupValues[3].lowercase()) { "kb" -> 1024L; "mb" -> 1024L*1024; else -> 1024L*1024*1024 }; if (m.groupValues[1] == ">") file.bytes > m.groupValues[2].toLong() * factor else file.bytes < m.groupValues[2].toLong() * factor } }
                else -> true
            }
        }
    }
}

private fun fileIcon(mime: String) = when { mime.startsWith("image/") -> "▧"; mime == "application/pdf" -> "▤"; mime.startsWith("audio/") -> "♫"; mime.startsWith("video/") -> "▷"; else -> "▣" }
private fun formatBytes(bytes: Long) = when { bytes >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_000_000.0); bytes >= 1_000 -> "${bytes / 1_000} KB"; else -> "$bytes B" }
private fun loadViews(prefs: android.content.SharedPreferences): List<SavedView> = prefs.getStringSet("views", emptySet()).orEmpty().mapNotNull { raw -> raw.split("|", limit = 4).takeIf { it.size == 4 }?.let { SavedView(it[0], it[1], it[2], it[3]) } }
private fun saveViews(prefs: android.content.SharedPreferences, views: List<SavedView>) { prefs.edit().putStringSet("views", views.map { "${it.title}|${it.source}|${it.kind}|${it.period}" }.toSet()).apply() }
