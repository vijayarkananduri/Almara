package com.almara.app

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.os.Bundle
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.core.content.FileProvider
import com.almara.app.data.AlmaraDatabase
import com.almara.app.data.FolderEntity
import com.almara.app.data.IndexingScheduler
import com.almara.app.data.SqlCipherKeyRepository
import com.almara.app.data.UriMetadataEntity
import com.almara.app.data.VirtualRuleEntity
import com.almara.app.widget.AlmaraWidgetData
import com.almara.app.conversion.ConversionService
import com.almara.app.domain.FileRecord
import com.almara.app.domain.FileType
import com.almara.app.domain.Source
import com.almara.app.domain.parseQuery
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.sqlcipher.database.SQLiteDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class LocalFile(val uri: Uri, val name: String, val mime: String, val bytes: Long, val modified: Long, val source: String, val contentDay: LocalDate? = null) {
    val day: LocalDate get() = contentDay ?: Instant.ofEpochMilli(modified).atZone(ZoneId.systemDefault()).toLocalDate()
}
private data class SavedView(val id: String, val title: String, val source: String, val kind: String, val period: String, val expiresAt: Long = 0L, val archived: Boolean = false, val archiveOnExpiry: Boolean = false, val nameContains: String = "")
private enum class AlmaraTab { HOME, SEARCH, FOLDERS, SETTINGS }
private enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SQLiteDatabase.loadLibs(this)
        IndexingScheduler.schedule(this)
        com.almara.app.data.FolderExpiryWorker.schedule(this)
        setContent { AlmaraApp() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlmaraApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("almara", 0) }
    val appVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0" }.getOrDefault("1.0.0") }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    var mode by remember { mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)) }
    var dark by remember { mutableStateOf(mode == ThemeMode.DARK || mode == ThemeMode.AMOLED || (mode == ThemeMode.SYSTEM && systemDark)) }
    var files by remember { mutableStateOf<List<LocalFile>>(emptyList()) }
    var views by remember { mutableStateOf(loadViews(prefs)) }
    var searchQuery by remember { mutableStateOf("") }
    var calendarCollapsed by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var tab by remember { mutableStateOf(AlmaraTab.HOME) }
    var showCreate by remember { mutableStateOf(false) }
    var showWelcome by remember { mutableStateOf(!prefs.getBoolean("welcomed", false)) }
    var optionalName by remember { mutableStateOf(prefs.getString("optional_name", "") ?: "") }
    var dynamicColor by remember { mutableStateOf(prefs.getBoolean("dynamic_color", false)) }
    var paletteRefresh by remember { mutableStateOf(0) }
    var recentFilesEnabled by remember { mutableStateOf(prefs.getBoolean("show_recent_files", true)) }
    var puffyButtons by remember { mutableStateOf(prefs.getBoolean("puffy_buttons", false)) }
    var startupSetupStage by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("Your files stay on this phone. Nothing is uploaded.") }
    var indexing by remember { mutableStateOf(false) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var conversionTarget by remember { mutableStateOf<LocalFile?>(null) }
    var pendingConversionFile by remember { mutableStateOf<File?>(null) }
    var pendingExtractionFiles by remember { mutableStateOf<List<File>?>(null) }
    var requestExtractionPicker by remember { mutableStateOf(false) }
    var pendingExpiry by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var editingFolder by remember { mutableStateOf<SavedView?>(null) }
    var timerFolder by remember { mutableStateOf<SavedView?>(null) }
    val legacyPermission = if (Build.VERSION.SDK_INT < 33) arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE) else emptyArray()
    val hasAllFilesAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    fun persistViews(next: List<SavedView>) {
        views = next
        scope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching { storeViewsInDatabase(context, next) }.isSuccess }
            if (saved) AlmaraWidgetData.refresh(context) else status = "Could not save folder rules in the encrypted library. Retry after checking storage."
        }
    }
    fun scan() {
        indexing = true
        status = "Indexing local files…"
        scope.launch {
            val scanned = withContext(Dispatchers.IO) {
                val media = runCatching { if (hasAllFilesAccess) scanAllSharedStorage(context) else readMedia(context) }.getOrElse { readMedia(context) }
                val treeFiles = prefs.getStringSet("trees", emptySet()).orEmpty().flatMap { raw -> runCatching { scanTree(context, Uri.parse(raw)) }.getOrElse { emptyList() } }
                val current = (media + treeFiles).distinctBy { it.uri.toString() }
                runCatching {
                    val db = AlmaraDatabase.open(context, SqlCipherKeyRepository(context))
                    try {
                        val now = System.currentTimeMillis()
                        db.uriMetadataDao().applyFullScan(current.map { f -> UriMetadataEntity(f.uri.toString(), f.name, f.mime, f.bytes, f.modified, f.day.toString(), f.source, now) })
                        db.uriMetadataDao().getAll().mapNotNull { e -> runCatching { LocalFile(Uri.parse(e.uri), e.displayName, e.mimeType ?: mimeFor(e.displayName), e.sizeBytes, e.modifiedAtMillis, displaySource(e.source), e.contentDate?.let(LocalDate::parse)) }.getOrNull() }
                    } finally { db.close() }
                }.getOrElse { current }
            }
            files = scanned.distinctBy { it.uri.toString() }.sortedByDescending { it.modified }
            indexing = false
            status = if (files.isEmpty()) "No files found yet. Use Android's access prompt, then refresh the index." else "${files.size} encrypted local references · files never moved"
            AlmaraWidgetData.refresh(context)
        }
    }
    val allFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
            scan()
            status = "Scanning shared storage. Files stay on this phone."
        } else {
            status = "Storage access is off. Enable it in Android settings to scan all files."
        }
        if (startupSetupStage == 1) startupSetupStage = 2
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) scan() else status = "Storage access is off. You can enable it in Android settings."
        if (startupSetupStage == 1) startupSetupStage = 2
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingExpiry?.let { (id, duration) -> if (granted) persistViews(views.map { if (it.id == id) it.copy(expiresAt = System.currentTimeMillis() + duration, archived = false) else it }) else status = "Expiry reminder permission is off." }
        pendingExpiry = null
        if (startupSetupStage == 2) startupSetupStage = 3
    }
    fun setExpiry(id: String, duration: Long) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { pendingExpiry = id to duration; notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        else persistViews(views.map { if (it.id == id) it.copy(expiresAt = System.currentTimeMillis() + duration, archived = false) else it })
    }
    val saveConversionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { destination ->
        val source = pendingConversionFile; pendingConversionFile = null
        if (source != null) scope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching { if (destination == null) false else { context.contentResolver.openOutputStream(destination)?.use { out -> source.inputStream().use { it.copyTo(out) } } ?: error("Cannot open destination"); true } }.getOrDefault(false).also { source.delete() } }
            status = if (saved) "Converted copy saved. Original unchanged." else "Save cancelled; temporary copy removed."
        }
    }
    fun runConversion(file: LocalFile, action: String) {
        conversionTarget = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val service = ConversionService(context)
                when (action) {
                    "PNG" -> service.reencodeImage(file.uri, ConversionService.ImageFormat.PNG)
                    "JPEG" -> service.reencodeImage(file.uri, ConversionService.ImageFormat.JPEG)
                    "WebP" -> service.reencodeImage(file.uri, ConversionService.ImageFormat.WEBP)
                    "Image → PDF" -> service.imagesToPdf(listOf(file.uri))
                    "PDF text → TXT" -> service.extractPdfText(file.uri)
                    "PDF pages → ZIP" -> service.rasterizePdf(file.uri).let { pages -> service.zipCacheFiles(pages).also { pages.forEach { page -> page.delete() } } }
                    "Markdown → HTML" -> service.markdownToHtml(file.uri)
                    "Markdown → PDF" -> service.markdownToPdf(file.uri)
                    "Create ZIP" -> service.createZip(listOf(ConversionService.Input(file.uri, file.name)))
                    "Extract ZIP" -> service.extractZip(file.uri)
                    else -> error("Unsupported conversion")
                }
            } }
            result.fold({ output ->
                if (output is List<*>) { pendingExtractionFiles = output.filterIsInstance<File>(); requestExtractionPicker = true; status = "Choose a destination folder for the extracted files." }
                else { val generated = output as File; pendingConversionFile = generated; saveConversionLauncher.launch(generated.name); status = "Choose where to save the converted copy." }
            }, { status = it.message ?: "Conversion failed." })
        }
    }
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val extraction = pendingExtractionFiles
            if (extraction != null) {
                pendingExtractionFiles = null
                scope.launch { val count = withContext(Dispatchers.IO) { copyExtractedFiles(context, uri, extraction) }; status = if (count > 0) "Extracted $count files to your chosen folder." else "No extracted files could be saved." }
            } else {
                runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                val trees = prefs.getStringSet("trees", emptySet()).orEmpty().toMutableSet().apply { add(uri.toString()) }
                prefs.edit().putStringSet("trees", trees).apply(); scan(); status = "Folder added. Almara only reads its contents."
            }
        }
        if (startupSetupStage == 3) startupSetupStage = 4
    }
    LaunchedEffect(requestExtractionPicker) { if (requestExtractionPicker) { requestExtractionPicker = false; treeLauncher.launch(null) } }
    LaunchedEffect(showWelcome, startupSetupStage) {
        if (!showWelcome && startupSetupStage == 0 && !prefs.getBoolean("startup_access_setup_done", false)) startupSetupStage = 1
        when (startupSetupStage) {
            1 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasAllFilesAccess) {
                allFilesLauncher.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
            } else if (legacyPermission.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) {
                permissionLauncher.launch(legacyPermission)
            } else startupSetupStage = 2
            2 -> if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else startupSetupStage = 4
            3 -> startupSetupStage = 4
            4 -> prefs.edit().putBoolean("startup_access_setup_done", true).apply()
        }
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { ConversionService(context).cleanupTemporaryOutputs() }
        val legacy = loadViews(prefs)
        val stored = withContext(Dispatchers.IO) { runCatching { loadViewsFromDatabase(context) }.getOrDefault(emptyList()) }
        if (stored.isNotEmpty()) { views = stored; prefs.edit().remove("views").apply() }
        else if (legacy.isNotEmpty()) { views = legacy; if (withContext(Dispatchers.IO) { runCatching { storeViewsInDatabase(context, legacy) }.isSuccess }) prefs.edit().remove("views").apply() }
        if (hasAllFilesAccess || legacyPermission.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } || prefs.getStringSet("trees", emptySet()).orEmpty().isNotEmpty()) scan()
    }

    val colors = remember(dynamicColor, dark, mode, paletteRefresh) {
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val wallpaper = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (mode == ThemeMode.AMOLED) wallpaper.copy(background = Color.Black, surface = Color.Black, surfaceVariant = Color(0xFF111111)) else wallpaper
        } else when (mode) {
            ThemeMode.LIGHT -> lightColorScheme(primary = Color(0xFFA4512A), onPrimary = Color.White, primaryContainer = Color(0xFFF8E4D8), onPrimaryContainer = Color(0xFF3E160A), secondary = Color(0xFF66744F), background = Color(0xFFFFFBF7), surface = Color.White)
            ThemeMode.AMOLED -> darkColorScheme(primary = Color(0xFFFFB27D), onPrimary = Color(0xFF3E160A), primaryContainer = Color(0xFF5A2A1A), onPrimaryContainer = Color(0xFFFFEBDD), secondary = Color(0xFFB7C89B), background = Color.Black, surface = Color.Black, surfaceVariant = Color(0xFF111111))
            ThemeMode.DARK -> darkColorScheme(primary = Color(0xFFFFB27D), onPrimary = Color(0xFF3E160A), primaryContainer = Color(0xFF5A2A1A), onPrimaryContainer = Color(0xFFFFEBDD), secondary = Color(0xFFB7C89B), background = Color(0xFF17110E), surface = Color(0xFF211914))
            ThemeMode.SYSTEM -> if (dark) darkColorScheme(primary = Color(0xFFFFB27D), onPrimary = Color(0xFF3E160A), primaryContainer = Color(0xFF5A2A1A), onPrimaryContainer = Color(0xFFFFEBDD), secondary = Color(0xFFB7C89B), background = Color(0xFF17110E), surface = Color(0xFF211914)) else lightColorScheme(primary = Color(0xFFA4512A), onPrimary = Color.White, primaryContainer = Color(0xFFF8E4D8), onPrimaryContainer = Color(0xFF3E160A), secondary = Color(0xFF66744F), background = Color(0xFFFFFBF7), surface = Color.White)
        }
    }
    MaterialTheme(colorScheme = colors) {
        Scaffold(bottomBar = {
            Column {
                FloatingPillNav(tab, onSelect = { tab = it })
            }
        }, floatingActionButton = { if (tab == AlmaraTab.HOME || tab == AlmaraTab.FOLDERS) FloatingActionButton(onClick = { showCreate = true }) { Icon(Icons.Rounded.Add, "Create virtual folder") } }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Almara", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Smart Sedha.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary) }
                    if (indexing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                when (tab) {
                    AlmaraTab.HOME -> {
                        Text(timeGreeting(optionalName), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                            Text("  ${month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { month = month.minusMonths(1); selectedDay = null }) { Text("‹") }
                            TextButton(onClick = { month = month.plusMonths(1); selectedDay = null }) { Text("›") }
                        }
                        AnimatedVisibility(visible = !calendarCollapsed, enter = expandVertically(), exit = shrinkVertically()) {
                            Column {
                                Heatmap(month, files, selectedDay, onSelect = { selectedDay = if (selectedDay == it) null else it; calendarCollapsed = false })
                                HeatmapScale()
                            }
                        }
                        if (calendarCollapsed && selectedDay != null) DateBadge(selectedDay!!, month)
                        Text(if (selectedDay != null) "${selectedDay!!.format(DateTimeFormatter.ofPattern("MMM d"))} · ${files.count { it.day == selectedDay }} files" else "Your files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 4.dp))
                        if (recentFilesEnabled || selectedDay != null) FileList(files, "", selectedDay, expanded = !calendarCollapsed, onScrollChanged = { movingUp -> if (selectedDay != null) calendarCollapsed = movingUp }, onOpen = { openFile(context, it) }, onShare = { shareFile(context, it) }, onDeleteReference = { file -> files = files.filterNot { it.uri == file.uri }; status = "Removed from this view. The original file is untouched." })
                    }
                    AlmaraTab.SEARCH -> {
                        Text("Find something without remembering where it went.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp))
                        SearchBar(searchQuery, { searchQuery = it }, { tab = AlmaraTab.SEARCH })
                        FileList(files, searchQuery, null, expanded = true, onScrollChanged = {}, onOpen = { openFile(context, it) }, onShare = { shareFile(context, it) }, onDeleteReference = { file -> files = files.filterNot { it.uri == file.uri } })
                    }
                    AlmaraTab.FOLDERS -> {
                        Text("Virtual folders are saved rules. Your files stay put.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
                        if (views.isEmpty()) EmptyMessage("No virtual folders yet.", "Create one with +. Files are never copied or moved.")
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(views, key = { it.id }) { folder ->
                            var menu by remember(folder.id) { mutableStateOf(false) }
                            Card(Modifier.fillMaxWidth().combinedClickable(onClick = { selectedFolderId = folder.id; searchQuery = folderQuery(folder); tab = AlmaraTab.SEARCH }, onLongClick = { selectedFolderId = folder.id }), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(10.dp))
                                        Text(folder.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (folder.expiresAt > 0) Text("◷", color = MaterialTheme.colorScheme.secondary)
                                        Box {
                                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Folder actions") }
                                            DropdownMenu(menu, { menu = false }) {
                                                DropdownMenuItem(text = { Text("Open") }, onClick = { menu = false; selectedFolderId = folder.id; searchQuery = folderQuery(folder); tab = AlmaraTab.SEARCH })
                                                DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; editingFolder = folder })
                                                DropdownMenuItem(text = { Text("Timer") }, onClick = { menu = false; timerFolder = folder })
                                                DropdownMenuItem(text = { Text("Archive") }, onClick = { menu = false; persistViews(views.map { if (it.id == folder.id) it.copy(archiveOnExpiry = !it.archiveOnExpiry) else it }) })
                                                DropdownMenuItem(text = { Text("Remove") }, onClick = { menu = false; persistViews(views.filterNot { it.id == folder.id }); selectedFolderId = null })
                                            }
                                        }
                                    }
                                    Text("${folder.source} · ${folder.kind} · ${periodLabel(folder.period)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val count = remember(files, folder) { runCatching { filterFiles(files, folderQuery(folder), null).size }.getOrDefault(0) }
                                    Text("$count files", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        } }
                        selectedFolderId?.let { id -> Text("Long-press selected folder to remove its saved rule. Original files are always preserved.", style = MaterialTheme.typography.bodySmall) }
                    }
                    AlmaraTab.SETTINGS -> SettingsScreen(mode, { selected -> mode = selected; dark = selected == ThemeMode.DARK || selected == ThemeMode.AMOLED || (selected == ThemeMode.SYSTEM && systemDark); prefs.edit().putString("theme", selected.name).apply(); AlmaraWidgetData.refresh(context) }, dynamicColor, { enabled -> dynamicColor = enabled; prefs.edit().putBoolean("dynamic_color", enabled).apply(); paletteRefresh++; AlmaraWidgetData.refresh(context) }, puffyButtons, { enabled -> puffyButtons = enabled; prefs.edit().putBoolean("puffy_buttons", enabled).apply() }, recentFilesEnabled, { enabled -> recentFilesEnabled = enabled; prefs.edit().putBoolean("show_recent_files", enabled).apply() }, onWallpaperRefresh = { dynamicColor = true; prefs.edit().putBoolean("dynamic_color", true).apply(); paletteRefresh++; AlmaraWidgetData.refresh(context) }, onRefresh = { scan() }, onAddFolder = { treeLauncher.launch(null) }, onCheckUpdates = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/vijayarkananduri/Almara/releases"))) } }, fileCount = files.size)
                }
            }
        }
        if (showCreate) CreateViewDialog(files, onDismiss = { showCreate = false }, onCreate = { view -> persistViews(views + view.copy(id = java.util.UUID.randomUUID().toString())); showCreate = false; status = "Maa would be proud." })
        editingFolder?.let { original -> CreateViewDialog(files, initial = original, onDismiss = { editingFolder = null }, onCreate = { updated -> persistViews(views.map { if (it.id == original.id) updated.copy(id = original.id, expiresAt = original.expiresAt, archiveOnExpiry = original.archiveOnExpiry) else it }); editingFolder = null; status = "Folder updated." }) }
        timerFolder?.let { folder -> TimerDialog(folder, onDismiss = { timerFolder = null }, onSet = { duration -> setExpiry(folder.id, duration); timerFolder = null }, onClear = { persistViews(views.map { if (it.id == folder.id) it.copy(expiresAt = 0L, archiveOnExpiry = false) else it }); timerFolder = null }) }
        if (showWelcome) AlertDialog(onDismissRequest = { showWelcome = false; prefs.edit().putBoolean("welcomed", true).putString("optional_name", optionalName.trim()).apply() }, title = { Text("A tidier digital almara") }, text = { Column { Text("Remember maa's nudge — apna almara seedha karo? Files stay on your phone. No account. No cloud."); Text("Almara will ask for All files access so it can scan images, videos, PDFs, documents, spreadsheets, presentations, and archives across shared storage. You can add a specific folder later if needed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary); OutlinedTextField(optionalName, { optionalName = it.take(40) }, label = { Text("Your name (optional)") }) } }, confirmButton = { TextButton(onClick = { showWelcome = false; prefs.edit().putBoolean("welcomed", true).putString("optional_name", optionalName.trim()).apply() }) { Text("Continue") } }, dismissButton = { TextButton(onClick = { optionalName = ""; showWelcome = false; prefs.edit().putBoolean("welcomed", true).putString("optional_name", "").apply() }) { Text("Skip") } })
    }
}

@Composable private fun FloatingPillNav(selected: AlmaraTab, onSelect: (AlmaraTab) -> Unit) {
    val entries = listOf(AlmaraTab.HOME to Icons.Rounded.Home, AlmaraTab.SEARCH to Icons.Rounded.Search, AlmaraTab.FOLDERS to Icons.Rounded.Folder, AlmaraTab.SETTINGS to Icons.Rounded.Settings)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)),
        tonalElevation = 3.dp
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(62.dp).padding(5.dp)) {
            val slot = maxWidth / entries.size
            val indicatorX by animateDpAsState(5.dp + slot * selected.ordinal, animationSpec = spring(dampingRatio = .72f, stiffness = 520f), label = "liquid-nav-x")
            Surface(
                modifier = Modifier.offset(x = indicatorX).width(slot - 4.dp).fillMaxHeight().graphicsLayer { scaleX = 1.02f },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp
            ) {}
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                entries.forEach { (destination, icon) ->
                    val active = selected == destination
                    val content by animateColorAsState(if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, label = "nav-content")
                    Surface(onClick = { onSelect(destination) }, modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 2.dp), color = Color.Transparent, shape = RoundedCornerShape(20.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(icon, contentDescription = destination.name.lowercase(), tint = content, modifier = Modifier.size(if (active) 21.dp else 20.dp))
                            Text(destination.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun DepthActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 50.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp, pressedElevation = 1.dp, focusedElevation = 4.dp, hoveredElevation = 6.dp)
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

private fun timeGreeting(name: String): String {
    val hour = LocalTime.now().hour
    val greeting = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..20 -> "Good evening"; else -> "Good night" }
    return if (name.isBlank()) "$greeting. Ready to get things in order?" else "$greeting, ${name.trim()}. Ready to get things in order?"
}

@Composable private fun HeatmapScale() {
    Column(Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("Files per day · darker means more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("0", "1–3", "4–10", "11–25", "26+").forEachIndexed { index, value ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(8.dp).background(if (index == 0) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary.copy(alpha = listOf(0f, .24f, .46f, .70f, 1f)[index]), RoundedCornerShape(8.dp)))
                    Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

@Composable private fun SearchBar(value: String, onChange: (String) -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val accent by animateColorAsState(if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, label = "search-accent")
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth().padding(vertical = 8.dp).onFocusChanged { focused = it.isFocused }, singleLine = true, placeholder = { Text("Search your files") }, leadingIcon = { Icon(Icons.Rounded.Search, null, tint = accent) }, shape = RoundedCornerShape(20.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = accent.copy(alpha = .65f), focusedLeadingIconColor = accent), trailingIcon = { TextButton(onClick = onClick) { Text("Search") } })
}
@Composable private fun ViewChips(views: List<SavedView>, onSelect: (String) -> Unit) { Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { views.take(3).forEach { view -> FilterChip(false, { onSelect(folderQuery(view)) }, label = { Text(view.title, maxLines = 1) }, leadingIcon = { Icon(Icons.Rounded.Folder, null, Modifier.size(16.dp)) }) } } }

@Composable private fun DateBadge(day: LocalDate, month: YearMonth) {
    val column = (month.atDay(1).dayOfWeek.value - 1 + day.dayOfMonth - 1) % 7
    val alignment = if (column >= 4) Alignment.CenterEnd else Alignment.CenterStart
    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp).offset(x = if (column >= 4) 18.dp else (-18).dp), contentAlignment = alignment) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 4.dp, shadowElevation = 3.dp) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(day.format(DateTimeFormatter.ofPattern("MMM")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(day.year.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun Heatmap(month: YearMonth, files: List<LocalFile>, selected: LocalDate?, onSelect: (LocalDate) -> Unit) {
    val counts = remember(files, month) { files.filter { YearMonth.from(it.day) == month }.groupingBy { it.day }.eachCount() }
    val offset = month.atDay(1).dayOfWeek.value - 1
    val rows = (offset + month.lengthOfMonth() + 6) / 7
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp)).padding(horizontal = 14.dp, vertical = 13.dp)) {
        Row(Modifier.fillMaxWidth()) { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) } }
        repeat(rows) { week -> Row(Modifier.fillMaxWidth()) { repeat(7) { col ->
            val n = week * 7 + col - offset + 1
            if (n in 1..month.lengthOfMonth()) { val day = month.atDay(n); val count = counts[day] ?: 0; val alpha = when { count == 0 -> .08f; count <= 3 -> .24f; count <= 10 -> .46f; count <= 25 -> .7f; else -> 1f }; val color by animateColorAsState(if (selected == day) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary.copy(alpha = alpha), label = "heat")
                Box(Modifier.weight(1f).padding(2.dp).height(42.dp).background(color, RoundedCornerShape(12.dp)).combinedClickable(onClick = { onSelect(day) }), contentAlignment = Alignment.Center) { Text("$n", style = MaterialTheme.typography.labelMedium, fontWeight = if (selected == day) FontWeight.Bold else FontWeight.Medium, color = if (count > 10 || selected == day) Color.White else MaterialTheme.colorScheme.onSurface) }
            } else Spacer(Modifier.weight(1f).padding(2.dp).height(42.dp))
        } } }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun FileList(files: List<LocalFile>, query: String, day: LocalDate?, expanded: Boolean, onScrollChanged: (Boolean) -> Unit, onOpen: (LocalFile) -> Unit, onShare: (LocalFile) -> Unit, onDeleteReference: (LocalFile) -> Unit) {
    val (shown, error) = remember(files, query, day) { runCatching { filterFiles(files, query, day) }.fold({ it to null }, { emptyList<LocalFile>() to (it.message ?: "Search could not be parsed") }) }
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        var previous = 0
        snapshotFlow { listState.firstVisibleItemIndex * 10000 + listState.firstVisibleItemScrollOffset }.collect { position ->
            if (kotlin.math.abs(position - previous) > 2) onScrollChanged(position > previous)
            previous = position
        }
    }
    if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    if (shown.isEmpty()) { EmptyMessage(if (files.isEmpty()) "Your almara is empty." else "No matching files.", if (files.isEmpty()) "Allow file access during setup, then refresh the index." else "Try another search or date.") }
    else {
        if (shown.size > 6) Text("Scroll to see more files", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(bottom = 4.dp))
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().fillMaxHeight(if (expanded) 0.72f else 0.90f), contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(shown, key = { it.uri.toString() }) { file -> var menu by remember { mutableStateOf(false) }; var radial by remember { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth().combinedClickable(onClick = { onOpen(file) }, onLongClick = { radial = true }), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(fileIcon(file.mime), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp).padding(end = 8.dp))
                    Column(Modifier.weight(1f)) { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium); Text("${file.source} · ${file.day.format(DateTimeFormatter.ofPattern("MMM d"))} · ${formatBytes(file.bytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
                    Box { IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "File actions") }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("Open") }, onClick = { menu = false; onOpen(file) }); DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; onShare(file) }); DropdownMenuItem(text = { Text("Remove from this view") }, onClick = { menu = false; onDeleteReference(file) }) } }
                }
            }
            if (radial) AlertDialog(
                onDismissRequest = { radial = false },
                title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                text = { Text("Choose an action. The original file stays where Android stores it.", style = MaterialTheme.typography.bodySmall) },
                confirmButton = { TextButton(onClick = { radial = false; onOpen(file) }) { Text("Open") } },
                dismissButton = { Row { TextButton(onClick = { radial = false; onShare(file) }) { Text("Share") }; TextButton(onClick = { radial = false; onDeleteReference(file) }) { Text("Remove reference") } } }
            )
        }
    }
}
}

