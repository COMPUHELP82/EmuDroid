package emudroid

import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Resultado de un comando ejecutado. */
data class CmdResult(val code: Int, val output: String) {
    val ok: Boolean get() = code == 0
}

/** Teléfono encendido: serial de adb y si Android ya terminó de arrancar. */
data class RunningInfo(val serial: String, val booted: Boolean)

/** Imagen de sistema (versión de Android) del SDK. */
data class SystemImage(val apiLevel: Int, val tag: String, val abi: String) {
    val packageId: String get() = "system-images;android-$apiLevel;$tag;$abi"
    val label: String get() = "${androidName(apiLevel)} (API $apiLevel) · ${tagLabel(tag)} · $abi"
}

fun androidName(api: Int): String = when (api) {
    36 -> "Android 16"; 35 -> "Android 15"; 34 -> "Android 14"; 33 -> "Android 13"
    32 -> "Android 12L"; 31 -> "Android 12"; 30 -> "Android 11"; 29 -> "Android 10"
    28 -> "Android 9"; 27 -> "Android 8.1"; 26 -> "Android 8.0"; 25 -> "Android 7.1"
    24 -> "Android 7.0"; 23 -> "Android 6.0"
    else -> "Android"
}

fun tagLabel(tag: String): String = when (tag) {
    "google_apis_playstore" -> "Con Play Store"
    "google_apis" -> "Google APIs"
    "default" -> "Android puro"
    else -> tag
}

fun profileLabel(id: String): String =
    id.replace('_', ' ').replaceFirstChar { it.uppercase() }

fun prettyName(avd: String): String = avd.replace('_', ' ')

/** Versiones recomendadas para descargar. */
val RECOMMENDED_IMAGES = listOf(36, 35, 34, 33, 31, 30).map {
    SystemImage(it, "google_apis_playstore", "x86_64")
}

/** Resolución real del monitor principal (en píxeles, sin la escala de Windows). */
fun monitorSize(): Pair<Int, Int> = runCatching {
    val m = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayMode
    maxOf(m.width, m.height) to minOf(m.width, m.height)
}.getOrDefault(1920 to 1080)

enum class ScreenMode(val label: String, val description: String) {
    PHONE("Teléfono", "Vertical, tamaño de celular (1080 × 2400)"),
    TABLET("Tablet", "Horizontal, tamaño de tablet (2560 × 1600)"),
    FULL("Pantalla completa", "Horizontal, ocupa todo su monitor como una PC");

    fun resolution(): Pair<Int, Int> = when (this) {
        PHONE -> 1080 to 2400
        TABLET -> 2560 to 1600
        FULL -> monitorSize()
    }

    /** Densidad: en pantalla completa se calcula para que letras e iconos se vean de buen tamaño. */
    fun density(): Int = when (this) {
        PHONE -> 420
        TABLET -> 320
        FULL -> (monitorSize().second * 160 / 720).coerceIn(160, 480)
    }

    companion object {
        fun detect(size: Pair<Int, Int>?): ScreenMode? = when (size) {
            null -> null
            FULL.resolution() -> FULL
            TABLET.resolution() -> TABLET
            else -> if (size.second > size.first) PHONE else null
        }
    }
}

val DEFAULT_PROFILES =listOf("pixel_7", "pixel_6", "pixel_5", "pixel_4", "pixel_3a", "pixel_tablet")

/** Configuración guardada en %USERPROFILE%\.emudroid\config.properties */
object Config {
    private val dir = File(System.getProperty("user.home"), ".emudroid").apply { mkdirs() }
    private val file = File(dir, "config.properties")
    private val props = Properties().apply {
        if (file.exists()) runCatching { file.inputStream().use { load(it) } }
    }

    var sdkPath: String?
        get() = props.getProperty("sdk")
        set(value) {
            if (value == null) props.remove("sdk") else props.setProperty("sdk", value)
            runCatching { file.outputStream().use { props.store(it, "EmuDroid") } }
        }
}

