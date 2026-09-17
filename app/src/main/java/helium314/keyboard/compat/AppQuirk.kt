// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.compat

import org.json.JSONObject

/**
 * Data model representing per-application compatibility quirks and overrides.
 */
data class AppQuirk(
    val packageName: String,
    val forceWebEditor: Boolean = false,
    val stripNoEnterAction: Boolean = false,
    val forceEnterAction: Int? = null,
    val forceIncognito: Boolean = false,
    val forceDirectCommit: Boolean = false,
    val disableAutoSpace: Boolean = false,
    val allowTypeNullKeyboard: Boolean = false,
    val autoCorrectionMode: Int? = null,
) {
    fun hasCustomSettings(): Boolean =
        forceWebEditor || stripNoEnterAction || (forceEnterAction != null) || forceIncognito ||
                forceDirectCommit || disableAutoSpace || allowTypeNullKeyboard || (autoCorrectionMode != null)

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("packageName", packageName)
        if (forceWebEditor) json.put("forceWebEditor", true)
        if (stripNoEnterAction) json.put("stripNoEnterAction", true)
        if (forceEnterAction != null) json.put("forceEnterAction", forceEnterAction)
        if (forceIncognito) json.put("forceIncognito", true)
        if (forceDirectCommit) json.put("forceDirectCommit", true)
        if (disableAutoSpace) json.put("disableAutoSpace", true)
        if (allowTypeNullKeyboard) json.put("allowTypeNullKeyboard", true)
        if (autoCorrectionMode != null) json.put("autoCorrectionMode", autoCorrectionMode)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): AppQuirk {
            val packageName = json.optString("packageName", "")
            val forceWebEditor = json.optBoolean("forceWebEditor", false)
            val stripNoEnterAction = json.optBoolean("stripNoEnterAction", false)
            val forceEnterAction = if (json.has("forceEnterAction")) json.getInt("forceEnterAction") else null
            val forceIncognito = json.optBoolean("forceIncognito", false)
            val forceDirectCommit = json.optBoolean("forceDirectCommit", false)
            val disableAutoSpace = json.optBoolean("disableAutoSpace", false)
            val allowTypeNullKeyboard = json.optBoolean("allowTypeNullKeyboard", false)
            val autoCorrectionMode = if (json.has("autoCorrectionMode")) json.getInt("autoCorrectionMode") else null
            return AppQuirk(
                packageName = packageName,
                forceWebEditor = forceWebEditor,
                stripNoEnterAction = stripNoEnterAction,
                forceEnterAction = forceEnterAction,
                forceIncognito = forceIncognito,
                forceDirectCommit = forceDirectCommit,
                disableAutoSpace = disableAutoSpace,
                allowTypeNullKeyboard = allowTypeNullKeyboard,
                autoCorrectionMode = autoCorrectionMode,
            )
        }
    }
}
