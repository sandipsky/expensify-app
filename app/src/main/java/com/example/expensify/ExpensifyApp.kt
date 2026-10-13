package com.example.expensify

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ExpensifyApp : Application() {
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
