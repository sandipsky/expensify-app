package com.example.expensify.feature.notifications

import android.content.Context
import com.example.expensify.R
import com.example.expensify.core.ui.AppMessage
import com.example.expensify.core.ui.AppVisibility
import com.example.expensify.core.ui.MessageAction
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.res
import com.example.expensify.feature.notifications.data.AlertStore
import com.example.expensify.feature.notifications.domain.AlertKind
import com.example.expensify.feature.notifications.domain.AlertLinks
import com.example.expensify.feature.notifications.domain.AlertTone
import com.example.expensify.feature.notifications.domain.AppAlert
import com.example.expensify.feature.notifications.system.SystemNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** An alert as the observers raise it; the titles are written into the list as it lands. */
data class AlertInput(
    /** Names what it's about: the system notification's tag, and the same id raised again replaces the older alert in the list. */
    val id: String,
    val kind: AlertKind,
    val tone: AlertTone,
    val title: UiText,
    val message: UiText,
    /** Where the alert leads, an `AlertLinks` URI. */
    val link: String,
)

/**
 * Delivers an alert (port of useNotifier.ts, req NTF-05): into the list
 * behind the bell, and as a snackbar while the app is open, or as a system
 * notification while it's in the background and the phone allows them. The
 * callers decide whether an alert is due and switched on.
 */
@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: AlertStore,
    private val system: SystemNotifier,
    private val messages: Messages,
    private val visibility: AppVisibility,
    private val navigator: AlertNavigator,
    private val clock: Clock,
) {
    suspend fun deliver(input: AlertInput) {
        val alert = AppAlert(
            id = input.id,
            kind = input.kind,
            tone = input.tone,
            title = input.title.asString(context),
            message = input.message.asString(context),
            link = input.link,
            at = clock.millis(),
            read = false,
        )
        store.add(alert)
        if (!visibility.foreground.value) {
            // Nothing to show on screen; the bell's badge says it's waiting when the phone doesn't allow notifications.
            system.post(alert)
            return
        }
        val target = AlertLinks.parse(alert.link)
        messages.show(
            AppMessage(
                text = res(R.string.alert_snackbar, alert.title, alert.message),
                long = true,
                action = target?.let { MessageAction(res(R.string.alert_view)) { navigator.open(it) } },
            ),
        )
    }
}
