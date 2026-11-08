package io.github.magicalwig34653.visualinspector.bridge

import com.google.gson.JsonObject
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader

/**
 * Bridge protocol v1, Kotlin side. Normative spec: docs/protocol.md; reference implementation:
 * browser-extension/src/protocol.ts; shared vectors: test-fixtures/protocol/vectors.json.
 * Same check order and error codes as the TypeScript implementation. Never throws on input.
 */
enum class ErrorCode(val wire: String) {
    BAD_JSON("bad_json"),
    TOO_LARGE("too_large"),
    BAD_VERSION("bad_version"),
    UNKNOWN_TYPE("unknown_type"),
    UNKNOWN_FIELD("unknown_field"),
    BAD_FIELD("bad_field"),
    BAD_PATH("bad_path"),
    NOT_FOUND("not_found"),
    DENIED("denied");

    companion object {
        fun fromWire(s: String): ErrorCode? = entries.firstOrNull { it.wire == s }
    }
}

data class Ancestor(val path: String, val offset: Int, val label: String?)

sealed interface BridgeMessage {
    // Page to IDE
    data class Hello(val id: String?) : BridgeMessage
    data class Navigate(
        val path: String, val offset: Int, val line: Int?, val label: String?,
        val index: Int?, val count: Int?, val id: String?,
    ) : BridgeMessage
    data class Unmappable(val tag: String, val label: String?, val ancestor: Ancestor?, val id: String?) : BridgeMessage

    // IDE to page
    data class Highlight(val path: String, val offset: Int, val index: Int?, val id: String?) : BridgeMessage
    data class Clear(val id: String?) : BridgeMessage
    data class Ack(val ref: String?) : BridgeMessage
    data class Error(val code: ErrorCode, val ref: String?) : BridgeMessage
}

sealed interface ValidationResult {
    data class Valid(val message: BridgeMessage) : ValidationResult
    data class Rejected(val code: ErrorCode) : ValidationResult
}

/** Parsed JSON value: integers keep their literal token so 1.0, 1e2 and -0 are never mistaken for integers. */
private sealed interface J {
    data class Num(val token: String) : J
    data class Str(val value: String) : J
    data class Obj(val map: Map<String, J>) : J
    object Other : J // true, false, null, array
}

private class BadJson : RuntimeException(null, null, false, false)

object BridgeMessageValidator {
    const val VERSION = 1
    const val MAX_MESSAGE_CHARS = 4096
    const val MAX_PATH_CHARS = 512
    const val MAX_SEGMENT_CHARS = 255
    const val MAX_LABEL_CHARS = 128
    const val MAX_TAG_CHARS = 32
    const val MAX_ID_CHARS = 64
    const val MAX_INT = 2147483647L
    const val MAX_INSTANCES = 10000L

    private class Shape(val required: List<String>, val optional: List<String>)

    private val PAGE_SHAPES = mapOf(
        "hello" to Shape(emptyList(), listOf("id")),
        "navigate" to Shape(listOf("path", "offset"), listOf("line", "label", "index", "count", "id")),
        "unmappable" to Shape(listOf("tag"), listOf("label", "ancestor", "id")),
    )
    private val IDE_SHAPES = mapOf(
        "highlight" to Shape(listOf("path", "offset"), listOf("index", "id")),
        "clear" to Shape(emptyList(), listOf("id")),
        "ack" to Shape(emptyList(), listOf("ref")),
        "error" to Shape(listOf("code"), listOf("ref")),
    )

    private val ID_PATTERN = Regex("[A-Za-z0-9_.-]+")
    private val TAG_PATTERN = Regex("[A-Za-z][A-Za-z0-9-]*")
    private val INT_TOKEN = Regex("-?(0|[1-9][0-9]*)")

    /** Validates a string received by the IDE from the page. */
    fun validate(raw: String?): ValidationResult = parseWith(raw, PAGE_SHAPES)

    /** Validates a string received by the page from the IDE (used by tests and the self-test). */
    fun validateIde(raw: String?): ValidationResult = parseWith(raw, IDE_SHAPES)

