package me.huidoudour.QRCode.scan

import android.app.Application
import android.content.Context
import android.content.res.Configuration

class MainApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // 应用保存的语言设置
        val languageCode = LanguageManager.getCurrentLanguage(this)
        LanguageManager.setLocale(this, languageCode)
    }

    override fun attachBaseContext(base: Context?) {
        val languageCode = LanguageManager.getCurrentLanguage(base!!)
        val context = LanguageManager.setLocale(base, languageCode)
        super.attachBaseContext(context)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val languageCode = LanguageManager.getCurrentLanguage(this)
        LanguageManager.setLocale(this, languageCode)
    }
}