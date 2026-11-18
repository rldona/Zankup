package com.zankup.android.you

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zankup.android.api.model.Me
import com.zankup.android.designsystem.components.ChipPicker
import com.zankup.android.designsystem.components.ZankupSheet
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Units
import com.zankup.android.network.ApiResult
import com.zankup.android.ui.LocalFormats
import kotlinx.coroutines.launch

/**
 * Editing who you are (OS-1160): name, location, date of birth (typed DD/MM/YYYY, sent YYYY-MM-DD),
 * sex, weight and height in the person's units. Save sends `PATCH /api/auth/me`; an empty field clears it.
 */
@Composable
internal fun EditProfileSheet(me: Me, model: YouViewModel, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val units = LocalFormats.current.units
    val imperial = units == Units.IMPERIAL
    var form by remember {
        mutableStateOf(
            ProfileForm(
                name = me.displayName.orEmpty(),
                location = me.location.orEmpty(),
                birth = me.birthDate?.let(::toDayMonthYear).orEmpty(),
                sex = me.sex?.value.orEmpty(),
                weight = me.weightKg?.let { fieldNumber(weightIn(it, units)) }.orEmpty(),
                height = me.heightCm?.let { heightIn(it, units).toString() }.orEmpty()
            )
        )
    }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidDate = stringResource(R.string.you_invalid_date)
    val invalidNumber = stringResource(R.string.you_invalid_number)
    val failed = stringResource(R.string.you_error)

    fun save() {
        when (val check = checkProfile(form, units)) {
            ProfileCheck.BadDate -> error = invalidDate
            ProfileCheck.BadNumber -> error = invalidNumber
            is ProfileCheck.Ok -> scope.launch {
                saving = true
                error = null
                when (val result = model.save(check.body)) {
                    is ApiResult.Ok -> onDone()
                    is ApiResult.Http -> error = result.detail ?: failed
                    else -> error = failed
                }
                saving = false
            }
        }
    }

    ZankupSheet(onDismiss = onDone) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.you_edit), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                form.name,
                { if (it.length <= MAX_TEXT) form = form.copy(name = it) },
                label = { Text(stringResource(R.string.you_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                form.location,
                { if (it.length <= MAX_TEXT) form = form.copy(location = it) },
                label = { Text(stringResource(R.string.you_location)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                form.birth,
                { form = form.copy(birth = typeDayMonthYear(it)) },
                label = { Text(stringResource(R.string.you_birth)) },
                placeholder = { Text(stringResource(R.string.you_date_format)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Text(stringResource(R.string.you_sex), style = MaterialTheme.typography.titleSmall)
            ChipPicker(listOf("") + SEXES, form.sex, { sexName(it) }, { form = form.copy(sex = it) })
            OutlinedTextField(
                form.weight,
                { form = form.copy(weight = it) },
                label = {
                    Text(stringResource(if (imperial) R.string.you_weight_lb else R.string.you_weight_kg))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                form.height,
                { form = form.copy(height = it.filter(Char::isDigit).take(3)) },
                label = {
                    Text(stringResource(if (imperial) R.string.you_height_in else R.string.you_height_cm))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZankupTheme.colors.muted,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = ::save, enabled = !saving, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.you_save))
                }
                OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.you_cancel))
                }
            }
        }
    }
}

private const val MAX_TEXT = 120
