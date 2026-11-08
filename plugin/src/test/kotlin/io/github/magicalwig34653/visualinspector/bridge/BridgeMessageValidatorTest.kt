package io.github.magicalwig34653.visualinspector.bridge

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BridgeMessageValidatorTest {
    private fun code(raw: String?) = (BridgeMessageValidator.validate(raw) as? ValidationResult.Rejected)?.code

    @Test fun sharedVectors() {
        val file = File(System.getProperty("visualinspector.protocolVectors") ?: error("system property not set"))
        val vectors = JsonParser.parseString(file.readText()).asJsonObject.getAsJsonArray("vectors")
        assertEquals(202, vectors.size())
        val failures = mutableListOf<String>()
        for (e in vectors) {
            val v = e.asJsonObject
            val raw = v.get("raw").asString
            val result = if (v.get("direction").asString == "page") BridgeMessageValidator.validate(raw)
            else BridgeMessageValidator.validateIde(raw)
            val expected = if (v.get("valid").asBoolean) null else v.get("code").asString
            val actual = (result as? ValidationResult.Rejected)?.code?.wire
            if (expected != actual) failures += "${v.get("name").asString}: expected $expected, got $actual"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun duplicateKeysAreRejected() {
        assertEquals(ErrorCode.BAD_JSON, code("""{"v":1,"type":"hello","id":"a","id":"b"}"""))
        assertEquals(ErrorCode.BAD_JSON, code("""{"v":1,"v":1,"type":"hello"}"""))
    }

    @Test fun integersMustBeIntegerLiterals() {
        assertEquals(ErrorCode.BAD_VERSION, code("""{"v":1.0,"type":"hello"}"""))
        assertEquals(ErrorCode.BAD_VERSION, code("""{"v":1e0,"type":"hello"}"""))
        assertEquals(ErrorCode.BAD_FIELD, code("""{"v":1,"type":"navigate","path":"a.html","offset":10.0}"""))
        assertEquals(ErrorCode.BAD_FIELD, code("""{"v":1,"type":"navigate","path":"a.html","offset":-0}"""))
    }

    @Test fun parsesFieldsAndSerializesReplies() {
        val m = BridgeMessageValidator.validate(
            """{"v":1,"type":"unmappable","tag":"td","ancestor":{"path":"a.html","offset":3,"label":"tr"},"id":"x"}""",
        ) as ValidationResult.Valid
        assertEquals(BridgeMessage.Unmappable("td", null, Ancestor("a.html", 3, "tr"), "x"), m.message)
        assertEquals("""{"v":1,"type":"ack","ref":"x"}""", BridgeMessageValidator.ackFor(m.message))
        assertEquals("""{"v":1,"type":"error","code":"denied"}""", BridgeMessageValidator.serialize(BridgeMessage.Error(ErrorCode.DENIED, null)))
    }

    @Test fun ownRepliesPassIdeValidation() {
        val replies = listOf(
            BridgeMessage.Ack("a"), BridgeMessage.Clear(null), BridgeMessage.Highlight("a/b.html", 5, 1, "h"),
            BridgeMessage.Error(ErrorCode.NOT_FOUND, "n"),
        )
        for (r in replies) {
            val v = BridgeMessageValidator.validateIde(BridgeMessageValidator.serialize(r)) as ValidationResult.Valid
            assertEquals(r, v.message)
        }
    }
}
