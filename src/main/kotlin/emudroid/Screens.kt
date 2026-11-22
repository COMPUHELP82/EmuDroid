package emudroid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

// ---------------- Tema ----------------

val Green = Color(0xFF3DDC84)
val Amber = Color(0xFFFFB74D)
val Red = Color(0xFFFF6B6B)

@Composable
fun EmuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Green,
            onPrimary = Color(0xFF00210E),
            secondary = Color(0xFF8AB4F8),
            background = Color(0xFF0E1014),
            onBackground = Color(0xFFE6E8EC),
            surface = Color(0xFF161920),
            onSurface = Color(0xFFE6E8EC),
            surfaceVariant = Color(0xFF1F232C),
            onSurfaceVariant = Color(0xFF9AA1AD),
            outline = Color(0xFF3A404C),
            error = Red,
        ),
        content = content,
    )
}

// ---------------- Estructura principal ----------------

@Composable
fun MainLayout(app: AppState, frame: Frame) {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(app.snackbar) {
        app.snackbar?.let {
            snack.showSnackbar(it)
            app.snackbar = null
        }
    }

    app.confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { app.confirm = null },
            title = { Text(c.title) },
            text = { Text(c.message) },
            confirmButton = {
                TextButton(onClick = { app.confirm = null; c.action() }) { Text(c.button, color = Red) }
            },
            dismissButton = { TextButton(onClick = { app.confirm = null }) { Text("Cancelar") } },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Row(Modifier.fillMaxSize().padding(pad)) {
            Sidebar(app)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (app.busy != null) BusyBar(app)
                Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
                    when (app.screen) {
                        Screen.PHONES -> PhonesScreen(app, frame)
                        Screen.CREATE -> CreateScreen(app)
                        Screen.DOWNLOAD -> DownloadScreen(app)
                        Screen.SETTINGS -> SettingsScreen(app)
                    }
                }
                LogPanel(app)
            }
        }
    }
}

@Composable
fun Sidebar(app: AppState) {
    Column(
        Modifier.width(232.dp).fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Green),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Android, null, tint = Color(0xFF00210E)) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("EmuDroid", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text("Emulador Android", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(28.dp))
        NavItem(Icons.Filled.PhoneAndroid, "Mis teléfonos", app.screen == Screen.PHONES) { app.screen = Screen.PHONES }
        NavItem(Icons.Filled.AddCircle, "Crear teléfono", app.screen == Screen.CREATE) { app.screen = Screen.CREATE }
        NavItem(Icons.Filled.CloudDownload, "Descargar Android", app.screen == Screen.DOWNLOAD) { app.screen = Screen.DOWNLOAD }
        NavItem(Icons.Filled.Settings, "Ajustes y ayuda", app.screen == Screen.SETTINGS) { app.screen = Screen.SETTINGS }
        Spacer(Modifier.weight(1f))

        val (text, color) = when {
            app.sdk == null -> "SDK no encontrado" to Red
            app.missing.isNotEmpty() -> "Faltan componentes" to Amber
            else -> "SDK listo" to Green
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.12f))
                .clickable { app.screen = Screen.SETTINGS }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (color == Green) Icons.Filled.CheckCircle else Icons.Filled.Warning, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("${app.running.size} encendido(s)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun NavItem(icon: ImageVector, text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Green.copy(alpha = 0.14f) else Color.Transparent
    val fg = if (selected) Green else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp)).background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = fg, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun BusyBar(app: AppState) {
    val pct = app.progress?.let { Regex("(\\d{1,3})%").find(it)?.groupValues?.get(1)?.toFloatOrNull() }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        if (pct != null) LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth(), color = Green)
        else LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green)
        Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(app.busy.orEmpty(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (pct != null) {
                Spacer(Modifier.width(12.dp))
                Text("${pct.toInt()} %", fontSize = 13.sp, color = Green)
            }
        }
    }
}

@Composable
fun LogPanel(app: AppState) {
    var open by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(app.logs.size, open) {
        if (open && app.logs.isNotEmpty()) listState.scrollToItem(app.logs.size - 1)
    }
    Column(Modifier.fillMaxWidth().background(Color(0xFF0A0C0F))) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Terminal, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text("Registro", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(12.dp))
            Text(
                if (open) "" else app.logs.lastOrNull().orEmpty(),
                Modifier.weight(1f), fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Icon(if (open) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) {
            LazyColumn(Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 16.dp), state = listState) {
                items(app.logs) {
                    Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFFB8C0CC))
                }
            }
        }
    }
}

