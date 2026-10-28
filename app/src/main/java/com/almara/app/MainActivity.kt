package com.almara.app

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.material.icons.rounded.Home
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
import androidx.documentfile.provider.DocumentFile
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class LocalFile(val uri: Uri, val name: String, val mime: String, val bytes: Long, val modified: Long, val source: String) {
    val day: LocalDate get() = Instant.ofEpochMilli(modified).atZone(ZoneId.systemDefault()).toLocalDate()
}
private data class SavedView(val id: String, val title: String, val source: String, val kind: String, val period: String, val expiresAt: Long = 0L, val archived: Boolean = false)
private enum class AlmaraTab { HOME, SEARCH, FOLDERS, SETTINGS }
private enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

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
    var mode by remember { mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)) }
    var dark by remember { mutableStateOf(mode == ThemeMode.DARK || mode == ThemeMode.AMOLED || (mode == ThemeMode.SYSTEM && androidx.compose.foundation.isSystemInDarkTheme())) }
    var files by remember { mutableStateOf<List<LocalFile>>(emptyList()) }
    var views by remember { mutableStateOf(loadViews(prefs)) }
    var query by remember { mutableStateOf("") }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var tab by remember { mutableStateOf(AlmaraTab.HOME) }
    var showCreate by remember { mutableStateOf(false) }
    var showWelcome by remember { mutableStateOf(!prefs.getBoolean("welcomed", false)) }
    var status by remember { mutableStateOf("Your files stay on this phone. Nothing is uploaded.") }
    var indexing by remember { mutableStateOf(false) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    val permission = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    fun scan() {
        indexing = true
        val media = runCatching { readMedia(context) }.getOrElse { emptyList() }
        val treeFiles = prefs.getStringSet("trees", emptySet()).orEmpty().flatMap { raw ->
            runCatching { scanTree(context, Uri.parse(raw)) }.getOrElse { emptyList() }
        }
        files = (media + treeFiles).distinctBy { it.uri.toString() }.sortedByDescending { it.modified }
        indexing = false
        status = if (files.isEmpty()) "No files found yet. Choose media access or add a folder." else "${files.size} local references · files never moved"
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) scan() else status = "Media access is off. You can enable it in Android settings."
    }
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val trees = prefs.getStringSet("trees", emptySet()).orEmpty().toMutableSet().apply { add(uri.toString()) }
            prefs.edit().putStringSet("trees", trees).apply(); scan(); status = "Folder added. Almara only reads its contents."
        }
    }
    LaunchedEffect(Unit) { if (permission.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } || prefs.getStringSet("trees", emptySet()).orEmpty().isNotEmpty()) scan() }

    val colors = when (mode) {
        ThemeMode.LIGHT -> lightColorScheme(primary = Color(0xFF81502D), secondary = Color(0xFF536B48), background = Color(0xFFFAF5EB), surface = Color(0xFFFFFCF6))
        ThemeMode.AMOLED -> darkColorScheme(primary = Color(0xFFE6A86C), secondary = Color(0xFFB5C9A5), background = Color.Black, surface = Color(0xFF080807))
        ThemeMode.DARK -> darkColorScheme(primary = Color(0xFFE6A86C), secondary = Color(0xFFB5C9A5), background = Color(0xFF151311), surface = Color(0xFF211E1A))
        ThemeMode.SYSTEM -> if (dark) darkColorScheme(primary = Color(0xFFE6A86C), secondary = Color(0xFFB5C9A5), background = Color(0xFF151311), surface = Color(0xFF211E1A)) else lightColorScheme(primary = Color(0xFF81502D), secondary = Color(0xFF536B48), background = Color(0xFFFAF5EB), surface = Color(0xFFFFFCF6))
    }
    MaterialTheme(colorScheme = colors) {
        Scaffold(bottomBar = {
            NavigationBar { listOf(AlmaraTab.HOME to Icons.Rounded.Home, AlmaraTab.SEARCH to Icons.Rounded.Search, AlmaraTab.FOLDERS to Icons.Rounded.Folder, AlmaraTab.SETTINGS to Icons.Rounded.Settings).forEach { (destination, icon) ->
                NavigationBarItem(selected = tab == destination, onClick = { tab = destination }, icon = { Icon(icon, null) }, label = { Text(destination.name.lowercase().replaceFirstChar { it.uppercase() }) })
            } }
        }, floatingActionButton = { if (tab == AlmaraTab.HOME || tab == AlmaraTab.FOLDERS) FloatingActionButton(onClick = { showCreate = true }) { Icon(Icons.Rounded.Add, "Create virtual folder") } }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Almara", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Smart Sedha.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary) }
                    if (indexing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                when (tab) {
                    AlmaraTab.HOME -> {
                        Text("Your files, organized — never relocated.", style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                            Text("  ${month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { month = month.minusMonths(1); selectedDay = null }) { Text("‹") }
                            TextButton(onClick = { month = month.plusMonths(1); selectedDay = null }) { Text("›") }
                        }
                        Heatmap(month, files, selectedDay, onSelect = { selectedDay = if (selectedDay == it) null else it })
                        SearchBar(query, { query = it }, { tab = AlmaraTab.SEARCH })
                        if (views.isNotEmpty()) ViewChips(views, onSelect = { query = it; tab = AlmaraTab.SEARCH })
                        Text(if (selectedDay != null) "${selectedDay!!.format(DateTimeFormatter.ofPattern("MMM d"))} · files" else "Your files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 4.dp))
                        FileList(files, query, selectedDay, onOpen = { openFile(context, it) }, onShare = { shareFile(context, it) }, onDeleteReference = { file -> files = files.filterNot { it.uri == file.uri }; status = "Removed from this view. The original file is untouched." })
                    }
                    AlmaraTab.SEARCH -> {
                        Text("Find something without remembering where it went.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp))
                        SearchBar(query, { query = it }, {})
                        Text("Try source:whatsapp type:pdf date:last-week", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 8.dp))
                        FileList(files, query, null, onOpen = { openFile(context, it) }, onShare = { shareFile(context, it) }, onDeleteReference = { file -> files = files.filterNot { it.uri == file.uri } })
                    }
                    AlmaraTab.FOLDERS -> {
                        Text("Virtual folders are saved rules. Your files stay put.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
                        if (views.isEmpty()) EmptyMessage("No virtual folders yet.", "Create one with +. Files are never copied or moved.")
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(views, key = { it.id }) { folder ->
                            Card(Modifier.fillMaxWidth().combinedClickable(onClick = { selectedFolderId = folder.id; query = folderQuery(folder); tab = AlmaraTab.SEARCH }, onLongClick = { selectedFolderId = folder.id }), shape = RoundedCornerShape(20.dp)) {
                                Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Text(folder.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); if (folder.expiresAt > 0) Text("◷", color = MaterialTheme.colorScheme.secondary) }
                                    Text("${folder.source} · ${folder.kind} · ${folder.period}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                    Row { TextButton(onClick = { selectedFolderId = folder.id; query = folderQuery(folder); tab = AlmaraTab.SEARCH }) { Text("View matches") }; TextButton(onClick = { views = views.filterNot { it.id == folder.id }; saveViews(prefs, views) }) { Text("Remove") } }
                                }
                            }
                        } }
                        selectedFolderId?.let { id -> Text("Long-press selected folder to remove its saved rule. Original files are always preserved.", style = MaterialTheme.typography.bodySmall) }
                    }
                    AlmaraTab.SETTINGS -> SettingsScreen(mode, { selected -> mode = selected; dark = selected == ThemeMode.DARK || selected == ThemeMode.AMOLED || (selected == ThemeMode.SYSTEM && androidx.compose.foundation.isSystemInDarkTheme()); prefs.edit().putString("theme", selected.name).apply() },
                        onGrant = { permissionLauncher.launch(permission) }, onAddFolder = { treeLauncher.launch(null) }, onRefresh = { scan() }, fileCount = files.size, treeCount = prefs.getStringSet("trees", emptySet()).orEmpty().size)
                }
            }
        }
        if (showCreate) CreateViewDialog(onDismiss = { showCreate = false }, onCreate = { view -> views = views + view.copy(id = java.util.UUID.randomUUID().toString()); saveViews(prefs, views); showCreate = false; status = "Maa would be proud." })
        if (showWelcome) AlertDialog(onDismissRequest = { showWelcome = false; prefs.edit().putBoolean("welcomed", true).apply() }, title = { Text("A tidier digital almara") }, text = { Text("Remember maa's nudge — apna almara seedha karo? Almara helps you find and group local files. Nothing is moved, uploaded, or shared without your action. No account. No cloud.") }, confirmButton = { TextButton(onClick = { showWelcome = false; prefs.edit().putBoolean("welcomed", true).apply() }) { Text("Let's begin") } })
    }
}