@Composable private fun EmptyMessage(title: String, detail: String) { Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) } }

@Composable private fun SettingsScreen(mode: ThemeMode, onMode: (ThemeMode) -> Unit, dynamicColor: Boolean, onDynamicColor: (Boolean) -> Unit, puffyButtons: Boolean, onPuffyButtons: (Boolean) -> Unit, recentFilesEnabled: Boolean, onRecentFiles: (Boolean) -> Unit, onWallpaperRefresh: () -> Unit, onRefresh: () -> Unit, onAddFolder: () -> Unit, onCheckUpdates: () -> Unit, fileCount: Int) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Appearance", style = MaterialTheme.typography.titleLarge); Text("Choose how Almara feels on this phone.", style = MaterialTheme.typography.bodyMedium) }
        item { Column { ThemeMode.values().forEach { option -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(mode == option, { onMode(option) }); Text(when(option) { ThemeMode.SYSTEM -> "System"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark"; ThemeMode.AMOLED -> "AMOLED · pure black" }, Modifier.weight(1f)); if (mode == option) Text("✓") } } } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Wallpaper colors", fontWeight = FontWeight.Medium); Text("Use your current Android wallpaper palette.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }; Switch(dynamicColor, onDynamicColor, enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) } }
        item { OutlinedButton(onClick = onWallpaperRefresh, modifier = Modifier.fillMaxWidth(), enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { Text("Refresh wallpaper accent") } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Show recent files", fontWeight = FontWeight.Medium); Text("Show or hide the recent list below the calendar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }; Switch(recentFilesEnabled, onRecentFiles) } }
        item { HorizontalDivider(); Text("Local library", style = MaterialTheme.typography.titleLarge); Text("$fileCount indexed files", style = MaterialTheme.typography.bodyMedium); OutlinedButton(onClick = onAddFolder, modifier = Modifier.fillMaxWidth()) { Text("Add a specific folder") }; Text("All files access scans shared storage automatically. Use this only for a provider folder Android keeps separate.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
        item { DepthActionButton("Refresh index", onRefresh) }
        item { TextButton(onClick = onCheckUpdates, modifier = Modifier.fillMaxWidth()) { Text("Check GitHub releases") } }
        item { Text("File and notification access are requested during first setup. Almara reads only files you allow and never changes originals.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
    }
}

@Composable private fun CreateViewDialog(files: List<LocalFile>, initial: SavedView? = null, onDismiss: () -> Unit, onCreate: (SavedView) -> Unit) {
    val sourceOptions = listOf("All", "WhatsApp", "Telegram", "Camera", "Screenshot", "Download", "Other")
    val typeOptions = listOf("All", "Images", "Videos", "Audio", "PDFs", "Docs", "Archives", "Other")
    var sources by remember(initial) { mutableStateOf(initial?.source?.split(",")?.filter(String::isNotBlank)?.toSet()?.takeIf { it.isNotEmpty() } ?: setOf("All")) }
    var kinds by remember(initial) { mutableStateOf(initial?.kind?.split(",")?.filter(String::isNotBlank)?.toSet()?.takeIf { it.isNotEmpty() } ?: setOf("All")) }
    var period by remember(initial) { mutableStateOf(if (initial?.period?.startsWith("Custom:") == true) "Custom range" else initial?.period ?: "This month") }; var name by remember(initial) { mutableStateOf(initial?.title ?: "") }; var nameContains by remember(initial) { mutableStateOf(initial?.nameContains ?: "") }
    val customParts = initial?.period?.removePrefix("Custom:")?.split("..")
    var customStart by remember(initial) { mutableStateOf(customParts?.getOrNull(0) ?: LocalDate.now().withDayOfMonth(1).toString()) }
    var customEnd by remember(initial) { mutableStateOf(customParts?.getOrNull(1) ?: LocalDate.now().toString()) }
    var expiry by remember(initial) { mutableStateOf(if ((initial?.expiresAt ?: 0L) > System.currentTimeMillis()) "Keep for a day" else "Never") }
    var moreOptions by remember(initial) { mutableStateOf(false) }
    val savedPeriod = if (period == "Custom range") "Custom:$customStart..$customEnd" else period
    val sourceValue = if ("All" in sources) "All" else sourceOptions.filter { it in sources }.joinToString(",")
    val kindValue = if ("All" in kinds) "All" else typeOptions.filter { it in kinds }.joinToString(",")
    val sourceSummary = if ("All" in sources) "All sources" else if (sources.size > 1) "Selected sources" else sourceValue
    val typeSummary = if ("All" in kinds) "All files" else if (kinds.size > 1) "Selected types" else kindValue
    val suggestion = "$sourceSummary · $typeSummary · ${if (period == "Custom range") "Custom range" else period}"
    val ruleQuery = folderQuery(SavedView("", "", sourceValue, kindValue, savedPeriod, nameContains = nameContains))
    val matchingFiles = remember(files, sourceValue, kindValue, period, customStart, customEnd, nameContains) { runCatching { filterFiles(files, ruleQuery, null) }.getOrDefault(emptyList()) }
    fun toggle(current: Set<String>, choice: String): Set<String> = when { choice == "All" -> setOf("All"); "All" in current -> setOf(choice); choice in current -> (current - choice).ifEmpty { setOf("All") }; else -> current + choice }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "New virtual folder" else "Edit virtual folder") }, text = { Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text("${matchingFiles.size} matching files", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer); Text(if (matchingFiles.isEmpty()) "Your choices will preview here." else matchingFiles.take(3).joinToString(" · ") { it.name }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis) } }
        Text("Choose sources · multiple allowed", fontWeight = FontWeight.SemiBold); MultiSelectChoices(sourceOptions, sources) { sources = toggle(sources, it) }
        Text("Choose file types · multiple allowed", fontWeight = FontWeight.SemiBold); MultiSelectChoices(typeOptions, kinds) { kinds = toggle(kinds, it) }
        Text("Time range", fontWeight = FontWeight.SemiBold); ChipChoices(listOf("Today", "This week", "This month", "Custom range"), period) { period = it }
        if (period == "Custom range") { OutlinedTextField(customStart, { customStart = it }, label = { Text("From · YYYY-MM-DD") }, singleLine = true); OutlinedTextField(customEnd, { customEnd = it }, label = { Text("To · YYYY-MM-DD") }, singleLine = true) }
        OutlinedTextField(name, { name = it }, label = { Text("Folder name") }, placeholder = { Text(suggestion) }, singleLine = true)
        TextButton(onClick = { moreOptions = !moreOptions }, modifier = Modifier.fillMaxWidth()) { Text(if (moreOptions) "Fewer options" else "More options") }
        if (moreOptions) {
            OutlinedTextField(nameContains, { nameContains = it }, label = { Text("Name contains") }, placeholder = { Text("invoice, project, notes…") }, singleLine = true)
            Text("Expiry", fontWeight = FontWeight.SemiBold)
            ChipChoices(listOf("Never", "Keep for a day", "Keep for a week"), expiry) { expiry = it }
        }
    } }, confirmButton = { DepthActionButton(if (initial == null) "Create folder" else "Save folder", onClick = { runCatching { LocalDate.parse(customStart); LocalDate.parse(customEnd) }.onSuccess { start -> val end = runCatching { LocalDate.parse(customEnd) }.getOrNull(); if (period != "Custom range" || (end != null && !end.isBefore(start))) { val timerMs = when (expiry) { "Keep for a day" -> 24L * 60 * 60 * 1000; "Keep for a week" -> 7L * 24 * 60 * 60 * 1000; else -> 0L }; onCreate(SavedView(initial?.id ?: "", name.ifBlank { suggestion }, sourceValue, kindValue, savedPeriod, if (timerMs > 0) System.currentTimeMillis() + timerMs else 0L, initial?.archived ?: false, initial?.archiveOnExpiry ?: false, nameContains.trim())) } } }) }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
@Composable private fun MultiSelectChoices(values: List<String>, selected: Set<String>, choose: (String) -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(1.dp)) { values.chunked(3).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) { row.forEach { value -> FilterChip(value in selected, { choose(value) }, modifier = Modifier.weight(1f), label = { Text(value, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) }) }; if (row.size < 3) repeat(3 - row.size) { Spacer(Modifier.weight(1f)) } } } } }
@Composable private fun ChipChoices(values: List<String>, selected: String, choose: (String) -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(1.dp)) { values.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { row.forEach { value -> FilterChip(selected == value, { choose(value) }, modifier = Modifier.weight(1f), label = { Text(value, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) }) }; if (row.size == 1) Spacer(Modifier.weight(1f)) } } } }

