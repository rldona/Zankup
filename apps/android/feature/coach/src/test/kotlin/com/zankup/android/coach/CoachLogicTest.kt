package com.zankup.android.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachLogicTest {
    @Test
    fun linesAreEvents() {
        assertEquals(CoachEvent.Text("Hi"), parseEvent("""{"type":"text","text":"Hi"}"""))
        assertEquals(
            CoachEvent.Tool("records", "running"),
            parseEvent("""{"type":"tool","name":"records","status":"running"}""")
        )
        assertEquals(CoachEvent.Done(listOf("records")), parseEvent("""{"type":"done","tools":["records"]}"""))
        assertEquals(
            CoachEvent.Error("spent", "budget"),
            parseEvent("""{"type":"error","message":"spent","code":"budget"}""")
        )
        assertNull(parseEvent("""{"type":"new_kind"}"""))
        assertNull(parseEvent("""{"type":"te"""))
    }

    @Test
    fun eventsWriteTheAnswer() {
        var chat = Chat().ask("How am I doing?")
        assertEquals(listOf(Speaker.USER, Speaker.ASSISTANT), chat.turns.map { it.role })
        assertEquals(Working.Thinking, chat.working)
        assertTrue(chat.busy)
        chat = chat.apply(CoachEvent.Tool("records", "running"))
        assertEquals(Working.Tool("records"), chat.working)
        chat = chat.apply(CoachEvent.Tool("records", "done"))
        assertNull(chat.working)
        chat = chat.apply(CoachEvent.Text("Fine, ")).apply(CoachEvent.Text("thanks."))
        assertEquals("Fine, thanks.", chat.turns.last().content)
        chat = chat.apply(CoachEvent.Done(emptyList())).finish()
        assertFalse(chat.busy)
        assertEquals(2, chat.history().size)
    }

    @Test
    fun errorsMarkTheAnswer() {
        val budget = Chat().ask("?").apply(CoachEvent.Error("x", "budget"))
        assertEquals(TurnError.BUDGET, budget.turns.last().error)
        val failed = Chat().ask("?").fail()
        assertEquals(TurnError.FAILED, failed.turns.last().error)
        // An answer that never came is not sent back.
        assertEquals(listOf("?"), failed.history().map { it.content })
    }

    @Test
    fun picksOutLinksToActivities() {
        assertEquals(
            listOf(Piece.Text("Your best 10K: "), Piece.Activity(42), Piece.Text(".")),
            pieces("Your **best** 10K: /activities/42.")
        )
        assertEquals(
            listOf(Piece.Text("Best ("), Piece.Activity(155), Piece.Text(").")),
            pieces("Best ([Actividad 155](/activities/155)).")
        )
        assertEquals(listOf(Piece.Activity(7)), pieces("/activities/7"))
    }

    @Test
    fun bulletLinesAreAList() {
        assertEquals(
            listOf(Block.Paragraph("Two things:"), Block.Bullets(listOf("rest", "sleep"))),
            blocks("Two things:\n\n- rest\n- sleep")
        )
        assertEquals(listOf(Block.Paragraph("a\n- b")), blocks("a\n- b"))
    }

    @Test
    fun theMcpAddress() {
        assertEquals("https://api.zankup.com/api/mcp", mcpAddress("https://api.zankup.com/"))
        assertEquals("https://x.up.railway.app/api/mcp", mcpAddress("https://x.up.railway.app"))
    }
}
