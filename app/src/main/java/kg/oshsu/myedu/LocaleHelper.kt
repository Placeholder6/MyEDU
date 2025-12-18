package kg.oshsu.myedu

import android.content.Context
import android.content.ContextWrapper
import java.util.Locale

object LocaleHelper {
    fun setLocale(context: Context, languageCode: String): ContextWrapper {
        val locale = Locale.forLanguageTag(languageCode) // Replaced deprecated constructor
        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        val newContext = context.createConfigurationContext(config)
        return ContextWrapper(newContext)
    }
}