// ---------------- Piezas reutilizables ----------------

@Composable
fun Header(title: String, subtitle: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 18.dp, bottom = 10.dp),
    )
}

@Composable
fun FieldLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

data class Action(val icon: ImageVector, val label: String, val run: () -> Unit)

@Composable
fun TileGrid(actions: List<Action>, columns: Int = 4) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        actions.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { a -> ActionTile(a.icon, a.label, Modifier.weight(1f), a.run) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun ActionTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(84.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Green, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun <T> Picker(value: String, options: List<T>, label: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(value, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 380.dp)) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(label(o)) }, onClick = { open = false; onPick(o) })
            }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) Green.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, if (selected) Green else Color.Transparent, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) { Text(text, fontSize = 13.sp, color = if (selected) Green else MaterialTheme.colorScheme.onSurface) }
}

@Composable
fun NoSdkCard(app: AppState) {
    Panel(Modifier.widthIn(max = 620.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = Amber)
            Spacer(Modifier.width(10.dp))
            Text("No se encontró el SDK de Android", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "EmuDroid usa el motor gratuito de Google para ejecutar Android. Siga los pasos de Ajustes y ayuda para instalarlo (una sola vez).",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
        )
        Spacer(Modifier.height(14.dp))
        Button(onClick = { app.screen = Screen.SETTINGS }) { Text("Ir a Ajustes y ayuda") }
    }
}

fun pickApk(frame: Frame): File? {
    val fd = FileDialog(frame, "Elegir archivo APK", FileDialog.LOAD)
    fd.file = "*.apk"
    fd.isVisible = true
    return if (fd.file != null) File(fd.directory, fd.file) else null
}

fun pickFolder(): File? {
    val ch = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Elegir la carpeta del SDK de Android"
    }
    return if (ch.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) ch.selectedFile else null
}

// ---------------- Mis teléfonos ----------------

@Composable
fun PhonesScreen(app: AppState, frame: Frame) {
    Column(Modifier.fillMaxSize()) {
        Header("Mis teléfonos virtuales", "Arrastre un archivo .apk a la ventana para instalarlo en el teléfono encendido.") {
            OutlinedButton(onClick = { app.refreshAll() }) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Actualizar")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { app.screen = Screen.CREATE }) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Nuevo teléfono")
            }
        }
        if (app.sdk == null) { NoSdkCard(app); return@Column }

        if (app.avds.isEmpty()) {
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.PhoneAndroid, null, Modifier.size(64.dp), tint = Green)
                    Spacer(Modifier.height(12.dp))
                    Text("Aún no tiene teléfonos virtuales", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Descargue una versión de Android y luego cree su primer teléfono.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Row {
                        OutlinedButton(onClick = { app.screen = Screen.DOWNLOAD }) { Text("1. Descargar Android") }
                        Spacer(Modifier.width(10.dp))
                        Button(onClick = { app.screen = Screen.CREATE }) { Text("2. Crear teléfono") }
                    }
                }
            }
            return@Column
        }

        Row(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.width(330.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(app.avds) { name -> AvdCard(app, name) }
            }
            Spacer(Modifier.width(20.dp))
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val sel = app.selected
                if (sel != null) ControlPanel(app, sel, frame)
            }
        }
    }
}

@Composable
fun AvdCard(app: AppState, name: String) {
    val info = app.running[name]
    val isStarting = name in app.starting
    val (statusText, statusColor) = when {
        info?.booted == true -> "Encendido" to Green
        info != null || isStarting -> "Iniciando…" to Amber
        else -> "Apagado" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val sel = app.selected == name
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)

    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (sel) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (sel) Green.copy(alpha = 0.6f) else Color.Transparent, shape)
            .clickable { app.selected = name }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Green.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.PhoneAndroid, null, tint = Green) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(prettyName(name), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                Spacer(Modifier.width(6.dp))
                Text(statusText, fontSize = 12.sp, color = statusColor)
            }
        }
        if (info != null || isStarting) {
            IconButton(onClick = { app.stopAvd(name) }) { Icon(Icons.Filled.Stop, "Apagar", tint = Red) }
        } else {
            IconButton(onClick = { app.startAvd(name) }) { Icon(Icons.Filled.PlayArrow, "Encender", tint = Green) }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Más opciones") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Arranque en frío") },
                    leadingIcon = { Icon(Icons.Filled.AcUnit, null) },
                    onClick = { menu = false; app.startAvd(name, coldBoot = true) },
                )
                DropdownMenuItem(
                    text = { Text("Crear acceso directo en el escritorio") },
                    leadingIcon = { Icon(Icons.Filled.Shortcut, null) },
                    onClick = { menu = false; app.createShortcut(name) },
                )
                DropdownMenuItem(
                    text = { Text("Borrar datos y encender") },
                    leadingIcon = { Icon(Icons.Filled.RestartAlt, null) },
                    onClick = { menu = false; app.askWipe(name) },
                )
                DropdownMenuItem(
                    text = { Text("Eliminar teléfono", color = Red) },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Red) },
                    onClick = { menu = false; app.askDelete(name) },
                )
            }
        }
    }
}

