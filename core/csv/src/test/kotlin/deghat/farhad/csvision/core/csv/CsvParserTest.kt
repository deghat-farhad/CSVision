package deghat.farhad.csvision.core.csv

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
