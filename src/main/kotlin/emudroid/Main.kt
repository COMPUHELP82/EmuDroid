package emudroid

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDropEvent
import java.io.File

fun main() = application {
    val windowState = rememberWindowState(width = 1220.dp, height = 800.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "EmuDroid – Emulador de Android para Windows",
        state = windowState,
    ) {
        val scope = rememberCoroutineScope()
        val app = remember { AppState(scope) }

        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(1000, 660)
            // Arrastrar y soltar archivos .apk en cualquier parte de la ventana
            window.contentPane.dropTarget = object : DropTarget() {
                @Synchronized
                override fun drop(evt: DropTargetDropEvent) {
                    try {
                        evt.acceptDrop(DnDConstants.ACTION_COPY)
                        val files = (evt.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                            ?.filterIsInstance<File>().orEmpty()
                        app.installApks(files)
                        evt.dropComplete(true)
                    } catch (e: Exception) {
                        evt.dropComplete(false)
                    }
                }
            }
            app.start()
        }

        EmuTheme { MainLayout(app, window) }
    }
}
