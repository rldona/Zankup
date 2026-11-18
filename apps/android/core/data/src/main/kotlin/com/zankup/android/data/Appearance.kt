package com.zankup.android.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What only this phone keeps about how the app looks (Settings › General): the theme's key (see
 * `ThemeChoice` in core:designsystem; "system" by default). Read synchronously, so the first frame
 * already has the right theme.
 */
@Singleton
class Appearance
@Inject
constructor(@param:ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val _theme = MutableStateFlow(prefs.getString(THEME, null) ?: "system")

    val theme: StateFlow<String> = _theme.asStateFlow()

    fun setTheme(key: String) {
        prefs.edit().putString(THEME, key).apply()
        _theme.value = key
    }

    private companion object {
        const val THEME = "theme"
    }
}
