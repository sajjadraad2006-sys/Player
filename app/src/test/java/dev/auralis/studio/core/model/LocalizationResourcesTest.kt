package dev.auralis.studio.core.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationResourcesTest {
    @Test fun `Arabic resources contain every English application key`() {
        val english = resourceKeys(File("src/main/res/values/strings.xml"))
        val arabic = resourceKeys(File("src/main/res/values-ar/strings.xml"))
        assertTrue("English resources should not be empty", english.isNotEmpty())
        assertEquals(english, arabic)
    }

    @Test fun `Compose UI contains no embedded Arabic interface literals`() {
        val sourceRoot = File("src/main/java/dev/auralis/studio/ui")
        val arabic = Regex("[\\u0600-\\u06FF]")
        val offenders = sourceRoot.walkTopDown().filter { it.extension == "kt" }
            .filter { arabic.containsMatchIn(it.readText()) }.map { it.relativeTo(sourceRoot).path }.toList()
        assertTrue("Move Arabic UI text to values-ar resources: $offenders", offenders.isEmpty())
    }

    private fun resourceKeys(file: File): Set<String> {
        require(file.isFile) { "Missing ${file.path}" }
        return Regex("name=\"([^\"]+)\"").findAll(file.readText()).map { it.groupValues[1] }.toSet()
    }
}
