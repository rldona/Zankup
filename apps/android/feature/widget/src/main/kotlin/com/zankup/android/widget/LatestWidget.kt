package com.zankup.android.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.zankup.android.ui.sportName
import com.zankup.android.ui.sportOf

// The React Native widget's colours: the dark and light page, text and muted text, the brand green.
private val bg = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF111316))
private val fg = ColorProvider(day = Color(0xFF16181D), night = Color(0xFFEEF0F3))
private val muted = ColorProvider(day = Color(0xFF646A75), night = Color(0xFF9AA1AD))
private val accent = ColorProvider(day = Color(0xFF20C997), night = Color(0xFF20C997))

/**
 * The home-screen widget (OS-1170): the latest activity and today's form, drawn from the snapshot kept
 * on the phone. A tap opens the activity (or the app, before there is one).
 */
class LatestWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetStore(context).read()
        provideContent { Latest(context, snapshot) }
    }

    @Composable
    private fun Latest(context: Context, s: Snapshot) {
        // The app's own launch intent with the web path it opens, as a notification carries it.
        val open = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
            .apply { s.activityId?.let { putExtra(EXTRA_URL, "/activities/$it") } }
        val title = s.title ?: s.sport?.let { context.getString(sportName(sportOf(it))) } ?: "Zankup"
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(bg)
                .cornerRadius(20.dp)
                .padding(14.dp)
                .clickable(actionStartActivity(open))
        ) {
            Text(title, maxLines = 1, style = TextStyle(color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold))
            if (s.subtitle.isNotEmpty()) {
                Text(s.subtitle, maxLines = 1, style = TextStyle(color = muted, fontSize = 12.sp))
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(
                s.figures,
                maxLines = 1,
                style = TextStyle(color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            )
            Spacer(GlanceModifier.defaultWeight())
            if (s.form.isNotEmpty()) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        context.getString(R.string.widget_form),
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = muted, fontSize = 12.sp)
                    )
                    Text(s.form, style = TextStyle(color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

/** The extra with a web path the app opens (Push.EXTRA_URL in :app). */
internal const val EXTRA_URL = "url"

class LatestWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LatestWidget()
}
