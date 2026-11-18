package com.zankup.android.signin

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
