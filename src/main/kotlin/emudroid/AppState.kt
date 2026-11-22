package emudroid

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

enum class Screen { PHONES, CREATE, DOWNLOAD, SETTINGS }

data class Confirm(val title: String, val message: String, val button: String, val action: () -> Unit)

class AppState(private val scope: CoroutineScope) {
    var sdk by mutableStateOf<AndroidSdk?>(null)
        private set
    var missing by mutableStateOf(listOf<String>())
    var screen by mutableStateOf(Screen.PHONES)
    var avds by mutableStateOf(listOf<String>())
    var screens by mutableStateOf(mapOf<String, Pair<Int, Int>?>())
    var running by mutableStateOf(mapOf<String, RunningInfo>())
    var starting by mutableStateOf(setOf<String>())
    var selected by mutableStateOf<String?>(null)
    var images by mutableStateOf(listOf<SystemImage>())
    var profiles by mutableStateOf(listOf<String>())
    var busy by mutableStateOf<String?>(null)
    var progress by mutableStateOf<String?>(null)
    var snackbar by mutableStateOf<String?>(null)
    var confirm by mutableStateOf<Confirm?>(null)
    val logs = mutableStateListOf<String>()

    val screenshotsDir = File(System.getProperty("user.home"), "Pictures/EmuDroid")
    private val processes = ConcurrentHashMap<String, Process>()
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun start() {
        setSdk(SdkLocator.find(), save = false)
        scope.launch {
            while (isActive) {
                pollRunning()
                delay(3000)
            }
        }
    }

    // ---------- SDK ----------

    fun setSdk(dir: File?, save: Boolean = true) {
        sdk = dir?.let { AndroidSdk(it) }
        if (save && dir != null) Config.sdkPath = dir.absolutePath
        missing = sdk?.missingParts() ?: emptyList()
        profiles = emptyList()
        if (dir == null) log("No se encontró el SDK de Android. Vaya a Ajustes.")
        else log("SDK de Android: ${dir.absolutePath}")
        refreshAll()
    }

    fun autoDetect() {
        Config.sdkPath = null
        val found = SdkLocator.find()
        setSdk(found, save = found != null)
        toast(if (found != null) "SDK encontrado en ${found.absolutePath}" else "No se encontró el SDK automáticamente")
    }

    fun refreshAll() {
        val s = sdk ?: return
        scope.launch {
            val (a, i) = withContext(Dispatchers.IO) {
                (if (s.emulator.isFile) s.listAvds() else emptyList()) to s.installedImages()
            }
            val sc = withContext(Dispatchers.IO) { a.associateWith { s.readScreen(it) } }
            avds = a
            screens = sc
            images = i
            missing = s.missingParts()
            if (selected == null || selected !in a) selected = a.firstOrNull()
        }
    }

    fun loadProfiles() {
        val s = sdk ?: return
        if (profiles.isNotEmpty()) return
        scope.launch { profiles = withContext(Dispatchers.IO) { s.deviceProfiles() } }
    }

    private suspend fun pollRunning() {
        val s = sdk ?: return
        if (!s.adb.isFile) return
        val r = withContext(Dispatchers.IO) { runCatching { s.runningEmulators() }.getOrDefault(emptyMap()) }
        running = r
        starting = starting.filter { r[it]?.booted != true }.toSet()
    }

    // ---------- Mensajes ----------

    fun log(msg: String) {
        val line = "[${LocalTime.now().format(timeFmt)}] $msg"
        scope.launch(Dispatchers.Main) {
            logs.add(line)
            if (logs.size > 600) repeat(logs.size - 500) { logs.removeAt(0) }
        }
    }

    fun toast(msg: String) {
        snackbar = msg
        log(msg)
    }

    private val onLine: (String) -> Unit = { raw ->
        val t = raw.trim()
        if (t.isNotEmpty()) {
            if (t.startsWith("[") && t.contains("%")) progress = t else log(t)
        }
    }

