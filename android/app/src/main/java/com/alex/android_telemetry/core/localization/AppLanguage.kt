package com.alex.android_telemetry.core.localization

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

enum class AppLanguage(
    val code: String,
    val displayNameRu: String,
    val displayNameEn: String,
) {
    Russian("ru", "Русский", "Russian"),
    English("en", "Английский", "English");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return entries.firstOrNull { it.code == code } ?: Russian
        }
    }
}

object AppLanguageStore {
    private const val prefsName = "app_language_prefs"
    private const val keyLanguage = "language"

    fun get(context: Context): AppLanguage {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        return AppLanguage.fromCode(prefs.getString(keyLanguage, AppLanguage.Russian.code))
    }

    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(keyLanguage, language.code)
            .apply()
    }

    fun wrap(context: Context): Context {
        val language = get(context)
        val locale = Locale(language.code)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }
}