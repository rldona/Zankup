package com.zankup.android.coach

import android.content.ClipData
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.ZankupTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where Claude's connectors are: there, "Add custom connector". */
private const val CLAUDE_CONNECTORS = "https://claude.ai/settings/connectors"

private val CLAUDE_STEPS = listOf(
    R.string.coach_assistants_claude_copy,
    R.string.coach_assistants_claude_open,
    R.string.coach_assistants_claude_paste,
    R.string.coach_assistants_claude_allow,
    R.string.coach_assistants_claude_chat
)

/**
 * AI assistants (OS-1162): connect Claude, or another assistant that speaks MCP, to Zankup's MCP server
 * by its address on this instance; Zankup then asks the person, signed in, to allow it. Other
 * assistants use a personal token ([onTokens]). What they can read, and how to disconnect, at the end.
 */
@Composable
fun AssistantsScreen(
    onBack: () -> Unit,
    onTokens: () -> Unit,
    modifier: Modifier = Modifier,
    model: AssistantsViewModel = hiltViewModel()
) {
    val colors = ZankupTheme.colors
    val spacing = ZankupTheme.spacing
    val clipboard = LocalClipboard.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_MS)
            copied = false
        }
    }
    Scaffold(
        modifier = modifier,
        containerColor = colors.page,
        topBar = { ZankupTopBar(stringResource(R.string.coach_assistants_title), onBack = onBack) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutter, vertical = spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Text(
                stringResource(R.string.coach_assistants_intro),
                color = colors.fg,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
            ZankupCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ClaudeMark(Modifier.size(28.dp))
                        Text(
                            "Claude",
                            Modifier.padding(start = 10.dp).semantics { heading() },
                            color = colors.fg,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    CLAUDE_STEPS.forEachIndexed { index, id ->
                        Text(
                            "${index + 1}. ${stringResource(id)}",
                            color = colors.fg,
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )
                    }
                    SelectionContainer {
                        Text(
                            model.address,
                            color = colors.fg,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surface2)
                                .padding(12.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Zankup", model.address)))
                                copied = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (copied) ZankupIcon(ZIcon.Check, contentDescription = null, size = 18.dp)
                        Text(
                            stringResource(if (copied) R.string.coach_copied else R.string.coach_assistants_copy),
                            Modifier.padding(start = if (copied) 8.dp else 0.dp)
                        )
                    }
                    Button(onClick = { uri.openUri(CLAUDE_CONNECTORS) }, modifier = Modifier.fillMaxWidth()) {
                        ZankupIcon(ZIcon.OpenExternal, contentDescription = null, size = 18.dp)
                        Text(stringResource(R.string.coach_assistants_open_claude), Modifier.padding(start = 8.dp))
                    }
                }
            }
            ZankupCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.coach_assistants_others),
                        Modifier.semantics { heading() },
                        color = colors.fg,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.coach_assistants_others_help),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted
                    )
                    OutlinedButton(onClick = onTokens, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.coach_assistants_tokens))
                    }
                }
            }
            Text(
                stringResource(R.string.coach_assistants_privacy),
                color = colors.muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(bottom = spacing.l)
            )
        }
    }
}

/** Claude's mark, in its own colour (the brand's path, 24 × 24). */
@Composable
private fun ClaudeMark(modifier: Modifier = Modifier) {
    val path = remember { PathParser().parsePathString(CLAUDE_MARK_PATH).toPath() }
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(path, CLAUDE_COLOR)
        }
    }
}

private const val COPIED_MS = 2_000L
private val CLAUDE_COLOR = Color(0xFFD97757)

private val CLAUDE_MARK_PATH =
    "m4.7144 15.9555 4.7174-2.6471.079-.2307-.079-.1275h-.2307l-.7893-.0486-2.6956-.0729-2.3375-.0971-2.2" +
        "646-.1214-.5707-.1215-.5343-.7042.0546-.3522.4797-.3218.686.0608 1.5179.1032 2.2767.1578 1.6514.0972" +
        " 2.4468.255h.3886l.0546-.1579-.1336-.0971-.1032-.0972L6.973 9.8356l-2.55-1.6879-1.3356-.9714-.7225-." +
        "4918-.3643-.4614-.1578-1.0078.6557-.7225.8803.0607.2246.0607.8925.686 1.9064 1.4754 2.4893 1.8336.36" +
        "43.3035.1457-.1032.0182-.0728-.164-.2733-1.3539-2.4467-1.445-2.4893-.6435-1.032-.17-.6194c-.0607-.25" +
        "5-.1032-.4674-.1032-.7285L6.287.1335 6.6997 0l.9957.1336.419.3642.6192 1.4147 1.0018 2.2282 1.5543 3" +
        ".0296.4553.8985.2429.8318.091.255h.1579v-.1457l.1275-1.706.2368-2.0947.2307-2.6957.0789-.7589.3764-." +
        "9107.7468-.4918.5828.2793.4797.686-.0668.4433-.2853 1.8517-.5586 2.9021-.3643 1.9429h.2125l.2429-.24" +
        "29.9835-1.3053 1.6514-2.0643.7286-.8196.85-.9046.5464-.4311h1.0321l.759 1.1293-.34 1.1657-1.0625 1.3" +
        "478-.8804 1.1414-1.2628 1.7-.7893 1.36.0729.1093.1882-.0183 2.8535-.607 1.5421-.2794 1.8396-.3157.83" +
        "18.3886.091.3946-.3278.8075-1.967.4857-2.3072.4614-3.4364.8136-.0425.0304.0486.0607 1.5482.1457.6618" +
        ".0364h1.621l3.0175.2247.7892.522.4736.6376-.079.4857-1.2142.6193-1.6393-.3886-3.825-.9107-1.3113-.32" +
        "79h-.1822v.1093l1.0929 1.0686 2.0035 1.8092 2.5075 2.3314.1275.5768-.3218.4554-.34-.0486-2.2039-1.65" +
        "75-.85-.7468-1.9246-1.621h-.1275v.17l.4432.6496 2.3436 3.5214.1214 1.0807-.17.3521-.6071.2125-.6679-" +
        ".1214-1.3721-1.9246L14.38 17.959l-1.1414-1.9428-.1397.079-.674 7.2552-.3156.3703-.7286.2793-.6071-.4" +
        "614-.3218-.7468.3218-1.4753.3886-1.9246.3157-1.53.2853-1.9004.17-.6314-.0121-.0425-.1397.0182-1.4328" +
        " 1.9672-2.1796 2.9446-1.7243 1.8456-.4128.164-.7164-.3704.0667-.6618.4008-.5889 2.386-3.0357 1.4389-" +
        "1.882.929-1.0868-.0062-.1579h-.0546l-6.3385 4.1164-1.1293.1457-.4857-.4554.0608-.7467.2307-.2429 1.9" +
        "064-1.3114Z"
