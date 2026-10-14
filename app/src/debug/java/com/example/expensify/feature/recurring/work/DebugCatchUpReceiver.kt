package com.example.expensify.feature.recurring.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Debug builds only: runs the recurring catch-up worker once, without
 * opening the app, so the closed-app path can be checked from a terminal:
 *
 *     adb shell am broadcast -n com.example.expensify/.feature.recurring.work.DebugCatchUpReceiver
 *
 * The periodic job only runs in its daily window, which is too slow for a QA check.
 */
@AndroidEntryPoint
class DebugCatchUpReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduler: RecurringWorkScheduler

    override fun onReceive(context: Context, intent: Intent) {
        scheduler.runNow()
    }
}