@Composable
fun ControlPanel(app: AppState, name: String, frame: Frame) {
    val info = app.running[name]
    var text by remember(name) { mutableStateOf("") }
    var url by remember(name) { mutableStateOf("") }

    Panel(Modifier.fillMaxSize()) {
        Text(prettyName(name), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            when {
                info == null && name in app.starting -> "Abriendo la ventana del emulador…"
                info == null -> "Apagado"
                !info.booted -> "Conectado como ${info.serial} · Android está arrancando…"
                else -> "Conectado como ${info.serial} · listo"
            },
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (info == null) {
            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.PhoneAndroid, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                if (name in app.starting) {
                    CircularProgressIndicator(color = Green)
                    Spacer(Modifier.height(12.dp))
                    Text("Encendiendo… la primera vez puede tardar 1 o 2 minutos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Este teléfono está apagado", fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    Row {
                        Button(onClick = { app.startAvd(name) }) {
                            Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Encender")
                        }
                        Spacer(Modifier.width(10.dp))
                        OutlinedButton(onClick = { app.startAvd(name, coldBoot = true) }) { Text("Arranque en frío") }
                    }
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { app.createShortcut(name) }) {
                        Icon(Icons.Filled.Shortcut, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text("Crear acceso directo en el escritorio")
                    }
                    Spacer(Modifier.height(18.dp))
                    ScreenSizeSection(app, name, enabled = true)
                }
            }
            return@Panel
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionTitle("Aplicaciones y pantalla")
            TileGrid(
                listOf(
                    Action(Icons.Filled.InstallMobile, "Instalar APK") { pickApk(frame)?.let { app.installApks(listOf(it)) } },
                    Action(Icons.Filled.Screenshot, "Captura") { app.screenshot(name) },
                    Action(Icons.Filled.FolderOpen, "Ver capturas") { app.openScreenshots() },
                    Action(Icons.Filled.ScreenRotation, "Rotar") { app.rotate(name) },
                    Action(Icons.Filled.Fullscreen, "Pantalla completa") { app.fullscreen(name, true) },
                    Action(Icons.Filled.FullscreenExit, "Salir de pantalla completa") { app.fullscreen(name, false) },
                )
            )

            SectionTitle("Botones del teléfono")
            TileGrid(
                listOf(
                    Action(Icons.Filled.ArrowBack, "Atrás") { app.key(name, 4) },
                    Action(Icons.Filled.Home, "Inicio") { app.key(name, 3) },
                    Action(Icons.Filled.CropSquare, "Recientes") { app.key(name, 187) },
                    Action(Icons.Filled.PowerSettingsNew, "Encendido") { app.key(name, 26) },
                    Action(Icons.Filled.VolumeUp, "Volumen +") { app.key(name, 24) },
                    Action(Icons.Filled.VolumeDown, "Volumen −") { app.key(name, 25) },
                    Action(Icons.Filled.VolumeOff, "Silencio") { app.key(name, 164) },
                    Action(Icons.Filled.Stop, "Apagar") { app.stopAvd(name) },
                )
            )

            SectionTitle("Escribir en el teléfono")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    text, { text = it }, Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("Texto (sin tildes ni ñ)") },
                )
                Spacer(Modifier.width(10.dp))
                Button(onClick = { if (text.isNotBlank()) { app.sendText(name, text); text = "" } }) { Text("Enviar") }
            }

            SectionTitle("Abrir enlace en el teléfono")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    url, { url = it }, Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("https://…") },
                )
                Spacer(Modifier.width(10.dp))
                Button(onClick = { if (url.isNotBlank()) app.openUrl(name, url.trim()) }) { Text("Abrir") }
            }

            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.fillMaxWidth().height(86.dp)
                    .border(1.5.dp, Green.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .clickable { pickApk(frame)?.let { app.installApks(listOf(it)) } },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FileUpload, null, tint = Green)
                    Spacer(Modifier.width(10.dp))
                    Text("Arrastre aquí un archivo .apk para instalarlo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Consejo: el teclado de su PC funciona dentro del teléfono. Ctrl+F11 también rota la pantalla.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("Tamaño de pantalla")
            ScreenSizeSection(app, name, enabled = false)
        }
    }
}