private fun scanAllSharedStorage(context: android.content.Context): List<LocalFile> {
    val root = Environment.getExternalStorageDirectory()
    val output = mutableListOf<LocalFile>()
    root.walkTopDown()
        .onEnter { dir -> dir.name != "Android" || dir.resolve("data").exists().not() }
        .forEach { file ->
            if (!file.isFile || !file.canRead()) return@forEach
            val mime = mimeFor(file.name)
            if (!isVisibleFile(file.name, mime)) return@forEach
            val uri = runCatching { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }.getOrNull() ?: return@forEach
            val relative = runCatching { file.relativeTo(root).invariantSeparatorsPath }.getOrDefault(file.name)
            output += LocalFile(uri, file.name, mime, file.length(), file.lastModified(), sourceFor(relative, file.name))
        }
    return output
}

private fun readMedia(context: android.content.Context): List<LocalFile> {
    val resolver = context.contentResolver
    val result = mutableListOf<LocalFile>()
    val collection = MediaStore.Files.getContentUri("external")
    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.MIME_TYPE,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.DATE_MODIFIED,
        MediaStore.MediaColumns.RELATIVE_PATH
    )
    runCatching {
        resolver.query(collection, projection, null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { cursor ->
            val idIx = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
            val nameIx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeIx = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val sizeIx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val dateIx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val pathIx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                if (idIx < 0) continue
                val name = if (nameIx >= 0) cursor.getString(nameIx).orEmpty() else "Untitled"
                val mime = if (mimeIx >= 0) cursor.getString(mimeIx).orEmpty().ifBlank { mimeFor(name) } else mimeFor(name)
                if (!isVisibleFile(name, mime)) continue
                val path = if (pathIx >= 0) cursor.getString(pathIx).orEmpty() else ""
                val modified = if (dateIx >= 0) cursor.getLong(dateIx) * 1000L else System.currentTimeMillis()
                result += LocalFile(ContentUris.withAppendedId(collection, cursor.getLong(idIx)), name, mime, if (sizeIx >= 0) cursor.getLong(sizeIx) else 0L, modified, sourceFor(path, name))
            }
        }
    }
    return result
}
private fun isVisibleFile(name: String, mime: String?): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return mime?.startsWith("image/") == true || mime?.startsWith("audio/") == true || mime?.startsWith("video/") == true ||
        ext in setOf("pdf", "doc", "docx", "odt", "rtf", "txt", "md", "xls", "xlsx", "csv", "ods", "ppt", "pptx", "odp", "zip", "tar", "gz", "rar", "7z", "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "mp4", "mkv", "webm", "mov", "avi", "mp3", "opus", "ogg", "wav", "m4a", "aac", "flac")
}

