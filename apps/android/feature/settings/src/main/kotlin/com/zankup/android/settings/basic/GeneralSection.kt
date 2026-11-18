package com.zankup.android.settings.basic

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zankup.android.api.model.Preferences
import com.zankup.android.data.Appearance
import com.zankup.android.designsystem.components.ChipPicker
import com.zankup.android.designsystem.theme.Dark
import com.zankup.android.designsystem.theme.Light
import com.zankup.android.designsystem.theme.ThemeChoice
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.designsystem.theme.label
import com.zankup.android.designsystem.theme.paletteFor
import com.zankup.android.network.ApiResult
import com.zankup.android.settings.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Settings › General: the language (Android's per-app language from Android 13; kept here and applied
 * on the next opening before it), the units (saved in the account) and the theme (kept on the phone).
 */
@HiltViewModel
class GeneralModel
@Inject
constructor(
    private val app: Application,
    private val writer: SettingsWriter,
    private val appearance: Appearance
) : ViewModel() {
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _language = MutableStateFlow(readLanguage())

    val settings = writer.settings.query
    val theme: StateFlow<String> = appearance.theme
    val language: StateFlow<LanguageChoice> = _language.asStateFlow()

    init {
        viewModelScope.launch { settings.refresh() }
    }

    private fun readLanguage(): LanguageChoice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val locales = app.getSystemService(LocaleManager::class.java).applicationLocales
        if (locales.isEmpty) LanguageChoice.AUTO else LanguageChoice.fromTag(locales[0].language)
    } else {
        LanguageChoice.fromTag(prefs.getString(LANGUAGE, null))
    }

    /** Saves the choice in the account and here; from Android 13 the app switches at once. */
    fun setLanguage(choice: LanguageChoice) {
        prefs.edit { putString(LANGUAGE, choice.tag) }
        _language.value = choice
        // In the model's scope, so it survives the activity being recreated in the new language.
        viewModelScope.launch {
            writer.save(JsonObject(mapOf("language" to (choice.tag?.let(::JsonPrimitive) ?: JsonNull))))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.getSystemService(LocaleManager::class.java).applicationLocales =
                choice.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
        }
    }

    suspend fun setUnits(units: Preferences.Units): ApiResult<Preferences> =
        writer.save(JsonObject(mapOf("units" to JsonPrimitive(units.value))))

    fun setTheme(choice: ThemeChoice) = appearance.setTheme(choice.key)

    companion object {
        /** Where the language is kept for Android 12 and older (the app applies it when it opens). */
        const val PREFS = "settings"
        const val LANGUAGE = "language"
    }
}

@Composable
internal fun GeneralSection(model: GeneralModel = hiltViewModel()) {
    val state by model.settings.state.collectAsStateWithLifecycle(initialValue = null)
    val language by model.language.collectAsStateWithLifecycle()
    val theme by model.theme.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val showing = LocalConfiguration.current.locales[0].language
    SettingsBody {
        SettingsCard(title = null) {
            SettingsLabel(stringResource(R.string.set_language))
            SettingsNote(stringResource(R.string.set_language_help))
            ChipPicker(
                LanguageChoice.entries,
                language,
                {
                    stringResource(
                        when (it) {
                            LanguageChoice.AUTO -> R.string.set_language_auto
                            LanguageChoice.EN -> R.string.set_language_en
                            LanguageChoice.ES -> R.string.set_language_es
                        }
                    )
                },
                model::setLanguage
            )
            if (needsRestart(language, showing, Build.VERSION.SDK_INT)) {
                SettingsNote(stringResource(R.string.set_restart_language), live = true)
            }
            SettingsLabel(stringResource(R.string.set_units))
            val units = state?.data?.units ?: Preferences.Units.METRIC
            ChipPicker(
                Preferences.Units.entries,
                units,
                {
                    stringResource(
                        if (it == Preferences.Units.IMPERIAL) R.string.set_imperial else R.string.set_metric
                    )
                },
                { scope.launch { model.setUnits(it) } }
            )
            SettingsLabel(stringResource(R.string.set_appearance))
            SettingsNote(stringResource(R.string.set_theme_help))
            ThemePicker(ThemeChoice.fromKey(theme), model::setTheme)
        }
    }
}

private fun ThemeChoice.note(): Int? = when (this) {
    ThemeChoice.SYSTEM -> R.string.set_theme_system_note
    ThemeChoice.OLED -> R.string.set_theme_oled_note
    ThemeChoice.HIGH_CONTRAST -> R.string.set_theme_high_contrast_note
    ThemeChoice.STRAVA -> R.string.set_theme_strava_note
    ThemeChoice.STRAVA_DARK -> R.string.set_theme_strava_dark_note
    else -> null
}

/** A card per theme, two to a row, with a small picture in that theme's own colours; the chosen one ringed. */
@Composable
private fun ThemePicker(selected: ThemeChoice, onSelect: (ThemeChoice) -> Unit) {
    val colors = ZankupTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemeChoice.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { choice ->
                    val on = choice == selected
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surface,
                        border = BorderStroke(if (on) 2.dp else 1.dp, if (on) colors.accent else colors.line),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(on, role = Role.RadioButton, onClick = { onSelect(choice) })
                    ) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ThemePreview(choice)
                            Text(
                                stringResource(choice.label),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.fg
                            )
                            choice.note()?.let {
                                Text(
                                    stringResource(it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted
                                )
                            }
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A side bar, a card with two lines of text and a dot of the accent; "System" is half light, half dark. */
@Composable
private fun ThemePreview(choice: ThemeChoice) {
    val shape = RoundedCornerShape(8.dp)
    if (choice == ThemeChoice.SYSTEM) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(shape)
                .border(1.dp, Dark.line, shape)
        ) {
            drawRect(Dark.bg)
            val light = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(0f, size.height)
                close()
            }
            drawPath(light, Light.bg)
            drawLine(Dark.line, Offset(size.width, 0f), Offset(0f, size.height))
        }
        return
    }
    val p = paletteFor(choice, systemDark = false)
    val line = if (choice == ThemeChoice.HIGH_CONTRAST) 2.dp else 1.dp
    Box(
        Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(shape)
            .background(p.bg)
            .border(line, p.line, shape)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.22f)
                .background(p.surface)
                .border(line, p.line)
        )
        Row(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(0.30f))
            Column(
                Modifier
                    .weight(0.62f)
                    .padding(top = 12.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(p.surface)
                    .border(line, p.line, RoundedCornerShape(6.dp))
                    .padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .height(5.dp)
                        .alpha(0.75f)
                        .clip(CircleShape)
                        .background(p.fg)
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.45f)
                        .height(5.dp)
                        .alpha(0.6f)
                        .clip(CircleShape)
                        .background(p.muted)
                )
            }
            Spacer(Modifier.weight(0.08f))
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 8.dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(p.accent)
        )
    }
}
