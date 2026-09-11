package dev.auralis.desktop.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

/**
 * Windows desktop native folder picker helper.
 */
object DesktopFolderPicker {

    suspend fun pickFolder(): File? = withContext(Dispatchers.IO) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (_: Exception) {
        }

        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Select Music Folder for Lyrune"
            isAcceptAllFileFilterUsed = false
            isMultiSelectionEnabled = false
        }

        val result = chooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile
        } else {
            null
        }
    }
}
