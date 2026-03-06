package org.xjtuai.kqchecker.network

import org.json.JSONObject

/**
 * 当前学期信息响应模型
 */
data class CurrentTermResponse(
    val code: Int,
    val success: Boolean,
    val bh: Int,
    val name: String,
    val startDate: String,
    val endDate: String,
    val weeks: Int,
    val currentWeek: Int,
    val currentDate: String,
    val msg: String
) {
    companion object {
        fun fromJson(jsonString: String): CurrentTermResponse {
            val obj = JSONObject(jsonString)
            val data = obj.optJSONObject("data") ?: JSONObject()
            
            return CurrentTermResponse(
                code = obj.optInt("code"),
                success = obj.optBoolean("success"),
                bh = data.optInt("bh", 0),
                name = data.optString("name", ""),
                startDate = data.optString("startdate", ""),
                endDate = data.optString("enddate", ""),
                weeks = data.optInt("weeks", 0),
                currentWeek = try { data.optString("currentWeek", "1").toInt() } catch (e: Exception) { 1 },
                currentDate = data.optString("currentDate", ""),
                msg = obj.optString("msg", "")
            )
        }
    }
}