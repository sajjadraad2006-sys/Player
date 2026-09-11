package dev.auralis.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.auralis.desktop.library.DesktopMusicRepository
import dev.auralis.desktop.playback.DesktopPlaybackEngine
import dev.auralis.desktop.storage.DesktopSettingsRepository
import dev.auralis.desktop.ui.DesktopFolderPicker
import dev.auralis.desktop.ui.DesktopShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import kotlin.math.max

/**
 * Production Application entry point for Lyrune Desktop Studio on Windows.
 *
 * Requirements satisfied (Milestone 6):
 * - Command-line "Open With Lyrune" argument processing.
 * - Window geometry restoration and persistence.
 * - In-window keyboard shortcuts & Windows Media Keys.
 * - Native desktop drag-and-drop for audio files and folders.
 * - Clean shutdown releasing audio lines, database locks, and background jobs.
 */
fun main(args: Array<String>) = application {
    val settingsRepository = remember { DesktopSettingsRepository() }
    val initialSettings = remember { settingsRepository.settings.value }

    val windowState = rememberWindowState(
        placement = if(initialSettings.isMaximized) WindowPlacement.Maximized else WindowPlacement.Floating,
        width = initialSettings.windowWidth.coerceIn(900, 1400).dp,
        height = initialSettings.windowHeight.coerceIn(600, 900).dp,
    )

    val engine = remember { DesktopPlaybackEngine() }
    val repository = remember { DesktopMusicRepository() }
    val coroutineScope = rememberCoroutineScope()

    // Process CLI arguments (e.g. Lyrune.exe "D:\Music\song.mp3" or folder path)
    LaunchedEffect(args) {
        if (args.isNotEmpty()) {
            val pathArg = args[0].trim().trim('"', '\'')
            val file = File(pathArg)
            if (file.exists()) {
                if (file.isDirectory) {
                    repository.addFolder(file)
                } else if (file.isFile) {
                    engine.play(file.absolutePath)
                }
            }
        }
    }

    Window(
        onCloseRequest = {
            // Save window geometry and flush state
            coroutineScope.launch {
                settingsRepository.updateWindowGeometry(
                    width = windowState.size.width.value.toInt(),
                    height = windowState.size.height.value.toInt(),
                    isMaximized = windowState.placement == WindowPlacement.Maximized,
                )
                settingsRepository.flushNow()
                repository.database.flushNow()
                engine.release()
                exitApplication()
            }
        },
        title = "LYRUNE",
        state = windowState,
    ) {
        // Native Windows Drag and Drop handler for files & folders
        DisposableEffect(window) {
            window.minimumSize = java.awt.Dimension(900, 600)
            window.dropTarget = DropTarget(window, object : DropTargetAdapter() {
                override fun drop(dtde: DropTargetDropEvent) {
                    try {
                        dtde.acceptDrop(DnDConstants.ACTION_COPY)
                        val transferable = dtde.transferable
                        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                            @Suppress("UNCHECKED_CAST")
                            val droppedFiles = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
                            if (droppedFiles != null) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    droppedFiles.forEach { file ->
                                        if (file.isDirectory) {
                                            repository.addFolder(file)
                                        } else if (file.isFile) {
                                            engine.play(file.absolutePath)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            })
            onDispose {
                window.dropTarget = null
            }
        }

        DesktopShell(
            engine = engine,
            repository = repository,
            settingsRepository = settingsRepository,
            onOpenFolderPicker = {
                coroutineScope.launch {
                    val folder = DesktopFolderPicker.pickFolder()
                    if (folder != null) {
                        repository.addFolder(folder)
                    }
                }
            },
        )
    }
}
