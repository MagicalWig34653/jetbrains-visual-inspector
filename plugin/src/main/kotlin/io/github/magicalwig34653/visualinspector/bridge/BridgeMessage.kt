package io.github.magicalwig34653.visualinspector.bridge

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Round-trip proof only, not protocol v1 (backlog item 4). Page input is untrusted: closed set of keys and
 * types, size limit before parsing, everything else is dropped (ADR 0005).
 */
sealed interface BridgeMessage {
    data class Ping(val id: String?) : BridgeMessage
    data class Hello(val id: String?) : BridgeMessage
}

sealed interface ValidationResult {
    data class Valid(val message: BridgeMessage) : ValidationResult
    data class Rejected(val reason: String) : ValidationResult
}

object BridgeMessageValidator {
    const val VERSION = 1
    const val MAX_MESSAGE_CHARS = 2048
    const val MAX_ID_CHARS = 64
    private val allowedKeys = setOf("v", "type", "id")

    fun validate(raw: String?): ValidationResult {
        if (raw == null) return ValidationResult.Rejected("null")
        if (raw.length > MAX_MESSAGE_CHARS) return ValidationResult.Rejected("too large")
        val obj: JsonObject = try {
            JsonParser.parseString(raw).takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: RuntimeException) {
            null
        } ?: return ValidationResult.Rejected("not a JSON object")

        if (!allowedKeys.containsAll(obj.keySet())) return ValidationResult.Rejected("unknown field")

        val v = obj.get("v")
        if (v == null || !v.isJsonPrimitive || !v.asJsonPrimitive.isNumber ||
            v.asString != VERSION.toString()
        ) return ValidationResult.Rejected("unsupported version")

        val type = obj.get("type")
        if (type == null || !type.isJsonPrimitive || !type.asJsonPrimitive.isString) {
            return ValidationResult.Rejected("missing type")
        }

        val idElement = obj.get("id")
        val id: String? = when {
            idElement == null -> null
            idElement.isJsonPrimitive && idElement.asJsonPrimitive.isString &&
                idElement.asString.length <= MAX_ID_CHARS -> idElement.asString
            else -> return ValidationResult.Rejected("invalid id")
        }

        return when (type.asString) {
            "ping" -> ValidationResult.Valid(BridgeMessage.Ping(id))
            "hello" -> ValidationResult.Valid(BridgeMessage.Hello(id))
            else -> ValidationResult.Rejected("unknown type")
        }
    }

    /** Reply for a valid message. Serialized with Gson so ids cannot break out of the JSON. */
    fun reply(message: BridgeMessage): String {
        val (type, id) = when (message) {
            is BridgeMessage.Ping -> "pong" to message.id
            is BridgeMessage.Hello -> "hello-ack" to message.id
        }
        val o = JsonObject()
        o.addProperty("v", VERSION)
        o.addProperty("type", type)
        if (id != null) o.addProperty("id", id)
        return o.toString()
    }
}
