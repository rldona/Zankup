package com.zankup.android.coach

import com.zankup.android.network.ZankupJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The Coach's stream (OS-1162), as the React Native app reads it: `POST /api/ai/coach` answers with one
 * JSON event per line. Pure, so it is tested without a phone.
 */
sealed interface CoachEvent {
    data class Text(val text: String) : CoachEvent

    /** A tool the coach uses to read the person's data: `running`, `done` or `error`. */
    data class Tool(val name: String, val status: String) : CoachEvent

    data class Done(val tools: List<String>) : CoachEvent

    data class Error(val message: String, val code: String?) : CoachEvent
}

/** One NDJSON line as an event; null for a line that is not one (or of a kind this app does not know). */
fun parseEvent(line: String): CoachEvent? {
    val json = runCatching { ZankupJson.parseToJsonElement(line) as? JsonObject }.getOrNull() ?: return null
    fun field(name: String) = (json[name] as? JsonPrimitive)?.contentOrNull
    return when (field("type")) {
        "text" -> CoachEvent.Text(field("text").orEmpty())
        "tool" -> CoachEvent.Tool(field("name").orEmpty(), field("status").orEmpty())
        "done" -> CoachEvent.Done(
            (json["tools"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        )
        "error" -> CoachEvent.Error(field("message").orEmpty(), field("code"))
        else -> null
    }
}

enum class Speaker { USER, ASSISTANT }

/** Why an answer stopped: the coach failed, or this month's budget is spent. */
enum class TurnError { FAILED, BUDGET }

data class Turn(val role: Speaker, val content: String, val error: TurnError? = null)

/** What the chat shows under the turns while it answers. */
sealed interface Working {
    data object Thinking : Working

    /** A tool runs; its [name] picks the label ("Checking records"). */
    data class Tool(val name: String) : Working
}

/** The conversation: it lives in the screen only. */
data class Chat(val turns: List<Turn> = emptyList(), val working: Working? = null, val busy: Boolean = false) {
    /** What is sent: every turn so far with something said. */
    fun history(): List<Turn> = turns.filter { it.content.isNotBlank() }

    /** A question asked: it, an empty answer to fill, and "Thinking…". */
    fun ask(question: String) = copy(
        turns = turns + Turn(Speaker.USER, question) + Turn(Speaker.ASSISTANT, ""),
        working = Working.Thinking,
        busy = true
    )

    fun apply(event: CoachEvent): Chat = when (event) {
        is CoachEvent.Text -> patch { it.copy(content = it.content + event.text) }.copy(working = null)
        is CoachEvent.Tool -> copy(working = if (event.status == "running") Working.Tool(event.name) else null)
        is CoachEvent.Error -> patch {
            it.copy(error = if (event.code == "budget") TurnError.BUDGET else TurnError.FAILED)
        }
        is CoachEvent.Done -> this
    }

    fun fail() = patch { it.copy(error = TurnError.FAILED) }

    fun finish() = copy(working = null, busy = false)

    /** Changes the answer being written, the last turn. */
    private fun patch(change: (Turn) -> Turn): Chat {
        val last = turns.lastOrNull()?.takeIf { it.role == Speaker.ASSISTANT } ?: return this
        return copy(turns = turns.dropLast(1) + change(last))
    }
}

/** A piece of an answer: plain text, or a link to an activity. */
sealed interface Piece {
    data class Text(val text: String) : Piece

    data class Activity(val id: Long) : Piece
}

private val MARKDOWN_LINK = Regex("\\[[^\\]\\n]*]\\((/activities/\\d+)\\)")
private val ACTIVITY_LINK = Regex("/activities/(\\d+)")

/**
 * An answer's text with its links to activities picked out, and no bold marks. A link comes bare
 * ("/activities/123") or in Markdown ("[Activity 123](/activities/123)"): both become one link.
 */
fun pieces(text: String): List<Piece> {
    val plain = MARKDOWN_LINK.replace(text, "$1")
    val out = mutableListOf<Piece>()
    var at = 0
    for (match in ACTIVITY_LINK.findAll(plain)) {
        if (match.range.first > at) out += Piece.Text(plain.substring(at, match.range.first).replace("**", ""))
        val id = match.groupValues[1].toLongOrNull()
        out += if (id != null) Piece.Activity(id) else Piece.Text(match.value)
        at = match.range.last + 1
    }
    if (at < plain.length) out += Piece.Text(plain.substring(at).replace("**", ""))
    return out
}

sealed interface Block {
    data class Paragraph(val text: String) : Block

    data class Bullets(val items: List<String>) : Block
}

private val BULLET = Regex("^\\s*[-•*]\\s+")

/** Paragraphs and simple bullet lists, as the Coach writes them. */
fun blocks(text: String): List<Block> = text
    .split(Regex("\n{2,}"))
    .filter { it.isNotBlank() }
    .map { block ->
        val lines = block.split("\n")
        if (lines.all { BULLET.containsMatchIn(it) }) {
            Block.Bullets(lines.map { it.replace(BULLET, "") })
        } else {
            Block.Paragraph(block)
        }
    }

/** Zankup's MCP address on this instance: the API's base with `/api/mcp`. */
fun mcpAddress(baseUrl: String?): String = "${baseUrl.orEmpty().trimEnd('/')}/api/mcp"
