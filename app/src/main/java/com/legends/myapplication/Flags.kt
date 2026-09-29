package com.legends.myapplication

import ai.luciq.library.Luciq
import ai.luciq.library.featuresflags.model.LuciqFeatureFlag
import android.app.Application
import android.content.Context
import androidx.core.content.edit
import com.launchdarkly.sdk.ContextKind
import com.launchdarkly.sdk.LDContext
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.LDConfig
import java.util.UUID

/** LaunchDarkly boolean flag: on = single-page checkout (variant B), off = three-step checkout (variant A). */
const val VARIANT_B_FLAG = "variant-b" // LaunchDarkly keys can't contain spaces

/** Name the flag shows under in Luciq reports. */
const val VARIANT_B_LUCIQ_NAME = "variant B"

object Flags {
    fun init(app: Application) {
        // Mobile key (mob-...) from local.properties; without it the Settings screen toggle stays in charge.
        if (BuildConfig.LAUNCHDARKLY_MOBILE_KEY.isEmpty()) return
        val config = LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
            .mobileKey(BuildConfig.LAUNCHDARKLY_MOBILE_KEY)
            .build()
        val context = LDContext.builder(ContextKind.DEFAULT, deviceKey(app)).anonymous(true).build()
        // Non-blocking: flags start from the last cached values and the listener picks up fresh ones.
        LDClient.init(app, config, context)
        applyVariantB()
        LDClient.get().registerFeatureFlagListener(VARIANT_B_FLAG) { applyVariantB() }
    }

    private fun applyVariantB() {
        val enabled = LDClient.get().boolVariation(VARIANT_B_FLAG, false)
        // Tag Luciq reports (crashes included) with the flag so they can be filtered by it.
        if (enabled) Luciq.addFeatureFlag(LuciqFeatureFlag(VARIANT_B_LUCIQ_NAME))
        else Luciq.removeFeatureFlag(VARIANT_B_LUCIQ_NAME)
        Store.setVariant(if (enabled) "B" else "A")
    }

    /** Stable anonymous key so the same device keeps the same flag targeting across launches. */
    private fun deviceKey(context: Context): String {
        val prefs = context.getSharedPreferences("flags", Context.MODE_PRIVATE)
        return prefs.getString("ld_context_key", null)
            ?: UUID.randomUUID().toString().also { prefs.edit { putString("ld_context_key", it) } }
    }
}