private fun scanTree(context: android.content.Context, tree: Uri): List<LocalFile> {
    val root = DocumentFile.fromTreeUri(context, tree) ?: return emptyList(); val output = mutableListOf<LocalFile>(); val queue = ArrayDeque<Pair<DocumentFile, String>>(); queue.add(root to root.name.orEmpty()); var visited = 0
    while (queue.isNotEmpty() && visited < 20_000) { val (node, relativePath) = queue.removeFirst(); visited++; if (node.isDirectory) node.listFiles().forEach { child -> val name = child.name.orEmpty(); val childPath = listOf(relativePath, name).filter(String::isNotBlank).joinToString("/"); if (child.isDirectory) queue.add(child to childPath) else if (child.isFile) { val displayName = child.name ?: "Untitled"; val mime = child.type ?: mimeFor(displayName); if (isVisibleFile(displayName, mime)) { val modified = child.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis(); output += LocalFile(child.uri, displayName, mime, child.length(), modified, sourceFor(childPath, displayName)) } } } }
    return output
}
private fun mimeFor(name: String): String = when(name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
    "pdf" -> "application/pdf"; "txt" -> "text/plain"; "md" -> "text/markdown"; "zip" -> "application/zip";
    "doc" -> "application/msword"; "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    "xls" -> "application/vnd.ms-excel"; "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; "csv" -> "text/csv";
    "ppt" -> "application/vnd.ms-powerpoint"; "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    "odt" -> "application/vnd.oasis.opendocument.text"; "ods" -> "application/vnd.oasis.opendocument.spreadsheet"; "odp" -> "application/vnd.oasis.opendocument.presentation";
    "rtf" -> "application/rtf"; "tar" -> "application/x-tar"; "gz" -> "application/gzip";
    else -> "application/octet-stream"
}
private fun conversionActions(file: LocalFile): List<String> = when {
    file.mime.startsWith("image/") -> listOf("PNG", "JPEG", "WebP", "Image → PDF", "Create ZIP")
    file.mime == "application/pdf" -> listOf("PDF text → TXT", "PDF pages → ZIP", "Create ZIP")
    file.name.endsWith(".md", true) -> listOf("Markdown → HTML", "Markdown → PDF", "Create ZIP")
    file.name.endsWith(".zip", true) -> listOf("Extract ZIP", "Create ZIP")
    else -> listOf("Create ZIP")
}
private fun copyExtractedFiles(context: android.content.Context, treeUri: Uri, files: List<File>): Int {
    val destination = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
    val root = files.firstOrNull()?.let { file -> generateSequence(file.parentFile) { it.parentFile }.firstOrNull { it.name.startsWith("extract-") } }
    var copied = 0
    files.forEach { file -> runCatching {
        if (root == null || !file.canonicalPath.startsWith(root.canonicalPath + File.separator)) return@runCatching
        val parts = file.relativeTo(root).invariantSeparatorsPath.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return@runCatching
        var parent = destination
        parts.dropLast(1).forEach { segment -> parent = parent.findFile(segment) ?: parent.createDirectory(segment) ?: error("Could not create destination folder") }
        val outFile = parent.createFile(mimeFor(file.name), parts.last()) ?: error("Could not create destination file")
        context.contentResolver.openOutputStream(outFile.uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("Could not write destination file")
        copied++
    } }
    root?.deleteRecursively()
    return copied
}
private fun sourceFor(path: String, name: String): String { val v = "$path/$name"; return when { v.contains("WhatsApp", true) -> "WhatsApp"; v.contains("Telegram", true) -> "Telegram"; v.contains("Screenshots", true) -> "Screenshot"; v.contains("DCIM/Camera", true) -> "Camera"; v.contains("Download", true) -> "Download"; else -> "Other" } }
private fun filterFiles(files: List<LocalFile>, query: String, day: LocalDate?): List<LocalFile> {
    val parsed = parseQuery(query)
    val rule = parsed.query ?: throw IllegalArgumentException(parsed.error ?: "Invalid query")
    return files.filter { file -> (day == null || file.day == day) && rule.matches(FileRecord(file.name, file.uri.toString(), Source.parse(file.source) ?: Source.OTHER, file.day, FileType.fromName(file.name), file.bytes)) }
}
private fun dateMatches(day: LocalDate, value: String): Boolean { val now = LocalDate.now(); return when(value.lowercase()) { "today" -> day == now; "yesterday" -> day == now.minusDays(1); "last-week" -> day in now.minusDays(7)..now; "this-week" -> day >= now.with(java.time.DayOfWeek.MONDAY); "last-month" -> YearMonth.from(day) == YearMonth.now().minusMonths(1); "this-month" -> YearMonth.from(day) == YearMonth.now(); else -> runCatching { if (value.length == 7) YearMonth.parse(value) == YearMonth.from(day) else LocalDate.parse(value) == day }.getOrElse { throw IllegalArgumentException("Invalid date '$value'. Use today, this-week, YYYY-MM-DD, or YYYY-MM.") } } }
private fun sizeMatches(bytes: Long, value: String): Boolean { val m = Regex("([<>])(\\d+)(kb|mb|gb)", RegexOption.IGNORE_CASE).matchEntire(value) ?: throw IllegalArgumentException("Invalid size '$value'. Example: size:>5mb"); val factor = when(m.groupValues[3].lowercase()) { "kb" -> 1024L; "mb" -> 1024L*1024; else -> 1024L*1024*1024 }; val amount = m.groupValues[2].toLongOrNull() ?: throw IllegalArgumentException("Size is too large."); return if (m.groupValues[1] == ">") bytes > amount * factor else bytes < amount * factor }
private fun folderQuery(v: SavedView): String {
    val sources = if (v.source == "All") emptyList() else v.source.split(',').map { when (it.trim().lowercase(Locale.ROOT)) { "whatsapp" -> "whatsapp"; "telegram" -> "telegram"; "camera" -> "camera"; "screenshot" -> "screenshot"; "download" -> "download"; "other" -> "other"; else -> it.trim().lowercase(Locale.ROOT) } }
    val types = if (v.kind == "All") emptyList() else v.kind.split(',').flatMap { label -> when (label.trim().lowercase(Locale.ROOT)) { "docs", "documents" -> listOf("pdf", "document", "spreadsheet", "presentation"); "images", "image" -> listOf("image"); "videos", "video" -> listOf("video"); "audio" -> listOf("audio"); "pdfs", "pdf" -> listOf("pdf"); "archives", "archive" -> listOf("archive"); "other" -> listOf("other"); else -> listOf(label.trim().lowercase(Locale.ROOT).removeSuffix("s")) } }.distinct()
    val name = v.nameContains.trim().replace("\"", "")
    return listOfNotNull(
        sources.takeIf { it.isNotEmpty() }?.let { "source:${it.joinToString(",")}" },
        types.takeIf { it.isNotEmpty() }?.let { "type:${it.joinToString(",")}" },
        name.takeIf { it.isNotEmpty() }?.let { "name:\"$it\"" },
        when {
            v.period.startsWith("Custom:") -> "date:${v.period.removePrefix("Custom:")}"
            v.period == "Today" -> "date:today"
            v.period == "This week" -> "date:this-week"
            v.period == "This month" -> "date:this-month"
            v.period == "Last week" -> "date:last-week"
            v.period == "Last month" -> "date:last-month"
            else -> null
        }
    ).joinToString(" ")
}
private fun periodLabel(value: String): String = if (value.startsWith("Custom:")) "Custom · ${value.removePrefix("Custom:").replace("..", " – ")}" else value
private fun openFile(context: android.content.Context, file: LocalFile) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(file.uri, file.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) } }
private fun shareFile(context: android.content.Context, file: LocalFile) { runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(file.mime).putExtra(Intent.EXTRA_STREAM, file.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share file")) } }
private fun fileIcon(mime: String): ImageVector = when { mime.startsWith("image/") -> Icons.Rounded.Image; mime == "application/pdf" -> Icons.Rounded.Description; mime.startsWith("audio/") -> Icons.Rounded.AudioFile; mime.startsWith("video/") -> Icons.Rounded.VideoFile; mime.contains("spreadsheet") || mime == "text/csv" -> Icons.Rounded.TableChart; mime.contains("presentation") -> Icons.Rounded.Slideshow; mime.contains("zip") || mime.contains("archive") -> Icons.Rounded.Archive; else -> Icons.Rounded.Description }
private fun formatBytes(bytes: Long) = when { bytes >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_000_000.0); bytes >= 1_000 -> "${bytes / 1_000} KB"; else -> "$bytes B" }
private fun displaySource(value: String): String = value.split(',').map { when (it.trim().lowercase(Locale.ROOT)) { "whatsapp" -> "WhatsApp"; "telegram" -> "Telegram"; "camera" -> "Camera"; "download" -> "Download"; "screenshot" -> "Screenshot"; "other" -> "Other"; else -> it.trim().replaceFirstChar { ch -> ch.uppercase() } } }.distinct().joinToString(",")
private fun displayTypes(value: String): String {
    val types = value.split(',').map { it.trim().lowercase(Locale.ROOT) }.toSet()
    val result = mutableListOf<String>()
    if (setOf("pdf", "document", "spreadsheet", "presentation").all { it in types }) result += "Docs"
    types.forEach { type -> when (type) { "document", "spreadsheet", "presentation" -> if ("Docs" !in result) result += "Docs"; "image" -> result += "Images"; "video" -> result += "Videos"; "audio" -> result += "Audio"; "pdf" -> if ("Docs" !in result) result += "PDFs"; "archive" -> result += "Archives"; "other" -> result += "Other" } }
    return result.distinct().joinToString(",").ifBlank { "All" }
}
private fun loadViews(prefs: android.content.SharedPreferences): List<SavedView> = prefs.getStringSet("views", emptySet()).orEmpty().mapNotNull { raw -> raw.split("|", limit = 9).takeIf { it.size >= 5 }?.let { SavedView(it[0], it[1], it[2], it[3], it[4], it.getOrNull(5)?.toLongOrNull() ?: 0L, it.getOrNull(6)?.toBoolean() ?: false, it.getOrNull(7)?.toBoolean() ?: false, it.getOrNull(8).orEmpty()) } }

@Composable private fun TimerDialog(folder: SavedView, onDismiss: () -> Unit, onSet: (Long) -> Unit, onClear: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Folder timer") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Only this saved folder expires. Original files are never touched."); Text("Choose when to remove this view:", style = MaterialTheme.typography.bodySmall) } }, confirmButton = { Row { TextButton(onClick = { onSet(60L * 60 * 1000) }) { Text("1 hour") }; TextButton(onClick = { onSet(24L * 60 * 60 * 1000) }) { Text("1 day") }; TextButton(onClick = { onSet(7L * 24 * 60 * 60 * 1000) }) { Text("1 week") } } }, dismissButton = { Row { if (folder.expiresAt > 0) TextButton(onClick = onClear) { Text("Cancel timer") }; TextButton(onClick = onDismiss) { Text("Close") } } })
}