@Composable private fun SearchBar(value: String, onChange: (String) -> Unit, onClick: () -> Unit) { OutlinedTextField(value, onChange, Modifier.fillMaxWidth().padding(vertical = 10.dp), singleLine = true, placeholder = { Text("Search · source:whatsapp type:pdf") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(18.dp), trailingIcon = { TextButton(onClick = onClick) { Text("Go") } }) }
@Composable private fun ViewChips(views: List<SavedView>, onSelect: (String) -> Unit) { Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { views.take(3).forEach { view -> FilterChip(false, { onSelect(folderQuery(view)) }, label = { Text(view.title, maxLines = 1) }, leadingIcon = { Icon(Icons.Rounded.Folder, null, Modifier.size(16.dp)) }) } } }

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun Heatmap(month: YearMonth, files: List<LocalFile>, selected: LocalDate?, onSelect: (LocalDate) -> Unit) {
    val counts = remember(files, month) { files.filter { YearMonth.from(it.day) == month }.groupingBy { it.day }.eachCount() }
    val offset = month.atDay(1).dayOfWeek.value - 1
    val rows = (offset + month.lengthOfMonth() + 6) / 7
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth()) { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) } }
        repeat(rows) { week -> Row(Modifier.fillMaxWidth()) { repeat(7) { col ->
            val n = week * 7 + col - offset + 1
            if (n in 1..month.lengthOfMonth()) { val day = month.atDay(n); val count = counts[day] ?: 0; val alpha = when { count == 0 -> .08f; count <= 3 -> .24f; count <= 10 -> .46f; count <= 25 -> .7f; else -> 1f }; val color by animateColorAsState(if (selected == day) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary.copy(alpha = alpha), label = "heat")
                Box(Modifier.weight(1f).padding(2.dp).height(34.dp).background(color, RoundedCornerShape(10.dp)).combinedClickable(onClick = { onSelect(day) }), contentAlignment = Alignment.Center) { Text("$n", style = MaterialTheme.typography.labelSmall, color = if (count > 10 || selected == day) Color.White else MaterialTheme.colorScheme.onSurface) }
            } else Spacer(Modifier.weight(1f).padding(2.dp).height(34.dp))
        } } }
    }
}

