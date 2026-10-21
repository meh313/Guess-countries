package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.model.CountryCatalog
import com.example.data.settings.AppLanguage
import com.example.data.settings.PrefsUserSettings
import com.example.data.settings.goalHistory
import java.util.TimeZone
import kotlinx.coroutines.flow.first

/**
 * The home-screen widget: how many reviews are due, the streak and today's items against the goal, and a Practise
 * button that opens the Quiz tab. Glance renders it from [WidgetLines], today's [WidgetState] in the words of the
 * language the screens use. The app refreshes it ([refresh], through [WidgetRefresher]) after a quiz or study session
 * is saved, after a reset or an import of progress and when the daily goal or the language changes (once more when a
 * new language reaches the screens, which from Android 13 is a moment after the change), and the reminder's daily run
 * refreshes it too, so the due count follows the day.
 */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = lines(context)
        provideContent {
            // Glance keeps a session open for a while after it draws (45 s), and an update that reaches the open
            // session only hands it the widget's state again, without calling provideGlance, so lines worked out once
            // above would stay on screen. [refresh] counts each redraw in that state: every new count works out the
            // lines again here (the first composition does too), from the numbers and the language of that moment.
            val shown by produceState(first, currentState(REFRESHES)) { value = lines(context) }
            GlanceTheme { Content(shown) }
        }
    }

    companion object {
        /** Today's numbers from the database and the settings, off the main thread (Glance calls this in a worker). */
        suspend fun load(context: Context, now: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): WidgetState {
            val dao = AppDatabase.getDatabase(context).userProgressDao()
            val skills = dao.getAllSkills().first()
            val sessions = dao.practiceSessions().map { it.toPracticeSession() }
            val sovereign = CountryCatalog.all.filter { it.isSovereign }.mapTo(HashSet()) { it.code }
            return WidgetState.of(skills, sessions, sovereign, PrefsUserSettings(context).goalHistory, now, zone)
        }

        /** [load]'s numbers in the language the screens use (before Android 13 the widget's context is the phone's). */
        private suspend fun lines(context: Context): WidgetLines =
            WidgetLines.of(load(context), AppLanguage.wrap(context, PrefsUserSettings(context).language))

        /**
         * Redraws every instance of the widget; nothing happens when none is placed. Each one's count of redraws goes
         * up first, so a session Glance still holds from the last one draws the widget anew too ([provideGlance]).
         */
        suspend fun refresh(context: Context) {
            val widget = TodayWidget()
            GlanceAppWidgetManager(context).getGlanceIds(TodayWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { it[REFRESHES] = (it[REFRESHES] ?: 0) + 1 }
                widget.update(context, id)
            }
        }

        /** How many times [refresh] has redrawn a widget, kept in that widget's Glance state. */
        private val REFRESHES = intPreferencesKey("refreshes")
    }
}

/**
 * What the widget says: [WidgetState]'s numbers as its lines, in the language of the context they were read from.
 * Plain strings, compared as such: a state and a context would look unchanged after a change of language (from Android
 * 13 the app's context takes on the new one in place), and the widget would not be drawn again.
 */
internal data class WidgetLines(val title: String, val due: String, val progress: String, val practise: String) {
    companion object {
        fun of(state: WidgetState, texts: Context) = WidgetLines(
            title = texts.getString(R.string.app_name),
            due = if (state.due == 0) texts.getString(R.string.widget_nothing_due)
            else texts.resources.getQuantityString(R.plurals.widget_due, state.due, state.due),
            progress = texts.getString(R.string.widget_streak_today, state.streak, state.today, state.goal),
            practise = texts.getString(R.string.widget_practise)
        )
    }
}

/** The widget's receiver, declared in the manifest. */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

@Composable
internal fun Content(lines: WidgetLines) {
    val context = LocalContext.current
    val open = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val practise = Intent(open).putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_QUIZ)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(actionStartActivity(open)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_flag),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                modifier = GlanceModifier.size(18.dp)
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = lines.title,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)
            )
        }
        Spacer(modifier = GlanceModifier.height(6.dp))
        Text(
            text = lines.due,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        )
        Text(
            text = lines.progress,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp)
        )
        Spacer(modifier = GlanceModifier.height(10.dp))
        Text(
            text = lines.practise,
            style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier
                .background(GlanceTheme.colors.primary)
                .cornerRadius(16.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable(actionStartActivity(practise))
        )
    }
}

/** Something that redraws the widget; the app's does it through Glance, tests pass one that does nothing. */
fun interface WidgetRefresher {
    suspend fun refresh()

    companion object {
        val NONE = WidgetRefresher { }

        fun glance(context: Context) = WidgetRefresher { TodayWidget.refresh(context.applicationContext) }
    }
}
