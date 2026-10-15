package com.example.expensify.feature.notifications

import com.example.expensify.feature.notifications.domain.AlertTarget
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Where an alert asks the app to go: a tap on a system notification (through
 * the activity's intent), on the snackbar's View button, or on a row in the
 * alerts list. The signed-in shell collects the requests and navigates; one
 * raised before the shell exists, as a cold start from a notification, waits
 * for it.
 */
@Singleton
class AlertNavigator @Inject constructor() {
    private val queue = Channel<AlertTarget>(Channel.BUFFERED)

    val requests: Flow<AlertTarget> = queue.receiveAsFlow()

    fun open(target: AlertTarget) {
        queue.trySend(target)
    }
}
