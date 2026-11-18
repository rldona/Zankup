package com.zankup.android.designsystem

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every English text of every module has its Spanish one, and no Spanish text is left over. Texts
 * marked translatable="false" (the brand) are English only.
 */
class StringsTest {
    private val root = generateSequence(File("").absoluteFile) {
        it.parentFile
    }.first { File(it, "settings.gradle.kts").exists() }

    private fun names(file: File): Set<String> {
        if (!file.exists()) return emptySet()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val elements = doc.documentElement.childNodes
        return (0 until elements.length)
            .map { elements.item(it) }
            .filterIsInstance<org.w3c.dom.Element>()
            .filter { it.getAttribute("translatable") != "false" }
            .map { "${it.tagName}/${it.getAttribute("name")}" }
            .toSet()
    }

    @Test
    fun spanishHasEveryText() {
        val files =
            root
                .walkTopDown()
                .onEnter { it.name != "build" && !it.name.startsWith(".") }
                .filter { it.name == "strings.xml" && it.parentFile.name == "values" }
                .toList()
        assertTrue("no strings.xml found under $root", files.isNotEmpty())
        files.forEach { english ->
            val spanish = File(english.parentFile.parentFile, "values-es/strings.xml")
            val en = names(english)
            val es = names(spanish)
            assertEquals("missing in ${spanish.relativeTo(root)}", emptySet<String>(), en - es)
            assertEquals("only in ${spanish.relativeTo(root)}", emptySet<String>(), es - en)
        }
    }
}
