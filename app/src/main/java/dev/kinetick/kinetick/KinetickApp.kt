package dev.kinetick.kinetick

import android.app.Application
import dev.kinetick.kinetick.data.SettingsStore

class KinetickApp : Application() {
    val settings: SettingsStore by lazy { SettingsStore(this) }
}
