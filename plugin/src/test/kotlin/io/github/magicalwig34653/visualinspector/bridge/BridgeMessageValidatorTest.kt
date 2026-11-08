package io.github.magicalwig34653.visualinspector.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeMessageValidatorTest {
    private fun valid(raw: String?) = (BridgeMessageValidator.validate(raw) as ValidationResult.Valid).message
    private fun rejected(raw: String?) = assertTrue(raw, BridgeMessageValidator.validate(raw) is ValidationResult.Rejected)

    @Test fun acceptsPingAndHello() {
        assertEquals(BridgeMessage.Ping("a"), valid("""{"v":1,"type":"ping","id":"a"}"""))
        assertEquals(BridgeMessage.Hello(null), valid("""{"v":1,"type":"hello"}"""))
    }

    @Test fun rejectsBadVersion() {
        rejected("""{"type":"ping"}""")
        rejected("""{"v":2,"type":"ping"}""")
        rejected("""{"v":"1","type":"ping"}""")
        rejected("""{"v":1.5,"type":"ping"}""")
    }

    @Test fun rejectsUnknownTypeFieldsAndBadTypes() {
        rejected("""{"v":1,"type":"openFile"}""")
        rejected("""{"v":1,"type":"ping","path":"/etc/passwd"}""")
        rejected("""{"v":1,"type":5}""")
        rejected("""{"v":1,"type":"ping","id":5}""")
        rejected("""{"v":1,"type":"ping","id":"${"x".repeat(65)}"}""")
    }

    @Test fun rejectsMalformedAndNonObjects() {
        for (raw in listOf(null, "", "ping", "[]", "42", "null", "{", """{"v":1,"type":"ping"} x""", "\"s\"")) rejected(raw)
    }

    @Test fun rejectsOversizedInput() {
        val big = """{"v":1,"type":"ping","id":"a"}""" + " ".repeat(BridgeMessageValidator.MAX_MESSAGE_CHARS)
        rejected(big)
    }

    @Test fun replyEscapesIdentifier() {
        val reply = BridgeMessageValidator.reply(BridgeMessage.Ping("\"},\"x\":{"))
        val parsed = com.google.gson.JsonParser.parseString(reply).asJsonObject
        assertEquals(setOf("v", "type", "id"), parsed.keySet())
        assertEquals("pong", parsed.get("type").asString)
    }
}