@Composable private fun FileList(files: List<LocalFile>, query: String, day: LocalDate?, onOpen: (LocalFile) -> Unit, onShare: (LocalFile) -> Unit, onDeleteReference: (LocalFile) -> Unit) {
    val (shown, error) = remember(files, query, day) { runCatching { filterFiles(files, query, day) }.fold({ it to null }, { emptyList<LocalFile>() to (it.message ?: "Search could not be parsed") }) }
    if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    if (shown.isEmpty()) { EmptyMessage(if (files.isEmpty()) "Your almara is empty." else "No matching files.", if (files.isEmpty()) "Allow media access or add a folder in Settings." else "Try another search or date.") }
    else LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.72f), contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(shown, key = { it.uri.toString() }) { file -> var menu by remember { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth().combinedClickable(onClick = { onOpen(file) }, onLongClick = { menu = true }), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(fileIcon(file.mime), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 12.dp))
                    Column(Modifier.weight(1f)) { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium); Text("${file.source} · ${file.day.format(DateTimeFormatter.ofPattern("MMM d"))} · ${formatBytes(file.bytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
                    Box { TextButton(onClick = { menu = true }) { Text("•••") }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("Open") }, onClick = { menu = false; onOpen(file) }); DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; onShare(file) }); DropdownMenuItem(text = { Text("Remove from this view") }, onClick = { menu = false; onDeleteReference(file) }) } }
                }
            }
        }
    }
}

