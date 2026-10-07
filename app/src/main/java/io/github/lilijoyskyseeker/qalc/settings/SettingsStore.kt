package io.github.lilijoyskyseeker.qalc.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val angle = stringPreferencesKey("angle")
        val approximation = stringPreferencesKey("approximation")
        val precision = intPreferencesKey("precision")
        val fractions = stringPreferencesKey("fractions")
        val autoConversion = stringPreferencesKey("autoConversion")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun update(f: (Settings) -> Settings) {
        context.dataStore.edit { prefs ->
            val s = f(prefs.toSettings())
            prefs[Keys.angle] = s.angle.name
            prefs[Keys.approximation] = s.approximation.name
            prefs[Keys.precision] = s.precision
            prefs[Keys.fractions] = s.fractions.name
            prefs[Keys.autoConversion] = s.autoConversion.name
        }
    }

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            angle = enumOr(this[Keys.angle], d.angle),
            approximation = enumOr(this[Keys.approximation], d.approximation),
            precision = this[Keys.precision] ?: d.precision,
            fractions = enumOr(this[Keys.fractions], d.fractions),
            autoConversion = enumOr(this[Keys.autoConversion], d.autoConversion),
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