@Composable
fun ScreenSizeSection(app: AppState, name: String, enabled: Boolean) {
    val current = app.screens[name]
    val mode = ScreenMode.detect(current)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScreenMode.entries.forEach { m ->
                Chip(m.label, mode == m) {
                    if (enabled) app.setScreen(name, m)
                    else app.toast("Apague el teléfono para cambiar el tamaño de pantalla")
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            (current?.let { "Actual: ${it.first} × ${it.second}" } ?: "") +
                (if (!enabled) " · apague el teléfono para cambiarlo" else ""),
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------- Crear teléfono ----------------

@Composable
fun CreateScreen(app: AppState) {
    LaunchedEffect(app.sdk) { app.loadProfiles() }
    var name by remember { mutableStateOf("Mi_Telefono") }
    var profile by remember { mutableStateOf<String?>(null) }
    var image by remember { mutableStateOf<SystemImage?>(null) }
    var ram by remember { mutableStateOf(2048) }
    var screenMode by remember { mutableStateOf(ScreenMode.PHONE) }

    LaunchedEffect(app.profiles) {
        if (profile == null) profile = app.profiles.firstOrNull { it == "pixel_7" } ?: app.profiles.firstOrNull()
    }
    LaunchedEffect(app.images) {
        if (image == null || image !in app.images)
            image = app.images.firstOrNull { it.tag == "google_apis_playstore" } ?: app.images.firstOrNull()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Crear teléfono virtual", "Elija el modelo, la versión de Android y la memoria.")
        if (app.sdk == null) { NoSdkCard(app); return@Column }

        Panel(Modifier.widthIn(max = 660.dp)) {
            FieldLabel("Nombre")
            OutlinedTextField(
                value = name,
                onValueChange = { v -> name = v.replace(' ', '_').filter { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("Letras, números y guion bajo (sin espacios ni tildes)") },
                isError = name in app.avds,
            )
            if (name in app.avds) Text("Ya existe un teléfono con ese nombre", color = Red, fontSize = 12.sp)

            FieldLabel("Modelo de teléfono")
            Picker(
                value = profile?.let(::profileLabel) ?: if (app.profiles.isEmpty()) "Cargando modelos…" else "Elegir modelo",
                options = app.profiles,
                label = ::profileLabel,
            ) { profile = it }

            FieldLabel("Versión de Android")
            if (app.images.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, null, tint = Amber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("No tiene ninguna versión descargada.", fontSize = 13.sp)
                    TextButton(onClick = { app.screen = Screen.DOWNLOAD }) { Text("Descargar Android") }
                }
            } else {
                Picker(image?.label ?: "Elegir versión", app.images, { it.label }) { image = it }
            }

            FieldLabel("Memoria RAM")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2048, 3072, 4096, 6144).forEach { mb -> Chip("${mb / 1024} GB", ram == mb) { ram = mb } }
            }
            Text(
                "Use 2 GB si su PC tiene 8 GB de RAM; 4 GB o más si tiene 16 GB.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
            )

            FieldLabel("Tamaño de pantalla")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScreenMode.entries.forEach { m -> Chip(m.label, screenMode == m) { screenMode = m } }
            }
            val (sw, sh) = screenMode.resolution()
            Text(
                "${screenMode.description} · $sw × $sh",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(24.dp))
            val p = profile
            val img = image
            Button(
                onClick = { if (p != null && img != null) app.createAvd(name, img, p, ram, screenMode) },
                enabled = name.isNotBlank() && p != null && img != null && app.busy == null && name !in app.avds,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Filled.AddCircle, null); Spacer(Modifier.width(8.dp)); Text("Crear teléfono")
            }
        }
    }
}

// ---------------- Descargar Android ----------------