    /** Operación larga: solo una a la vez. */
    private fun task(label: String, block: suspend (AndroidSdk) -> Unit) {
        val s = sdk ?: return toast("Primero configure la carpeta del SDK de Android en Ajustes")
        if (busy != null) return toast("Espere a que termine: $busy")
        busy = label
        progress = null
        log("▶ $label")
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block(s) }
            } catch (e: Exception) {
                toast("Error: ${e.message}")
            } finally {
                busy = null
                progress = null
            }
        }
    }

    /** Acción rápida sobre un teléfono encendido. */
    private fun quick(name: String, block: (AndroidSdk, String) -> Unit) {
        val s = sdk ?: return
        val serial = running[name]?.serial ?: return toast("El teléfono ${prettyName(name)} no está encendido")
        scope.launch(Dispatchers.IO) {
            try { block(s, serial) } catch (e: Exception) { toast("Error: ${e.message}") }
        }
    }

    private fun lastLines(text: String, n: Int = 3) =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.takeLast(n).joinToString(" ")

    // ---------- Encender / apagar ----------

    fun startAvd(name: String, coldBoot: Boolean = false, wipe: Boolean = false) {
        val s = sdk ?: return toast("Configure el SDK de Android en Ajustes")
        if (!s.emulator.isFile) return toast("Falta el Android Emulator. Instálelo en 'Descargar Android'.")
        if (name in running || name in starting) return toast("${prettyName(name)} ya está encendido")
        starting = starting + name
        selected = name
        val size = s.readScreen(name)
        log("Encendiendo ${prettyName(name)}…" + (size?.let { " Pantalla ${it.first} × ${it.second}" } ?: ""))
        scope.launch(Dispatchers.IO) {
            try {
                val p = s.startEmulator(name, coldBoot, wipe) { line ->
                    if (listOf("ERROR", "FATAL", "PANIC", "WARNING").any { line.contains(it) }) log("[$name] ${line.trim()}")
                }
                processes[name] = p
                if (s.isFullScreen(name)) {
                    val r = s.windowMode(name, full = true)
                    if (!r.output.contains("OK")) log("No se pudo poner en pantalla completa: ${lastLines(r.output, 2)}")
                }
                p.onExit().thenRun {
                    processes.remove(name)
                    scope.launch {
                        starting = starting - name
                        log("${prettyName(name)} se cerró (código ${p.exitValue()})")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { starting = starting - name }
                toast("No se pudo encender: ${e.message}")
            }
        }
    }

    fun stopAvd(name: String) {
        val s = sdk ?: return
        val serial = running[name]?.serial
        scope.launch(Dispatchers.IO) {
            if (serial != null) s.adb("-s", serial, "emu", "kill", timeoutSec = 20)
            else processes[name]?.destroy()
            log("Apagando ${prettyName(name)}…")
        }
    }

    // ---------- Crear / eliminar ----------

    fun createAvd(name: String, image: SystemImage, profile: String, ramMb: Int, mode: ScreenMode = ScreenMode.PHONE) =
        task("Creando ${prettyName(name)}") { s ->
            val r = s.createAvd(name, image, profile, onLine)
            if (r.ok && s.avdDir(name).isDirectory) {
                s.tweakConfig(name, ramMb)
                if (mode != ScreenMode.PHONE) s.setScreen(name, mode)
                withContext(Dispatchers.Main) {
                    screen = Screen.PHONES
                    selected = name
                }
                toast("Teléfono ${prettyName(name)} creado")
                refreshAll()
            } else {
                toast("No se pudo crear: ${lastLines(r.output)}")
            }
        }

    fun setScreen(name: String, mode: ScreenMode) {
        val s = sdk ?: return
        if (name in running || name in starting) return toast("Apague el teléfono para cambiar el tamaño de pantalla")
        scope.launch(Dispatchers.IO) {
            try {
                s.setScreen(name, mode)
                val (w, h) = mode.resolution()
                if (s.readScreen(name) == (w to h)) {
                    toast("${prettyName(name)}: ${mode.label} ($w × $h). La próxima vez encenderá desde cero.")
                } else {
                    toast("No se pudo guardar el tamaño en ${s.avdDir(name).absolutePath}")
                }
                refreshAll()
            } catch (e: Exception) {
                toast("No se pudo cambiar la pantalla: ${e.message}")
            }
        }
    }

    fun fullscreen(name: String, on: Boolean) {
        val s = sdk ?: return
        if (name !in running && name !in starting) return toast("El teléfono ${prettyName(name)} no está encendido")
        scope.launch(Dispatchers.IO) {
            val r = s.windowMode(name, on)
            if (r.output.contains("OK")) {
                if (on) toast("Pantalla completa. Use Alt+Tab para volver a EmuDroid y salir de ella.")
            } else {
                toast("No se encontró la ventana del emulador: ${lastLines(r.output, 2)}")
            }
        }
    }

    fun createShortcut(name: String) {
        val s = sdk ?: return toast("Configure el SDK de Android en Ajustes")
        scope.launch(Dispatchers.IO) {
            val r = s.createDesktopShortcut(name)
            if (r.ok && r.output.endsWith(".lnk")) toast("Acceso directo creado en el escritorio: ${File(r.output).name}")
            else toast("No se pudo crear el acceso directo: ${lastLines(r.output, 2)}")
        }
    }

    fun askDelete(name: String) {
        confirm = Confirm(
            "Eliminar teléfono",
            "¿Seguro que quiere eliminar \"${prettyName(name)}\"? Se borrarán todas sus apps y datos.",
            "Eliminar",
        ) { deleteAvd(name) }
    }

    private fun deleteAvd(name: String) {
        if (name in running || name in starting) return toast("Apague el teléfono antes de eliminarlo")
        task("Eliminando ${prettyName(name)}") { s ->
            val r = s.deleteAvd(name, onLine)
            if (r.ok) toast("Teléfono eliminado") else toast("No se pudo eliminar: ${lastLines(r.output)}")
            refreshAll()
        }
    }

    fun askWipe(name: String) {
        confirm = Confirm(
            "Borrar datos",
            "Se borrarán todas las apps y datos de \"${prettyName(name)}\" y quedará como nuevo. ¿Continuar?",
            "Borrar y encender",
        ) { startAvd(name, coldBoot = true, wipe = true) }
    }

    // ---------- Descargas ----------

    fun downloadImage(img: SystemImage) = task("Descargando ${androidName(img.apiLevel)}") { s ->
        val pkgs = buildList {
            if (!s.emulator.isFile) add("emulator")
            if (!s.adb.isFile) add("platform-tools")
            add(img.packageId)
        }
        val r = s.sdkInstall(pkgs, onLine)
        if (r.ok) toast("${androidName(img.apiLevel)} descargado") else toast("Falló la descarga: ${lastLines(r.output)}")
        refreshAll()
    }

    fun installBase() = task("Instalando componentes del emulador") { s ->
        val r = s.sdkInstall(listOf("emulator", "platform-tools"), onLine)
        if (r.ok) toast("Componentes instalados") else toast("Falló la instalación: ${lastLines(r.output)}")
        refreshAll()
    }

    // ---------- APK ----------

    fun installApks(files: List<File>) {
        val apks = files.filter { it.extension.equals("apk", ignoreCase = true) }
        if (apks.isEmpty()) return toast("Solo se pueden instalar archivos .apk")
        val target = selected?.takeIf { running[it]?.booted == true }
            ?: running.entries.firstOrNull { it.value.booted }?.key
            ?: return toast("Encienda un teléfono y espere a que termine de arrancar")
        val serial = running[target]!!.serial
        task("Instalando ${apks.joinToString { it.name }} en ${prettyName(target)}") { s ->
            for (apk in apks) {
                val r = s.adb("-s", serial, "install", "-r", "-g", apk.absolutePath, timeoutSec = 600)
                if (r.output.contains("Success")) toast("${apk.name} instalado en ${prettyName(target)}")
                else toast("No se pudo instalar ${apk.name}: ${lastLines(r.output, 2)}")
            }
        }
    }

    // ---------- Controles ----------

    fun key(name: String, code: Int) = quick(name) { s, serial ->
        s.adb("-s", serial, "shell", "input", "keyevent", code.toString(), timeoutSec = 15)
    }

    fun rotate(name: String) = quick(name) { s, serial ->
        s.adb("-s", serial, "emu", "rotate", timeoutSec = 15)
    }

    fun screenshot(name: String) = quick(name) { s, serial ->
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val file = File(screenshotsDir, "${name}_$stamp.png")
        if (s.screenshot(serial, file)) toast("Captura guardada: ${file.name}")
        else toast("No se pudo tomar la captura")
    }

    fun sendText(name: String, text: String) = quick(name) { s, serial ->
        val t = text.replace("\"", "").replace(" ", "%s").replace("'", "'\\''")
        s.adb("-s", serial, "shell", "input", "text", "'$t'", timeoutSec = 30)
    }

    fun openUrl(name: String, url: String) = quick(name) { s, serial ->
        val u = if (url.contains("://")) url else "https://$url"
        s.adb("-s", serial, "shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", "'$u'", timeoutSec = 20)
    }

    fun openScreenshots() {
        screenshotsDir.mkdirs()
        runCatching { Desktop.getDesktop().open(screenshotsDir) }.onFailure { toast("No se pudo abrir la carpeta") }
    }

    fun openWeb(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }.onFailure { toast("Abra en su navegador: $url") }
    }
}
