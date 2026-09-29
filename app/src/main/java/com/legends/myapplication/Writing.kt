package com.legends.myapplication

import org.json.JSONObject

class Improved(val text: String, val fromLaunchDarkly: Boolean, val error: String?)

/**
 * Sends a draft to the Tripcart writing server (server/), which runs the LaunchDarkly AgentControl config.
 * The LaunchDarkly SDK key is server-side only, so the app never talks to LaunchDarkly directly.
 */
suspend fun improveWriting(text: String): Improved {
    val body = JSONObject().put("text", text).put("userId", Store.email.ifBlank { "tripcart-android" })
    val r = http("${BuildConfig.WRITING_SERVER_URL}/improve", body.toString())
    val json = JSONObject(r.body)
    if (r.code != 200) error(json.optString("error", "HTTP ${r.code}"))
    return Improved(
        text = json.getString("improved"),
        fromLaunchDarkly = json.optString("source") == "launchdarkly",
        error = json.optString("error").ifBlank { null },
    )
}