/** Busca la carpeta del SDK de Android en los lugares habituales. */
object SdkLocator {
    fun candidates(): List<File> {
        val home = System.getProperty("user.home")
        return listOfNotNull(
            Config.sdkPath,
            System.getenv("ANDROID_HOME"),
            System.getenv("ANDROID_SDK_ROOT"),
            System.getenv("LOCALAPPDATA")?.let { "$it\\Android\\Sdk" },
            "$home\\AppData\\Local\\Android\\Sdk",
            "C:\\Android\\Sdk",
            "C:\\Android\\android-sdk",
            "$home/Android/Sdk",
            "$home/Library/Android/sdk",
        ).map(::File).filter { it.isDirectory }.distinct()
    }

    fun find(): File? {
        val list = candidates()
        return list.firstOrNull { File(it, "emulator").isDirectory || File(it, "platform-tools").isDirectory }
            ?: list.firstOrNull()
    }
}

/** Todo lo que habla con el SDK de Android (emulator, adb, avdmanager, sdkmanager). */
class AndroidSdk(val root: File) {
    private val isWindows = System.getProperty("os.name").lowercase().contains("win")
    private val exe = if (isWindows) ".exe" else ""
    private val bat = if (isWindows) ".bat" else ""

    val emulator = File(root, "emulator/emulator$exe")
    val adb = File(root, "platform-tools/adb$exe")
    val avdmanager: File? get() = findTool("avdmanager")
    val sdkmanager: File? get() = findTool("sdkmanager")

