package deghat.farhad.csvision.core.csv

import java.io.PushbackReader
import java.io.Reader

/**
 * Reads one CSV record at a time. This initial version supports unquoted fields only.
 * The caller owns the supplied [Reader] and is responsible for closing it.
 */
class CsvParser(reader: Reader) {
    private val input = PushbackReader(reader, 1)

    /** Returns the next record, or null when no records remain. */
    fun readRecord(): List<String>? {
        val fields = mutableListOf<String>()
        val field = StringBuilder()

        while (true) {
            when (val character = input.read()) {
                -1 -> {
                    if (fields.isEmpty() && field.isEmpty()) return null
                    break
                }

                ','.code -> {
                    fields.add(field.toString())
                    field.clear()
                }

                '\n'.code -> break

                '\r'.code -> {
                    val next = input.read()
                    if (next != '\n'.code && next != -1) input.unread(next)
                    break
                }

                '"'.code -> throw IllegalArgumentException("Quoted fields are not supported yet.")

                else -> field.append(character.toChar())
            }
        }

        fields.add(field.toString())
        return fields
    }
}