@Composable private fun EmptyMessage(title: String, detail: String) { Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) } }

@Composable private fun SettingsScreen(mode: ThemeMode, onMode: (ThemeMode) -> Unit, onGrant: () -> Unit, onAddFolder: () -> Unit, onRefresh: () -> Unit, fileCount: Int, treeCount: Int) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Appearance", style = MaterialTheme.typography.titleLarge); Text("Choose how Almara feels on this phone.", style = MaterialTheme.typography.bodyMedium) }
        item { Column { ThemeMode.values().forEach { option -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(mode == option, { onMode(option) }); Text(when(option) { ThemeMode.SYSTEM -> "System"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark"; ThemeMode.AMOLED -> "AMOLED · pure black" }, Modifier.weight(1f)); if (mode == option) Text("✓") } } } }
        item { HorizontalDivider(); Text("Local library", style = MaterialTheme.typography.titleLarge); Text("$fileCount indexed references · $treeCount chosen folders", style = MaterialTheme.typography.bodyMedium) }
        item { Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) { Text("Allow media access") } }
        item { OutlinedButton(onClick = onAddFolder, modifier = Modifier.fillMaxWidth()) { Text("Add a documents folder") } }
        item { TextButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Refresh index") } }
        item { HorizontalDivider(); Text("Privacy by design", style = MaterialTheme.typography.titleLarge); Text("Almara requests no network access. Your local files stay where they are. Folder access is granted by you through Android's document picker. Almara never deletes, renames, or moves an original file.", style = MaterialTheme.typography.bodyMedium) }
        item { Text("Almara · Smart Sedha.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary) }
    }
}

@Composable private fun CreateViewDialog(onDismiss: () -> Unit, onCreate: (SavedView) -> Unit) {
    var source by remember { mutableStateOf("All") }; var kind by remember { mutableStateOf("All") }; var period by remember { mutableStateOf("This month") }; var name by remember { mutableStateOf("") }
    val suggestion = "$source · $kind · $period"
    AlertDialog(onDismissRequest = onDismiss, title = { Text("New virtual folder") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Files remain exactly where they are.", style = MaterialTheme.typography.bodySmall)
        Text("Source", fontWeight = FontWeight.SemiBold); ChipChoices(listOf("All", "WhatsApp", "Telegram", "Camera", "Screenshot", "Download"), source) { source = it }
        Text("Type", fontWeight = FontWeight.SemiBold); ChipChoices(listOf("All", "Images", "PDFs", "Docs", "Audio", "Archives", "Text"), kind) { kind = it }
        Text("Time", fontWeight = FontWeight.SemiBold); ChipChoices(listOf("Today", "This week", "This month"), period) { period = it }
        OutlinedTextField(name, { name = it }, label = { Text("Folder name") }, placeholder = { Text(suggestion) }, singleLine = true)
        Text("Preview updates from your local index after saving.", style = MaterialTheme.typography.bodySmall)
    } }, confirmButton = { TextButton(onClick = { onCreate(SavedView("", name.ifBlank { suggestion }, source, kind, period)) }) { Text("Create") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
@Composable private fun ChipChoices(values: List<String>, selected: String, choose: (String) -> Unit) { Column { values.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { row.forEach { value -> FilterChip(selected == value, { choose(value) }, label = { Text(value, maxLines = 1) }) } } } } }

private fun readMedia(context: android.content.Context): List<LocalFile> {
    val resolver = context.contentResolver; val result = mutableListOf<LocalFile>()
    val collections = listOf(Triple(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media._ID, "image/*"), Triple(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media._ID, "video/*"), Triple(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, MediaStore.Audio.Media._ID, "audio/*"))
    for ((collection, idCol, fallbackMime) in collections) runCatching {
        resolver.query(collection, arrayOf(idCol, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.RELATIVE_PATH), null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { cursor ->
            val idIx = cursor.getColumnIndexOrThrow(idCol); val nameIx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME); val mimeIx = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE); val sizeIx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE); val dateIx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED); val pathIx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) { val uri = ContentUris.withAppendedId(collection, cursor.getLong(idIx)); val name = if (nameIx >= 0) cursor.getString(nameIx).orEmpty() else "Untitled"; val mime = if (mimeIx >= 0) cursor.getString(mimeIx) ?: fallbackMime else fallbackMime; val path = if (pathIx >= 0) cursor.getString(pathIx).orEmpty() else ""; val source = sourceFor(path, name); val modified = if (dateIx >= 0) cursor.getLong(dateIx) * 1000 else System.currentTimeMillis(); result += LocalFile(uri, name, mime, if (sizeIx >= 0) cursor.getLong(sizeIx) else 0L, modified, source) }
        }
    }
    return result
}
private fun scanTree(context: android.content.Context, tree: Uri): List<LocalFile> {
    val root = DocumentFile.fromTreeUri(context, tree) ?: return emptyList(); val output = mutableListOf<LocalFile>(); val queue = ArrayDeque<DocumentFile>(); queue.add(root); var visited = 0
    while (queue.isNotEmpty() && visited < 20_000) { val node = queue.removeFirst(); visited++; if (node.isDirectory) node.listFiles().forEach { child -> if (child.isDirectory) queue.add(child) else if (child.isFile && accepted(child.name.orEmpty(), child.type.orEmpty())) { val name = child.name ?: "Untitled"; val modified = child.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis(); output += LocalFile(child.uri, name, child.type ?: mimeFor(name), child.length(), modified, sourceFor("", name)) } } }
    return output
}
private fun accepted(name: String, mime: String): Boolean = mime.startsWith("image/") || mime.startsWith("audio/") || mime.startsWith("video/") || mime == "application/pdf" || name.substringAfterLast('.', "").lowercase() in setOf("pdf", "doc", "docx", "odt", "rtf", "txt", "md", "zip", "tar", "gz", "rar", "7z")
private fun mimeFor(name: String): String = when(name.substringAfterLast('.', "").lowercase()) { "pdf" -> "application/pdf"; "txt" -> "text/plain"; "md" -> "text/markdown"; "zip" -> "application/zip"; "doc" -> "application/msword"; "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; else -> "application/octet-stream" }
private fun sourceFor(path: String, name: String): String { val v = "$path/$name"; return when { v.contains("WhatsApp", true) -> "WhatsApp"; v.contains("Telegram", true) -> "Telegram"; v.contains("Screenshots", true) -> "Screenshot"; v.contains("DCIM/Camera", true) -> "Camera"; v.contains("Download", true) -> "Download"; else -> "Other" } }
private fun filterFiles(files: List<LocalFile>, query: String, day: LocalDate?): List<LocalFile> {
    val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    for (token in tokens) { val parts = token.split(":", limit = 2); if (parts.size == 2 && parts[0].lowercase() !in setOf("name", "source", "type", "date", "size")) throw IllegalArgumentException("Unknown search prefix: ${parts[0]}. Try source:, type:, date:, name:, or size:"); if (parts.size == 2 && parts[1].isBlank()) throw IllegalArgumentException("Add a value after ${parts[0]}:") }
    return files.filter { file -> (day == null || file.day == day) && tokens.all { token ->
        val parts = token.split(":", limit = 2); if (parts.size == 1) file.name.contains(token, true) else when(parts[0].lowercase(Locale.ROOT)) {
            "name" -> file.name.contains(parts[1], true)
            "source" -> if (parts[1].equals("all", true)) true else if (parts[1].equals("screenshot", true)) file.source.equals("Screenshot", true) else file.source.equals(parts[1], true)
            "type" -> when(parts[1].lowercase()) { "all" -> true; "image", "images" -> file.mime.startsWith("image/"); "pdf", "pdfs" -> file.mime == "application/pdf"; "audio" -> file.mime.startsWith("audio/"); "doc", "docs" -> file.name.substringAfterLast('.', "").lowercase() in setOf("doc", "docx", "odt", "rtf"); "archive", "archives" -> file.name.substringAfterLast('.', "").lowercase() in setOf("zip", "tar", "gz", "rar", "7z"); "text" -> file.mime.startsWith("text/") || file.name.substringAfterLast('.', "").lowercase() in setOf("txt", "md"); else -> throw IllegalArgumentException("Unknown type '${parts[1]}'.") }
            "date" -> dateMatches(file.day, parts[1])
            "size" -> sizeMatches(file.bytes, parts[1])
            else -> false
        }
    } }
}
private fun dateMatches(day: LocalDate, value: String): Boolean { val now = LocalDate.now(); return when(value.lowercase()) { "today" -> day == now; "yesterday" -> day == now.minusDays(1); "last-week" -> day in now.minusDays(7)..now; "this-week" -> day >= now.with(java.time.DayOfWeek.MONDAY); "last-month" -> YearMonth.from(day) == YearMonth.now().minusMonths(1); "this-month" -> YearMonth.from(day) == YearMonth.now(); else -> runCatching { if (value.length == 7) YearMonth.parse(value) == YearMonth.from(day) else LocalDate.parse(value) == day }.getOrElse { throw IllegalArgumentException("Invalid date '$value'. Use today, this-week, YYYY-MM-DD, or YYYY-MM.") } } }
private fun sizeMatches(bytes: Long, value: String): Boolean { val m = Regex("([<>])(\\d+)(kb|mb|gb)", RegexOption.IGNORE_CASE).matchEntire(value) ?: throw IllegalArgumentException("Invalid size '$value'. Example: size:>5mb"); val factor = when(m.groupValues[3].lowercase()) { "kb" -> 1024L; "mb" -> 1024L*1024; else -> 1024L*1024*1024 }; val amount = m.groupValues[2].toLongOrNull() ?: throw IllegalArgumentException("Size is too large."); return if (m.groupValues[1] == ">") bytes > amount * factor else bytes < amount * factor }
private fun folderQuery(v: SavedView) = listOfNotNull(if(v.source == "All") null else "source:${v.source.lowercase()}", if(v.kind == "All") null else "type:${v.kind.lowercase().removeSuffix("s")}", when(v.period) { "Today" -> "date:today"; "This week" -> "date:this-week"; "This month" -> "date:this-month"; else -> null }).joinToString(" ")
private fun openFile(context: android.content.Context, file: LocalFile) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(file.uri, file.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) } }
private fun shareFile(context: android.content.Context, file: LocalFile) { runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(file.mime).putExtra(Intent.EXTRA_STREAM, file.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share file")) } }
private fun fileIcon(mime: String) = when { mime.startsWith("image/") -> "▧"; mime == "application/pdf" -> "▤"; mime.startsWith("audio/") -> "♫"; mime.startsWith("video/") -> "▷"; else -> "▣" }
private fun formatBytes(bytes: Long) = when { bytes >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_000_000.0); bytes >= 1_000 -> "${bytes / 1_000} KB"; else -> "$bytes B" }
private fun loadViews(prefs: android.content.SharedPreferences): List<SavedView> = prefs.getStringSet("views", emptySet()).orEmpty().mapNotNull { raw -> raw.split("|", limit = 7).takeIf { it.size >= 5 }?.let { SavedView(it[0], it[1], it[2], it[3], it[4], it.getOrNull(5)?.toLongOrNull() ?: 0L, it.getOrNull(6)?.toBoolean() ?: false) } }
private fun saveViews(prefs: android.content.SharedPreferences, views: List<SavedView>) { prefs.edit().putStringSet("views", views.map { "${it.id}|${it.title}|${it.source}|${it.kind}|${it.period}|${it.expiresAt}|${it.archived}" }.toSet()).apply() }