    private fun findTool(name: String): File? {
        val dir = File(root, "cmdline-tools")
        val list = mutableListOf(File(dir, "latest/bin/$name$bat"))
        dir.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name }
            ?.forEach { list += File(it, "bin/$name$bat") }
        list += File(root, "tools/bin/$name$bat")
        return list.firstOrNull { it.isFile }
    }

    fun missingParts(): List<String> = buildList {
        if (!emulator.isFile) add("Android Emulator")
        if (!adb.isFile) add("Platform-Tools (adb)")
        if (avdmanager == null) add("Command-line Tools")
    }

    private fun javaHome(): String? {
        System.getenv("JAVA_HOME")?.takeIf { File(it, "bin").isDirectory }?.let { return it }
        listOf(
            "C:\\Program Files\\Android\\Android Studio\\jbr",
            "C:\\Program Files\\Android\\Android Studio\\jre",
        ).firstOrNull { File(it, "bin").isDirectory }?.let { return it }
        return System.getProperty("java.home")
    }

    private fun prepare(pb: ProcessBuilder): ProcessBuilder {
        val env = pb.environment()
        env["ANDROID_HOME"] = root.absolutePath
        env["ANDROID_SDK_ROOT"] = root.absolutePath
        javaHome()?.let { env["JAVA_HOME"] = it }
        return pb
    }

    /** En Windows los .bat necesitan comillas en argumentos con ';' o espacios. */
    private fun command(tool: File, args: List<String>): List<String> {
        if (isWindows && tool.name.endsWith(".bat", ignoreCase = true)) {
            val quoted = args.map { a -> if (a.any { it == ' ' || it == ';' }) "\"$a\"" else a }
            return listOf(tool.absolutePath) + quoted
        }
        return listOf(tool.absolutePath) + args
    }

    fun run(
        tool: File,
        args: List<String>,
        input: String? = null,
        timeoutSec: Long = 600,
        onLine: (String) -> Unit = {},
    ): CmdResult = try {
        val p = prepare(ProcessBuilder(command(tool, args)).redirectErrorStream(true)).start()
        if (input != null) {
            thread(isDaemon = true) {
                runCatching { p.outputStream.bufferedWriter().use { it.write(input); it.flush() } }
            }
        } else {
            runCatching { p.outputStream.close() }
        }
        val out = StringBuffer()
        val reader = thread(isDaemon = true) {
            runCatching {
                p.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { out.appendLine(it); onLine(it) }
                }
            }
        }
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            CmdResult(-1, "$out\n(Se agotó el tiempo de espera)")
        } else {
            reader.join(3000)
            CmdResult(p.exitValue(), out.toString())
        }
    } catch (e: Exception) {
        CmdResult(-1, "Error: ${e.message}")
    }

    fun adb(vararg args: String, timeoutSec: Long = 60): CmdResult =
        run(adb, args.toList(), timeoutSec = timeoutSec)

    // ---------- Teléfonos virtuales (AVD) ----------

    fun listAvds(): List<String> =
        run(emulator, listOf("-list-avds"), timeoutSec = 30).output.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains(' ') && !it.startsWith("INFO") && !it.startsWith("WARNING") && !it.startsWith("Error") }

    fun runningEmulators(): Map<String, RunningInfo> {
        val serials = adb("devices", timeoutSec = 20).output.lines().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 2 && parts[0].startsWith("emulator-")) parts[0] to parts[1] else null
        }
        val result = mutableMapOf<String, RunningInfo>()
        for ((serial, state) in serials) {
            val name = adb("-s", serial, "emu", "avd", "name", timeoutSec = 10)
                .output.lines().firstOrNull()?.trim().orEmpty()
            if (name.isEmpty() || name == "OK" || name.startsWith("error")) continue
            val booted = state == "device" &&
                adb("-s", serial, "shell", "getprop", "sys.boot_completed", timeoutSec = 10).output.trim() == "1"
            result[name] = RunningInfo(serial, booted)
        }
        return result
    }

    fun installedImages(): List<SystemImage> {
        val res = mutableListOf<SystemImage>()
        File(root, "system-images").listFiles()?.forEach { apiDir ->
            val api = apiDir.name.removePrefix("android-").toIntOrNull() ?: return@forEach
            apiDir.listFiles()?.filter { it.isDirectory }?.forEach { tagDir ->
                tagDir.listFiles()?.filter { it.isDirectory }?.forEach { abiDir ->
                    res += SystemImage(api, tagDir.name, abiDir.name)
                }
            }
        }
        return res.sortedWith(compareByDescending<SystemImage> { it.apiLevel }.thenBy { it.tag })
    }

    fun deviceProfiles(): List<String> {
        val tool = avdmanager ?: return DEFAULT_PROFILES
        val ids = run(tool, listOf("list", "device", "-c"), timeoutSec = 90).output.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains(' ') && !it.contains(':') && !it.startsWith("[") && !it.startsWith("Error") }
        if (ids.isEmpty()) return DEFAULT_PROFILES
        return ids.sortedBy { id ->
            when {
                id.startsWith("pixel") && !id.contains("tablet") && !id.contains("fold") -> 0
                id.contains("phone") -> 1
                id.contains("tablet") || id.contains("fold") -> 2
                id.startsWith("Nexus") -> 3
                else -> 4
            }
        }
    }

    fun createAvd(name: String, image: SystemImage, profile: String, onLine: (String) -> Unit): CmdResult {
        val tool = avdmanager ?: return CmdResult(-1, "No se encontró avdmanager (Command-line Tools)")
        return run(
            tool,
            listOf("create", "avd", "-n", name, "-k", image.packageId, "-d", profile, "--force"),
            input = "no\n",
            onLine = onLine,
        )
    }

    fun deleteAvd(name: String, onLine: (String) -> Unit): CmdResult {
        val tool = avdmanager ?: return CmdResult(-1, "No se encontró avdmanager (Command-line Tools)")
        return run(tool, listOf("delete", "avd", "-n", name), onLine = onLine)
    }

    fun avdHome(): File =
        System.getenv("ANDROID_AVD_HOME")?.let(::File)
            ?: System.getenv("ANDROID_USER_HOME")?.let { File(it, "avd") }
            ?: File(System.getProperty("user.home"), ".android/avd")

    /** Carpeta real del teléfono: se lee de NOMBRE.ini (path=...), porque puede estar en otro disco. */
    fun avdDir(name: String): File {
        val ini = File(avdHome(), "$name.ini")
        if (ini.isFile) {
            ini.readLines().forEach { l ->
                val i = l.indexOf('=')
                if (i > 0 && l.substring(0, i).trim() == "path") {
                    val dir = File(l.substring(i + 1).trim())
                    if (dir.isDirectory) return dir
                }
            }
        }
        return File(avdHome(), "$name.avd")
    }

    private fun configFile(name: String) = File(avdDir(name), "config.ini")

    private fun readConfig(cfg: File): LinkedHashMap<String, String> {
        val map = LinkedHashMap<String, String>()
        if (!cfg.isFile) return map
        cfg.readLines().forEach { l ->
            val i = l.indexOf('=')
            if (i > 0) map[l.substring(0, i).trim()] = l.substring(i + 1).trim()
        }
        return map
    }

    private fun writeConfig(cfg: File, map: Map<String, String>) =
        cfg.writeText(map.entries.joinToString("\n", postfix = "\n") { "${it.key}=${it.value}" })

    /** Ajustes extra: RAM, teclado de la PC y aceleración gráfica. */
    fun tweakConfig(name: String, ramMb: Int) {
        val cfg = configFile(name)
        if (!cfg.isFile) return
        val map = readConfig(cfg)
        map["hw.ramSize"] = ramMb.toString()
        map["hw.keyboard"] = "yes"
        map["hw.gpu.enabled"] = "yes"
        map["hw.gpu.mode"] = "auto"
        writeConfig(cfg, map)
    }

    /** Resolución actual del teléfono (ancho x alto), o null si no se puede leer. */
    fun readScreen(name: String): Pair<Int, Int>? {
        val map = readConfig(configFile(name))
        val w = map["hw.lcd.width"]?.toIntOrNull() ?: return null
        val h = map["hw.lcd.height"]?.toIntOrNull() ?: return null
        return w to h
    }

    /** Cambia el tamaño de pantalla: teléfono, tablet o toda la pantalla del monitor. */
    fun setScreen(name: String, mode: ScreenMode) {
        val cfg = configFile(name)
        if (!cfg.isFile) return
        val map = readConfig(cfg)
        val (w, h) = mode.resolution()
        map["hw.lcd.width"] = w.toString()
        map["hw.lcd.height"] = h.toString()
        map["hw.lcd.density"] = mode.density().toString()
        map["skin.name"] = "${w}x$h"
        map["skin.path"] = "_no_skin"
        map["skin.dynamic"] = "yes"
        map["showDeviceFrame"] = "no"
        map["hw.initialOrientation"] = if (w > h) "landscape" else "portrait"
        writeConfig(cfg, map)
        // La resolución cambió: la sesión guardada ya no sirve, se enciende desde cero
        File(avdDir(name), "snapshots").deleteRecursively()
    }

    /** Argumentos para encender un teléfono (incluye el tamaño en Tablet / Pantalla completa). */
    private fun launchArgs(name: String): List<String> {
        val args = mutableListOf("-avd", name)
        val size = readScreen(name)
        val mode = ScreenMode.detect(size)
        if (size != null && (mode == ScreenMode.FULL || mode == ScreenMode.TABLET)) {
            args += listOf("-skin", "${size.first}x${size.second}")
        }
        return args
    }

    fun isFullScreen(name: String) = ScreenMode.detect(readScreen(name)) == ScreenMode.FULL

    /** Copia el script de ventanas a %USERPROFILE%\.emudroid\ventana.ps1 */
    private fun windowScript(): File {
        val f = File(System.getProperty("user.home"), ".emudroid/ventana.ps1")
        f.parentFile.mkdirs()
        val text = AndroidSdk::class.java.getResource("/ventana.ps1")?.readText() ?: error("Falta ventana.ps1")
        if (!f.isFile || f.readText() != text) f.writeText(text)
        return f
    }

    private fun powershell(args: List<String>, timeoutSec: Long = 60): CmdResult = try {
        val p = ProcessBuilder(listOf("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass") + args)
            .redirectErrorStream(true).start()
        p.outputStream.close()
        val out = StringBuffer()
        val reader = thread(isDaemon = true) { runCatching { out.append(p.inputStream.bufferedReader().readText()) } }
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            CmdResult(-1, "Tiempo agotado")
        } else {
            reader.join(2000)
            CmdResult(p.exitValue(), out.toString().trim())
        }
    } catch (e: Exception) {
        CmdResult(-1, "Error: ${e.message}")
    }

    /** Pone la ventana del emulador en pantalla completa sin bordes (full = true) o en ventana normal. */
    fun windowMode(name: String, full: Boolean): CmdResult {
        if (!isWindows) return CmdResult(-1, "Solo disponible en Windows")
        return powershell(
            listOf("-File", windowScript().absolutePath, "-Name", name, "-Mode", if (full) "full" else "window"),
            timeoutSec = 240,
        )
    }

    /** Crea en el escritorio un acceso directo que enciende el teléfono con doble clic. */
    fun createDesktopShortcut(name: String): CmdResult {
        if (!isWindows) return CmdResult(-1, "Los accesos directos solo están disponibles en Windows")
        fun q(s: String) = s.replace("'", "''")
        val label = "Android - ${prettyName(name)}".replace(Regex("[\\\\/:*?\"<>|]"), "")
        val emuArgs = launchArgs(name).joinToString(" ")
        // En pantalla completa el acceso directo pasa por el script que quita los bordes
        val (target, arguments) = if (isFullScreen(name)) {
            "powershell.exe" to "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File \"${windowScript().absolutePath}\" " +
                "-Name $name -Mode full -Emulator \"${emulator.absolutePath}\" -EmuArgs \"$emuArgs\""
        } else {
            emulator.absolutePath to emuArgs
        }
        val d = '$'
        val script = """
            ${d}d = [Environment]::GetFolderPath('Desktop')
            ${d}s = (New-Object -ComObject WScript.Shell).CreateShortcut((Join-Path ${d}d '${q(label)}.lnk'))
            ${d}s.TargetPath = '${q(target)}'
            ${d}s.Arguments = '${q(arguments)}'
            ${d}s.WorkingDirectory = '${q(emulator.parentFile.absolutePath)}'
            ${d}s.IconLocation = '${q(emulator.absolutePath)},0'
            ${d}s.Description = 'Encender ${q(prettyName(name))} (EmuDroid)'
            ${d}s.Save()
            Write-Output (Join-Path ${d}d '${q(label)}.lnk')
        """.trimIndent()
        val encoded = java.util.Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        return powershell(listOf("-EncodedCommand", encoded))
    }

    fun startEmulator(name: String, coldBoot: Boolean, wipe: Boolean, onLine: (String) -> Unit): Process {
        val args = (listOf(emulator.absolutePath) + launchArgs(name)).toMutableList()
        if (coldBoot) args += "-no-snapshot-load"
        if (wipe) args += "-wipe-data"
        val p = prepare(ProcessBuilder(args).redirectErrorStream(true)).start()
        thread(isDaemon = true) {
            runCatching { p.inputStream.bufferedReader().useLines { it.forEach(onLine) } }
        }
        return p
    }

    // ---------- Descargas ----------

    fun sdkInstall(packages: List<String>, onLine: (String) -> Unit): CmdResult {
        val tool = sdkmanager ?: return CmdResult(-1, "No se encontró sdkmanager (Command-line Tools)")
        return run(tool, packages, input = "y\n".repeat(60), timeoutSec = 4 * 3600, onLine = onLine)
    }

    // ---------- Acciones sobre el teléfono ----------

    fun screenshot(serial: String, dest: File): Boolean {
        val p = ProcessBuilder(adb.absolutePath, "-s", serial, "exec-out", "screencap", "-p")
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val bytes = p.inputStream.readBytes()
        p.waitFor(30, TimeUnit.SECONDS)
        if (bytes.size < 100) return false
        dest.parentFile?.mkdirs()
        dest.writeBytes(bytes)
        return true
    }
}
