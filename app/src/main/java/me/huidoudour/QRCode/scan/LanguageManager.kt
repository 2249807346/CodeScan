package me.huidoudour.QRCode.scan

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.*

class LanguageManager {
    companion object {
        fun setLocale(context: Context, languageCode: String): Context {
            return if (languageCode.isEmpty()) {
                // 使用系统默认语言
                updateResources(context, Locale.getDefault())
            } else {
                val locale = Locale(languageCode)
                updateResources(context, locale)
            }
        }

        private fun updateResources(context: Context, locale: Locale): Context {
            Locale.setDefault(locale)

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                updateResourcesLocale(context, locale)
            } else {
                updateResourcesLocaleLegacy(context, locale)
            }
        }

        @Suppress("DEPRECATION")
        private fun updateResourcesLocaleLegacy(context: Context, locale: Locale): Context {
            val resources = context.resources
            val configuration = resources.configuration
            configuration.locale = locale
            resources.updateConfiguration(configuration, resources.displayMetrics)
            return context
        }

        private fun updateResourcesLocale(context: Context, locale: Locale): Context {
            val configuration = context.resources.configuration
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            configuration.setLocales(localeList)
            return context.createConfigurationContext(configuration)
        }

        fun getCurrentLanguage(context: Context): String {
            val sharedPref = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            return sharedPref.getString("language_preference", "") ?: ""
        }
    }
}