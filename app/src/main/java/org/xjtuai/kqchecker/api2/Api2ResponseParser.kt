package org.xjtuai.kqchecker.api2

import org.json.JSONArray
import org.json.JSONObject

object Api2ResponseParser {
    fun parseResponse(responseText: String): JSONObject? {
        return try {
            JSONObject(responseText)
        } catch (_: Exception) {
            null
        }
    }

    fun extractCode(response: JSONObject): Int = response.optInt("code", -1)

    fun isSuccessCode(code: Int): Boolean = code == 0 || code == 200

    fun extractObjectList(response: JSONObject): JSONArray? {
        return try {
            response.getJSONObject("data").optJSONArray("list")
        } catch (_: Exception) {
            null
        }
    }

    fun extractCandidates(response: JSONObject): JSONArray? {
        val data = if (response.has("data")) response.opt("data") else null
        return when (data) {
            is JSONObject -> data.optJSONArray("list")
            is JSONArray -> data
            else -> null
        }
    }
}
