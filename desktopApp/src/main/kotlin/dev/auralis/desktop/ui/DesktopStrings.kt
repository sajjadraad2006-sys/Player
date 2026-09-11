package dev.auralis.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

/** Android's translation catalog plus strings specific to the Windows experience. */
class DesktopStrings(val arabic: Boolean) {
    fun text(english: String): String = if(arabic) translations[english.lowercase(Locale.ROOT)] ?: english else english
    fun songs(count: Int) = if(arabic) "$count أغنية" else "$count songs"

    companion object {
        private val translations by lazy {
            fun android(name: String): Map<String, String> {
                val factory = DocumentBuilderFactory.newInstance()
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                val stream = DesktopStrings::class.java.getResourceAsStream("/i18n/android_$name.xml") ?: return emptyMap()
                return stream.use {
                    val nodes = factory.newDocumentBuilder().parse(it).getElementsByTagName("string")
                    (0 until nodes.length).associate { index ->
                        val node = nodes.item(index)
                        node.attributes.getNamedItem("name").nodeValue to node.textContent.replace("\\'", "'").replace("\\n", "\n")
                    }
                }
            }
            val arabic = android("ar")
            val common = android("en").mapNotNull { (key, english) -> arabic[key]?.let { english.lowercase(Locale.ROOT) to it } }.toMap()
            val desktop = Properties()
            DesktopStrings::class.java.getResourceAsStream("/i18n/desktop_ar.properties")?.reader(Charsets.UTF_8)?.use(desktop::load)
            common + desktop.entries.associate { it.key.toString().lowercase(Locale.ROOT) to it.value.toString() }
        }
    }
}

val LocalDesktopStrings = staticCompositionLocalOf { DesktopStrings(false) }
@Composable fun uiText(english: String) = LocalDesktopStrings.current.text(english)
