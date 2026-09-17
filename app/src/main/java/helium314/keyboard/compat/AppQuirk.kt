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
) {
    fun hasCustomSettings(): Boolean =
        forceWebEditor || stripNoEnterAction || (forceEnterAction != null) || forceIncognito

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("packageName", packageName)
        if (forceWebEditor) json.put("forceWebEditor", true)
        if (stripNoEnterAction) json.put("stripNoEnterAction", true)
        if (forceEnterAction != null) json.put("forceEnterAction", forceEnterAction)
        if (forceIncognito) json.put("forceIncognito", true)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): AppQuirk {
            val packageName = json.optString("packageName", "")
            val forceWebEditor = json.optBoolean("forceWebEditor", false)
            val stripNoEnterAction = json.optBoolean("stripNoEnterAction", false)
            val forceEnterAction = if (json.has("forceEnterAction")) json.getInt("forceEnterAction") else null
            val forceIncognito = json.optBoolean("forceIncognito", false)
            return AppQuirk(
                packageName = packageName,
                forceWebEditor = forceWebEditor,
                stripNoEnterAction = stripNoEnterAction,
                forceEnterAction = forceEnterAction,
                forceIncognito = forceIncognito,
            )
        }
    }
}