@Composable
fun DownloadScreen(app: AppState) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Descargar Android", "Cada versión pesa entre 1 y 2 GB. Solo se descarga una vez.")
        val s = app.sdk
        if (s == null) { NoSdkCard(app); return@Column }
        val canDownload = s.sdkmanager != null

        if (app.missing.isNotEmpty()) {
            Panel(Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = Amber)
                    Spacer(Modifier.width(10.dp))
                    Text("Faltan: ${app.missing.joinToString()}", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                if (canDownload) {
                    Button(onClick = { app.installBase() }, enabled = app.busy == null) { Text("Instalar componentes del emulador") }
                } else {
                    Text(
                        "Abra Android Studio → More Actions → SDK Manager → pestaña SDK Tools y marque " +
                            "\"Android SDK Command-line Tools\". Luego vuelva aquí.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Panel(Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
            Text("Versiones recomendadas (con Play Store)", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            RECOMMENDED_IMAGES.forEachIndexed { i, img ->
                val installed = app.images.any { it.packageId == img.packageId }
                ImageRow(img, installed, canDownload && app.busy == null) { app.downloadImage(img) }
                if (i < RECOMMENDED_IMAGES.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }
        }

        val others = app.images.filter { img -> RECOMMENDED_IMAGES.none { it.packageId == img.packageId } }
        if (others.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Panel(Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                Text("Otras versiones instaladas", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                others.forEach { ImageRow(it, installed = true, enabled = false) {} }
            }
        }
    }
}

@Composable
fun ImageRow(img: SystemImage, installed: Boolean, enabled: Boolean, onDownload: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Android, null, tint = Green, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("${androidName(img.apiLevel)} · API ${img.apiLevel}", fontWeight = FontWeight.SemiBold)
            Text("${tagLabel(img.tag)} · ${img.abi}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (installed) {
            Icon(Icons.Filled.CheckCircle, null, tint = Green, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Instalado", color = Green, fontSize = 13.sp)
        } else {
            FilledTonalButton(onClick = onDownload, enabled = enabled) {
                Icon(Icons.Filled.CloudDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Descargar")
            }
        }
    }
}

// ---------------- Ajustes y ayuda ----------------

@Composable
fun SettingsScreen(app: AppState) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Ajustes y ayuda", "Dónde está el motor de Android y cómo prepararlo.")

        Panel(Modifier.fillMaxWidth().widthIn(max = 800.dp)) {
            Text("Carpeta del SDK de Android", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                app.sdk?.root?.absolutePath ?: "No configurada",
                fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                Button(onClick = { pickFolder()?.let { app.setSdk(it) } }) {
                    Icon(Icons.Filled.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Elegir carpeta…")
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { app.autoDetect() }) { Text("Buscar automáticamente") }
            }
            app.sdk?.let { s ->
                Spacer(Modifier.height(14.dp))
                CheckRow("Android Emulator", s.emulator.isFile)
                CheckRow("Platform-Tools (adb)", s.adb.isFile)
                CheckRow("Command-line Tools (avdmanager, sdkmanager)", s.avdmanager != null)
                CheckRow("Versiones de Android descargadas: ${app.images.size}", app.images.isNotEmpty())
            }
        }

        Spacer(Modifier.height(16.dp))
        Panel(Modifier.fillMaxWidth().widthIn(max = 800.dp)) {
            Text("Primera vez: cómo preparar su PC", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Step(1, "Instale Android Studio (gratis). Trae el motor de Android que usa EmuDroid.")
            Step(2, "Abra Android Studio → More Actions → SDK Manager → pestaña SDK Tools. Marque: Android Emulator, Android SDK Platform-Tools y Android SDK Command-line Tools (latest). Pulse Apply.")
            Step(3, "Active la virtualización: busque en Windows \"Activar o desactivar las características de Windows\", marque \"Plataforma del hipervisor de Windows\" y reinicie. Si no arranca, active VT-x / AMD-V (SVM) en la BIOS.")
            Step(4, "Vuelva aquí y pulse \"Buscar automáticamente\". Después vaya a Descargar Android.")
            Spacer(Modifier.height(10.dp))
            Row {
                Button(onClick = { app.openWeb("https://developer.android.com/studio") }) {
                    Icon(Icons.Filled.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Descargar Android Studio")
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { app.openScreenshots() }) { Text("Abrir carpeta de capturas") }
            }
        }
    }
}

@Composable
fun CheckRow(text: String, ok: Boolean) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning, null,
            tint = if (ok) Green else Amber, modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp)
    }
}

@Composable
fun Step(n: Int, text: String) {
    Row(Modifier.padding(vertical = 6.dp)) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(Green.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Text("$n", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
    }
}
