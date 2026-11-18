package com.zankup.android.signin

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme

/** A text field of the sign-in, with autofill's hint so password managers fill it. */
@Composable
internal fun Field(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    contentType: ContentType? = null,
    help: String? = null,
    secret: Boolean = false,
    shown: Boolean = false,
    onToggle: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        supportingText = help?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }, onGo = { onDone() }),
        visualTransformation = if (secret && !shown) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon =
        if (secret) {
            {
                IconButton(onClick = onToggle) {
                    ZankupIcon(
                        if (shown) ZIcon.Hide else ZIcon.Show,
                        contentDescription =
                        stringResource(
                            if (shown) R.string.signin_hide_password else R.string.signin_show_password
                        )
                    )
                }
            }
        } else {
            null
        },
        modifier =
        modifier
            .fillMaxWidth()
            .keepAboveKeyboard()
            .then(if (contentType != null) Modifier.semantics { this.contentType = contentType } else Modifier)
    )
}

/**
 * The code in six boxes: one hidden field takes what is typed, pasted or autofilled (SMS or the password
 * manager's one-time code), and the boxes draw it. [onComplete] runs once the sixth digit is in.
 */
@Composable
internal fun CodeBoxes(code: String, onCode: (String) -> Unit, onComplete: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ZankupTheme.colors
    val focus = remember { FocusRequester() }
    val description = stringResource(R.string.signin_code_boxes)
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(code) { if (code.length == 6) onComplete() }
    BasicTextField(
        value = TextFieldValue(code, TextRange(code.length)),
        onValueChange = { onCode(Rules.codeDigits(it.text)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onComplete() }),
        modifier =
        modifier
            .keepAboveKeyboard()
            .focusRequester(focus)
            .semantics {
                contentDescription = description
                contentType = ContentType.SmsOtpCode
            },
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(6) { index ->
                    val current = index == code.length
                    Box(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .border(
                                width = if (current) 2.dp else 1.dp,
                                color = if (current) colors.accent else colors.line,
                                shape = MaterialTheme.shapes.small
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            code.getOrNull(index)?.toString() ?: "",
                            style = MaterialTheme.typography.headlineSmall.merge(Numbers.tabular),
                            color = colors.fg
                        )
                    }
                    if (index == 2) Box(Modifier.width(4.dp))
                }
            }
        }
    )
}

/** Where a form ends (under its buttons and links), so a focused field can bring all of it above the keyboard. */
internal class FormEnd {
    var coordinates: LayoutCoordinates? = null
}

internal val LocalFormEnd = staticCompositionLocalOf<FormEnd?> { null }

/** Marks the end of the form: put it after the last button or link. */
@Composable
internal fun Modifier.formEnd(): Modifier {
    val end = LocalFormEnd.current
    return onGloballyPositioned { end?.coordinates = it }
}

/**
 * Keeps a focused field above the keyboard together with everything under it to the end of the form
 * ([formEnd]): the button that submits it and the links beside it. On a form taller than the space left,
 * the field itself wins. It runs again on every step of the keyboard's height, after that frame is laid
 * out, so the last run sees the screen already shrunk to the keyboard.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.keepAboveKeyboard(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var field by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var focused by remember { mutableStateOf(false) }
    val end = LocalFormEnd.current
    val keyboard = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, keyboard) {
        val self = field
        if (focused && keyboard > 0 && self != null && self.isAttached) {
            withFrameNanos { }
            val last = end?.coordinates?.takeIf { it.isAttached }
            val bottom = last?.let { self.localPositionOf(it, Offset(0f, it.size.height.toFloat())).y } ?: 0f
            requester.bringIntoView(Rect(0f, 0f, self.size.width.toFloat(), maxOf(self.size.height.toFloat(), bottom)))
        }
    }
    return this
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { field = it }
        .onFocusChanged { focused = it.hasFocus }
}
