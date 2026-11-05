package deghat.farhad.csvision.core.csv

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CsvParserTest {
    @Test
    fun readsUnquotedRecordsOneAtATime() {
        StringReader("Theo,Jansen,5\r\nFiona,de Vries,7").use { reader ->
            val parser = CsvParser(reader)

            assertEquals(listOf("Theo", "Jansen", "5"), parser.readRecord())
            assertEquals(listOf("Fiona", "de Vries", "7"), parser.readRecord())
            assertNull(parser.readRecord())
        }
    }

    @Test
    fun readsQuotedFieldsContainingCommas() {
        StringReader("\"Theo, Jr.\",\"Jansen\",\"5\"").use { reader ->
            val parser = CsvParser(reader)

            assertEquals(listOf("Theo, Jr.", "Jansen", "5"), parser.readRecord())
            assertNull(parser.readRecord())
        }
    }

    @Test
    fun readsEscapedQuotesInsideQuotedFields() {
        StringReader("\"Theo \"\"TJ\"\"\",Jansen,5").use { reader ->
            val parser = CsvParser(reader)

            assertEquals(listOf("Theo \"TJ\"", "Jansen", "5"), parser.readRecord())
            assertNull(parser.readRecord())
        }
    }

    @Test
    fun readsQuotedFieldsContainingNewlines() {
        StringReader("\"Theo\nTJ\",Jansen,5\r\nFiona,de Vries,7").use { reader ->
            val parser = CsvParser(reader)

            assertEquals(listOf("Theo\nTJ", "Jansen", "5"), parser.readRecord())
            assertEquals(listOf("Fiona", "de Vries", "7"), parser.readRecord())
            assertNull(parser.readRecord())
        }
    }

    @Test
    fun preservesEmptyFieldsIncludingTrailingField() {
        StringReader("Theo,,5,").use { reader ->
            val parser = CsvParser(reader)

            assertEquals(listOf("Theo", "", "5", ""), parser.readRecord())
            assertNull(parser.readRecord())
        }
    }

    @Test
    fun ignoresBomOnlyAtStartOfInput() {
        StringReader("\uFEFF\"Theo\",Jansen,5\r\n\uFEFFFiona,de Vries,7").use { reader ->
            val parser = CsvParser(reader)

            assertEquals(listOf("Theo", "Jansen", "5"), parser.readRecord())
            assertEquals(listOf("\uFEFFFiona", "de Vries", "7"), parser.readRecord())
            assertNull(parser.readRecord())
        }
    }

    @Test
    fun rejectsUnterminatedQuotedFields() {
        StringReader("\"Theo,Jansen,5").use { reader ->
            val parser = CsvParser(reader)

            val error = assertThrows(IllegalArgumentException::class.java) {
                parser.readRecord()
            }
            assertEquals("Unterminated quoted field.", error.message)
        }
    }

    @Test
    fun rejectsQuotesInsideUnquotedFields() {
        StringReader("Th\"eo,Jansen,5").use { reader ->
            val parser = CsvParser(reader)

            val error = assertThrows(IllegalArgumentException::class.java) {
                parser.readRecord()
            }
            assertEquals("Unexpected quote in a field.", error.message)
        }
    }

    @Test
    fun rejectsCharactersAfterClosingQuote() {
        StringReader("\"Theo\"x,Jansen,5").use { reader ->
            val parser = CsvParser(reader)

            val error = assertThrows(IllegalArgumentException::class.java) {
                parser.readRecord()
            }
            assertEquals("Unexpected character after a closing quote.", error.message)
        }
    }
}