    // Control, format, unpaired surrogate, line and paragraph separator.
    private fun hasForbiddenChar(s: String): Boolean {
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            when (Character.getType(cp).toByte()) {
                Character.CONTROL, Character.FORMAT, Character.SURROGATE,
                Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> return true
            }
            i += Character.charCount(cp)
        }
        return false
    }

    fun isValidPath(path: String): Boolean {
        if (path.isEmpty() || path.length > MAX_PATH_CHARS) return false
        if (hasForbiddenChar(path)) return false
        if (path.any { it == '\\' || it == ':' || it == '%' }) return false
        for (seg in path.split('/')) {
            if (seg.isEmpty() || seg == "." || seg == "..") return false
            if (seg.length > MAX_SEGMENT_CHARS) return false
            if (seg.startsWith("~") || seg.endsWith(".") || seg.endsWith(" ") || seg.startsWith(" ")) return false
        }
        return true
    }

    private fun intOf(v: J?, min: Long, max: Long): Long? {
        if (v !is J.Num || v.token == "-0" || !INT_TOKEN.matches(v.token)) return null
        val n = v.token.toLongOrNull() ?: return null
        return n.takeIf { it in min..max }
    }

    private fun isText(v: J?, max: Int) =
        v is J.Str && v.value.length in 1..max && !hasForbiddenChar(v.value)

    private fun parseJson(raw: String): J {
        val reader = JsonReader(StringReader(raw))
        reader.strictness = Strictness.STRICT
        reader.nestingLimit = MAX_MESSAGE_CHARS // depth <= size; JSON.parse has no limit at these sizes
        val v = readValue(reader)
        if (reader.peek() != JsonToken.END_DOCUMENT) throw BadJson()
        return v
    }

    private fun readValue(r: JsonReader): J = when (r.peek()) {
        JsonToken.BEGIN_OBJECT -> {
            r.beginObject()
            val map = LinkedHashMap<String, J>()
            while (r.hasNext()) {
                val name = r.nextName()
                val value = readValue(r)
                if (map.put(name, value) != null) throw BadJson() // duplicate keys are ambiguous: rejected
            }
            r.endObject()
            J.Obj(map)
        }
        JsonToken.BEGIN_ARRAY -> {
            r.beginArray()
            while (r.hasNext()) readValue(r)
            r.endArray()
            J.Other
        }
        JsonToken.STRING -> J.Str(r.nextString())
        JsonToken.NUMBER -> J.Num(r.nextString())
        JsonToken.BOOLEAN -> { r.nextBoolean(); J.Other }
        JsonToken.NULL -> { r.nextNull(); J.Other }
        else -> throw BadJson()
    }

    private fun checkAncestor(v: J?): ErrorCode? {
        if (v !is J.Obj) return ErrorCode.BAD_FIELD
        if (!setOf("path", "offset", "label").containsAll(v.map.keys)) return ErrorCode.UNKNOWN_FIELD
        if ("path" !in v.map || "offset" !in v.map) return ErrorCode.BAD_FIELD
        val path = v.map["path"] as? J.Str ?: return ErrorCode.BAD_FIELD
        if (!isValidPath(path.value)) return ErrorCode.BAD_PATH
        if (intOf(v.map["offset"], 0, MAX_INT) == null) return ErrorCode.BAD_FIELD
        if ("label" in v.map && !isText(v.map["label"], MAX_LABEL_CHARS)) return ErrorCode.BAD_FIELD
        return null
    }

    private fun checkField(field: String, v: J?): ErrorCode? = when (field) {
        "path" -> if (v !is J.Str) ErrorCode.BAD_FIELD else if (isValidPath(v.value)) null else ErrorCode.BAD_PATH
        "offset" -> if (intOf(v, 0, MAX_INT) != null) null else ErrorCode.BAD_FIELD
        "line" -> if (intOf(v, 1, MAX_INT) != null) null else ErrorCode.BAD_FIELD
        "index" -> if (intOf(v, 0, MAX_INSTANCES - 1) != null) null else ErrorCode.BAD_FIELD
        "count" -> if (intOf(v, 1, MAX_INSTANCES) != null) null else ErrorCode.BAD_FIELD
        "label" -> if (isText(v, MAX_LABEL_CHARS)) null else ErrorCode.BAD_FIELD
        "tag" -> if (v is J.Str && v.value.length <= MAX_TAG_CHARS && TAG_PATTERN.matches(v.value)) null else ErrorCode.BAD_FIELD
        "id", "ref" -> if (v is J.Str && v.value.length <= MAX_ID_CHARS && ID_PATTERN.matches(v.value)) null else ErrorCode.BAD_FIELD
        "code" -> if (v is J.Str && ErrorCode.fromWire(v.value) != null) null else ErrorCode.BAD_FIELD
        "ancestor" -> checkAncestor(v)
        else -> ErrorCode.BAD_FIELD
    }

    private fun parseWith(raw: String?, shapes: Map<String, Shape>): ValidationResult {
        fun fail(c: ErrorCode) = ValidationResult.Rejected(c)
        try {
            if (raw == null) return fail(ErrorCode.BAD_JSON)
            if (raw.length > MAX_MESSAGE_CHARS) return fail(ErrorCode.TOO_LARGE)
            val parsed = try { parseJson(raw) } catch (e: Exception) { return fail(ErrorCode.BAD_JSON) }
            if (parsed !is J.Obj) return fail(ErrorCode.BAD_JSON)
            val m = parsed.map
            if (intOf(m["v"], VERSION.toLong(), VERSION.toLong()) == null) return fail(ErrorCode.BAD_VERSION)
            val type = (m["type"] as? J.Str ?: return fail(ErrorCode.BAD_FIELD)).value
            val shape = shapes[type] ?: return fail(ErrorCode.UNKNOWN_TYPE)
            val allowed = HashSet<String>(listOf("v", "type")).apply { addAll(shape.required); addAll(shape.optional) }
            if (!allowed.containsAll(m.keys)) return fail(ErrorCode.UNKNOWN_FIELD)
            for (f in shape.required) if (f !in m) return fail(ErrorCode.BAD_FIELD)
            for (f in shape.required + shape.optional) {
                if (f !in m) continue
                checkField(f, m[f])?.let { return fail(it) }
            }
            val index = intOf(m["index"], 0, MAX_INSTANCES)
            val count = intOf(m["count"], 1, MAX_INSTANCES)
            if (index != null && count != null && index >= count) return fail(ErrorCode.BAD_FIELD)
            return ValidationResult.Valid(build(type, m))
        } catch (e: Exception) {
            return fail(ErrorCode.BAD_JSON)
        } catch (e: StackOverflowError) {
            return fail(ErrorCode.BAD_JSON)
        }
    }

    private fun str(m: Map<String, J>, k: String) = (m[k] as? J.Str)?.value
    private fun int(m: Map<String, J>, k: String) = intOf(m[k], Long.MIN_VALUE, Long.MAX_VALUE)?.toInt()

    private fun build(type: String, m: Map<String, J>): BridgeMessage = when (type) {
        "hello" -> BridgeMessage.Hello(str(m, "id"))
        "navigate" -> BridgeMessage.Navigate(
            str(m, "path")!!, int(m, "offset")!!, int(m, "line"), str(m, "label"),
            int(m, "index"), int(m, "count"), str(m, "id"),
        )
        "unmappable" -> BridgeMessage.Unmappable(
            str(m, "tag")!!, str(m, "label"),
            (m["ancestor"] as? J.Obj)?.map?.let { Ancestor(str(it, "path")!!, int(it, "offset")!!, str(it, "label")) },
            str(m, "id"),
        )
        "highlight" -> BridgeMessage.Highlight(str(m, "path")!!, int(m, "offset")!!, int(m, "index"), str(m, "id"))
        "clear" -> BridgeMessage.Clear(str(m, "id"))
        "ack" -> BridgeMessage.Ack(str(m, "ref"))
        "error" -> BridgeMessage.Error(ErrorCode.fromWire(str(m, "code")!!)!!, str(m, "ref"))
        else -> error("unreachable")
    }

    /** Serialized IDE message; Gson escapes all strings, so ids and paths cannot break out of the JSON. */
    fun serialize(message: BridgeMessage): String {
        val o = JsonObject()
        o.addProperty("v", VERSION)
        fun opt(k: String, s: String?) { if (s != null) o.addProperty(k, s) }
        fun opt(k: String, n: Int?) { if (n != null) o.addProperty(k, n) }
        when (message) {
            is BridgeMessage.Ack -> { o.addProperty("type", "ack"); opt("ref", message.ref) }
            is BridgeMessage.Error -> { o.addProperty("type", "error"); o.addProperty("code", message.code.wire); opt("ref", message.ref) }
            is BridgeMessage.Clear -> { o.addProperty("type", "clear"); opt("id", message.id) }
            is BridgeMessage.Highlight -> {
                o.addProperty("type", "highlight"); o.addProperty("path", message.path)
                o.addProperty("offset", message.offset); opt("index", message.index); opt("id", message.id)
            }
            else -> throw IllegalArgumentException("not an IDE message: ${message::class.simpleName}")
        }
        return o.toString()
    }

    /** Reply for a valid page message: `ack` carrying the message id as `ref`. */
    fun ackFor(message: BridgeMessage): String {
        val id = when (message) {
            is BridgeMessage.Hello -> message.id
            is BridgeMessage.Navigate -> message.id
            is BridgeMessage.Unmappable -> message.id
            else -> null
        }
        return serialize(BridgeMessage.Ack(id))
    }

    fun errorReply(code: ErrorCode): String = serialize(BridgeMessage.Error(code, null))
}
