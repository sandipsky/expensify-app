package com.example.expensify

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ExpensifyApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // WorkManager's default initializer is removed in the manifest, so its
    // workers can be built by Hilt (the recurring catch-up job).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Without google-services.json there is no default FirebaseApp. Debug
        // builds point at the emulators, which take any values for a demo-
        // project (the same one ../expensify's .env.example uses). A real
        // project registers the app in the console and ships its json instead.
        if (BuildConfig.USE_EMULATORS && FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(
                this,
                FirebaseOptions.Builder()
                    .setProjectId(EMULATOR_PROJECT_ID)
                    .setApplicationId("1:000000000000:android:0000000000000000")
                    .setApiKey("demo-api-key")
                    .build(),
            )
        }
    }

    companion object {
        const val EMULATOR_PROJECT_ID = "demo-expensify"
    }
}