private suspend fun storeViewsInDatabase(context: android.content.Context, views: List<SavedView>) {
    SQLiteDatabase.loadLibs(context)
    val db = AlmaraDatabase.open(context, SqlCipherKeyRepository(context))
    try {
        val now = System.currentTimeMillis()
        db.virtualRuleDao().clear(); db.folderDao().clear()
        val rules = views.map { VirtualRuleEntity(it.id, it.title, folderQuery(it), now, now) }
        if (rules.isNotEmpty()) db.virtualRuleDao().upsertAll(rules)
        views.forEach { view -> db.folderDao().upsert(FolderEntity(view.id, view.title, now, now, view.id, view.expiresAt.takeIf { it > 0 }, view.archiveOnExpiry, if (view.archived) now + 7L * 24 * 60 * 60 * 1000 else null)) }
    } finally { db.close() }
}

private suspend fun loadViewsFromDatabase(context: android.content.Context): List<SavedView> {
    SQLiteDatabase.loadLibs(context)
    val db = AlmaraDatabase.open(context, SqlCipherKeyRepository(context))
    try {
        val rules = db.virtualRuleDao().getAll().associateBy { it.id }
        return db.folderDao().getAll().mapNotNull { folder ->
            val rule = rules[folder.ruleId ?: folder.id] ?: return@mapNotNull null
            val clauses = rule.query.split(Regex("\\s+")).filter { it.contains(':') }.associate { it.substringBefore(':') to it.substringAfter(':') }
            SavedView(folder.id, folder.name, clauses["source"]?.let(::displaySource) ?: "All", clauses["type"]?.let(::displayTypes) ?: "All", when (val date = clauses["date"]) { "today" -> "Today"; "this-week" -> "This week"; "last-week" -> "Last week"; "last-month" -> "Last month"; else -> if (date?.contains("..") == true) "Custom:$date" else "This month" }, folder.expiresAtMillis ?: 0L, folder.archivedUntilMillis != null, folder.archiveOnExpiry, clauses["name"]?.trim('"').orEmpty())
        }
    } finally { db.close() }
}
