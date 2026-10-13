package com.example.expensify.core.date

import com.example.expensify.core.firebase.ApplicationScope
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The device's "today" (req BR-05), as one StateFlow everything that cares
 * about dates observes. It is re-read every minute and whenever the app
 * resumes, so periods and "Upcoming" chips roll over at midnight.
 */
@Singleton
class Today @Inject constructor(
    @ApplicationScope scope: CoroutineScope,
    private val clock: Clock,
) {
    private val state = MutableStateFlow(LocalDate.now(clock))

    val date: StateFlow<LocalDate> = state

    init {
        scope.launch {
            while (true) {
                delay(60_000)
                refresh()
            }
        }
    }

    fun refresh() {
        state.update { LocalDate.now(clock) }
    }
}
