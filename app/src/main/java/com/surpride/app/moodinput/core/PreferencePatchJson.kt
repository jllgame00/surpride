package com.surpride.app.moodinput.core

import com.surpride.app.experience.core.*

sealed interface PatchDecodeResult {
    data class Success(val patch: PreferencePatch) : PatchDecodeResult
    data object Invalid : PatchDecodeResult
}

/** Strict, bounded JSON boundary. No coercion, duplicate keys, arbitrary fields, or nested objects. */
object PreferencePatchJson {
    private val keys = setOf("mood", "scope", "categories", "excludedCategories", "removedCategories", "companion",
        "availableMinutes", "returnByEpochMillis", "returnToStart", "randomness")

    fun decode(json: String): PatchDecodeResult {
        if (json.length > 4096) return PatchDecodeResult.Invalid
        return try {
            val fields = Reader(json).readObject()
            require(fields.keys.all { it in keys })
            val patch = PreferencePatch(
                mood = enumValue<Mood>(fields["mood"]), scope = enumValue<TravelScope>(fields["scope"]),
                categories = enumSet(fields["categories"]), excludedCategories = enumSet(fields["excludedCategories"]),
                removedCategories = enumSet(fields["removedCategories"]), companion = enumValue<Companion>(fields["companion"]),
                availableMinutes = integer(fields["availableMinutes"]), returnByEpochMillis = long(fields["returnByEpochMillis"]),
                returnToStart = fields["returnToStart"]?.let { require(it is Boolean); it },
                randomness = integer(fields["randomness"])
            )
            if (patch.isValid()) PatchDecodeResult.Success(patch) else PatchDecodeResult.Invalid
        } catch (_: IllegalArgumentException) { PatchDecodeResult.Invalid }
    }

    fun encode(patch: PreferencePatch): String {
        require(patch.isValid()) { "Invalid preference patch" }
        val pairs = mutableListOf<String>()
        fun add(key: String, value: Any?) {
            if (value == null) return
            val encoded = when (value) {
                is Enum<*> -> "\"${value.name}\""
                is Set<*> -> value.map { (it as Enum<*>).name }.sorted().joinToString(",", "[", "]") { "\"$it\"" }
                else -> value.toString()
            }
            pairs += "\"$key\":$encoded"
        }
        add("mood", patch.mood); add("scope", patch.scope); add("categories", patch.categories)
        add("excludedCategories", patch.excludedCategories); add("removedCategories", patch.removedCategories)
        add("companion", patch.companion); add("availableMinutes", patch.availableMinutes)
        add("returnByEpochMillis", patch.returnByEpochMillis); add("returnToStart", patch.returnToStart); add("randomness", patch.randomness)
        return pairs.joinToString(",", "{", "}")
    }

    private inline fun <reified T : Enum<T>> enumValue(value: Any?): T? {
        if (value == null) return null
        require(value is String)
        return enumValues<T>().firstOrNull { it.name == value } ?: throw IllegalArgumentException()
    }
    private fun enumSet(value: Any?): Set<PlaceCategory>? {
        if (value == null) return null
        require(value is List<*>)
        val items = value.map { require(it != null); enumValue<PlaceCategory>(it)!! }
        require(items.size == items.toSet().size)
        return items.toSet()
    }
    private fun long(value: Any?): Long? { if (value == null) return null; require(value is Long); return value }
    private fun integer(value: Any?): Int? = long(value)?.let { require(it in Int.MIN_VALUE..Int.MAX_VALUE); it.toInt() }

    private class Reader(private val input: String) {
        private var at = 0
        private fun space() { while (at < input.length && input[at] in " \r\n\t") at++ }
        private fun take(c: Char) { space(); require(at < input.length && input[at++] == c) }
        private fun peek(): Char? { space(); return input.getOrNull(at) }
        fun readObject(): Map<String, Any?> {
            take('{')
            val result = linkedMapOf<String, Any?>()
            if (peek() != '}') while (true) {
                val key = string(); require(!result.containsKey(key)); take(':'); result[key] = value()
                if (peek() != ',') break
                take(',')
            }
            take('}'); space(); require(at == input.length)
            return result
        }
        private fun value(): Any? = when (peek()) {
            '"' -> string()
            '[' -> {
                take('['); val list = mutableListOf<Any?>()
                if (peek() != ']') while (true) {
                    // Arrays contain enum strings only. Reject nested containers and null members.
                    require(peek() == '"'); list += string()
                    if (peek() != ',') break
                    take(',')
                }
                take(']'); list
            }
            't' -> { literal("true"); true }
            'f' -> { literal("false"); false }
            'n' -> { literal("null"); null }
            else -> {
                val start = at
                if (input.getOrNull(at) == '-') at++
                require(input.getOrNull(at)?.isDigit() == true)
                if (input[at] == '0') at++ else while (input.getOrNull(at)?.let { it in '0'..'9' } == true) at++
                input.substring(start, at).toLongOrNull() ?: throw IllegalArgumentException()
            }
        }
        private fun literal(word: String) { require(input.startsWith(word, at)); at += word.length }
        private fun string(): String {
            take('"'); val result = StringBuilder()
            while (at < input.length) {
                val c = input[at++]
                if (c == '"') return result.toString()
                require(c >= ' ')
                if (c != '\\') result.append(c) else {
                    require(at < input.length)
                    when (val escaped = input[at++]) {
                        '"', '\\', '/' -> result.append(escaped)
                        'b' -> result.append('\b'); 'f' -> result.append('\u000C'); 'n' -> result.append('\n')
                        'r' -> result.append('\r'); 't' -> result.append('\t')
                        'u' -> { require(at + 4 <= input.length); result.append(input.substring(at, at + 4).toIntOrNull(16)?.toChar() ?: throw IllegalArgumentException()); at += 4 }
                        else -> throw IllegalArgumentException()
                    }
                }
            }
            throw IllegalArgumentException()
        }
    }
}
