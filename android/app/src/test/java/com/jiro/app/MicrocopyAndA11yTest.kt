package com.jiro.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MicrocopyAndA11yTest {

    @Test
    fun testStringsXmlMicrocopyRules() {
        val stringsFile = File("src/main/res/values/strings.xml")
        assertTrue("strings.xml must exist", stringsFile.exists())
        val content = stringsFile.readText()

        // 1. Must use "In your jar" or "jar", not "vault balance" in user-facing copy
        assertFalse("Should not use 'vault balance' in copy", content.contains("vault balance", ignoreCase = true))
        assertTrue("Must contain 'In your jar'", content.contains("In your jar"))

        // 2. Must contain "Owed"
        assertTrue("Must contain 'Owed'", content.contains("Owed", ignoreCase = true))

        // 3. Must specify "tokenized" for stocks
        assertTrue("Must mention tokenized security or stock", content.contains("Tokenized", ignoreCase = true))

        // 4. Must contain unaudited notice
        assertTrue("Must contain unaudited notice", content.contains("Unaudited", ignoreCase = true))

        // 5. Must not make return promises or projected returns
        assertFalse("No projected returns", content.contains("projected return", ignoreCase = true))
        assertFalse("No guaranteed profit", content.contains("guaranteed", ignoreCase = true))
    }

    @Test
    fun testNeverShowEmptyStockListRule() {
        val fallback = AssetCatalogRepository.getFallbackCatalog()
        assertFalse("Fallback catalog must not be empty", fallback.isEmpty())
        assertTrue("Fallback must contain at least 4 verified stocks", fallback.size >= 4)
        assertTrue(fallback.any { it.symbol.startsWith("VOO") })
        assertTrue(fallback.any { it.symbol.startsWith("AAPL") })
    }
}
