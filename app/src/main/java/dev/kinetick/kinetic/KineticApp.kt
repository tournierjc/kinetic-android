package dev.kinetick.kinetic

import android.app.Application
import dev.kinetick.kinetic.data.SettingsStore

class KineticApp : Application() {
    val settings: SettingsStore by lazy { SettingsStore(this) }
}
