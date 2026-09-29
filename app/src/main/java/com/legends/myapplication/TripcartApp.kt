package com.legends.myapplication

import ai.luciq.library.Luciq
import ai.luciq.library.MaskingType
import ai.luciq.library.invocation.LuciqInvocationEvent
import android.app.Application

class TripcartApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Luciq first, so it sees everything after it. Token comes from local.properties.
        if (BuildConfig.LUCIQ_APP_TOKEN.isNotEmpty()) {
            Luciq.Builder(this, BuildConfig.LUCIQ_APP_TOKEN)
                .setInvocationEvents(LuciqInvocationEvent.SHAKE, LuciqInvocationEvent.FLOATING_BUTTON)
                .build()
            Luciq.setAutoMaskScreenshotsTypes(MaskingType.TEXT_INPUTS, MaskingType.LABELS, MaskingType.MEDIA)
            // The SDK masks api_key/apikey by default, but not the x-api-key header Ask AI sends.
            Luciq.setNetworkLogListener { log ->
                log.requestHeaders = log.requestHeaders?.mapValues { (k, v) -> if (k.equals("x-api-key", ignoreCase = true)) "*****" else v }?.toMutableMap()
                log
            }
        }
        Store.init(this)
    }
}
