package deghat.farhad.csvision.core.csv

import java.io.PushbackReader
import java.io.Reader

class CsvParser(reader: Reader) {
    private val input = PushbackReader(reader, 1)

    fun readRecord(): List<String>? {
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var state = FieldState.START

        while (true) {
            val character = input.read()
            if (character == -1) {
                require(state != FieldState.QUOTED) { "Unterminated quoted field." }
                if (fields.isEmpty() && state == FieldState.START) return null
                break
            }

            if (state == FieldState.QUOTED) {
                if (character == '"'.code) {
                    state = FieldState.AFTER_QUOTE
                } else {
                    field.append(character.toChar())
                }
                continue
            }

            when (character) {
                ','.code -> {
                    fields.add(field.toString())
                    field.clear()
                    state = FieldState.START
                }

                '\n'.code -> break

                '\r'.code -> {
                    val next = input.read()
                    if (next != '\n'.code && next != -1) input.unread(next)
                    break
                }

                '"'.code -> {
                    require(state == FieldState.START || state == FieldState.AFTER_QUOTE) {
                        "Unexpected quote in a field."
                    }
                    if (state == FieldState.AFTER_QUOTE) field.append('"')
                    state = FieldState.QUOTED
                }

                else -> {
                    require(state != FieldState.AFTER_QUOTE) {
                        "Unexpected character after a closing quote."
                    }
                    field.append(character.toChar())
                    state = FieldState.UNQUOTED
                }
            }
        }

        fields.add(field.toString())
        return fields
    }

    private enum class FieldState {
        START,
        UNQUOTED,
        QUOTED,
        AFTER_QUOTE
    }
}
