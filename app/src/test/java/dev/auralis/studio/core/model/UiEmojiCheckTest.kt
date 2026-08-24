package dev.auralis.studio.core.model

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UiEmojiCheckTest {

    private fun isEmoji(codePoint: Int): Boolean {
        // Exclude standard math / punctuation / ASCII / Arabic / Latin
        if (codePoint < 0x2000) return false

        // Common symbols that are NOT emojis
        if (codePoint in 0x2000..0x206F) return false // General Punctuation
        if (codePoint in 0x2070..0x209F) return false // Superscripts and Subscripts
        if (codePoint in 0x20A0..0x20CF) return false // Currency Symbols
        if (codePoint in 0x2100..0x214F) return false // Letterlike Symbols
        if (codePoint in 0x2190..0x21FF) return false // Arrows
        if (codePoint in 0x2200..0x22FF) return false // Mathematical Operators
        if (codePoint in 0x2300..0x23FF) return false // Misc Technical
        if (codePoint in 0x25A0..0x25FF) return false // Geometric Shapes

        return (codePoint in 0x1F600..0x1F64F) || // Emoticons
            (codePoint in 0x1F300..0x1F5FF) || // Misc Symbols and Pictographs (e.g. 🔁, 🔂)
            (codePoint in 0x1F680..0x1F6FF) || // Transport and Map
            (codePoint in 0x1F900..0x1F9FF) || // Supplemental Symbols and Pictographs
            (codePoint in 0x1FA70..0x1FAFF) || // Symbols and Pictographs Extended-A
            (codePoint in 0x2600..0x26FF) ||   // Misc symbols
            (codePoint in 0x2700..0x27BF) ||   // Dingbats
            (codePoint in 0x1F1E6..0x1F1FF)   // Flags
    }

    private fun findEmojisInString(str: String): List<String> {
        val emojis = mutableListOf<String>()
        var offset = 0
        while (offset < str.length) {
            val codePoint = str.codePointAt(offset)
            if (isEmoji(codePoint)) {
                emojis.add(String(Character.toChars(codePoint)))
            }
            offset += Character.charCount(codePoint)
        }
        return emojis
    }

    @Test
    fun noAuthoredEmojisInStringResources() {
        val resDir = File("src/main/res")
        val stringFiles = resDir.walkTopDown().filter { it.name == "strings.xml" }.toList()
        assertTrue("String resource files must exist", stringFiles.isNotEmpty())

        val violations = mutableListOf<String>()
        for (file in stringFiles) {
            file.readLines().forEachIndexed { index, line ->
                val emojis = findEmojisInString(line)
                if (emojis.isNotEmpty()) {
                    violations.add("${file.path}:${index + 1}: Found emoji '${emojis.joinToString(" ")}' in: ${line.trim()}")
                }
            }
        }

        assertTrue("String resources must contain zero emojis. Found:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test
    fun noAuthoredEmojisInUiSourceFiles() {
        val uiDir = File("src/main/java/dev/auralis/studio/ui")
        val uiFiles = uiDir.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("UI source files must exist", uiFiles.isNotEmpty())

        val violations = mutableListOf<String>()
        for (file in uiFiles) {
            file.readLines().forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed

                val emojis = findEmojisInString(line)
                if (emojis.isNotEmpty()) {
                    violations.add("${file.path}:${index + 1}: Found emoji '${emojis.joinToString(" ")}' in: ${line.trim()}")
                }
            }
        }

        assertTrue("UI source files must contain zero authored emojis. Found:\n${violations.joinToString("\n")}", violations.isEmpty())
    }
}
