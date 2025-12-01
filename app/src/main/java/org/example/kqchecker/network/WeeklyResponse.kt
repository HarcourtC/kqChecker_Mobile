package org.example.kqchecker.network

import org.json.JSONArray
import org.json.JSONObject

/**
 * 周课表响应数据模型
 */
data class WeeklyResponse(
    val code: Int,
    val success: Boolean,
    val data: JSONArray,
    val msg: String,
    val date: String,
    val expires: String
) {
    companion object {
        /**
         * 从JSON字符串创建WeeklyResponse实例
         */
        fun fromJson(jsonString: String): WeeklyResponse {
            val jsonObject = JSONObject(jsonString)
            return WeeklyResponse(
                code = jsonObject.optInt("code"),
                success = jsonObject.optBoolean("success"),
                data = jsonObject.optJSONArray("data") ?: JSONArray(),
                msg = jsonObject.optString("msg", ""),
                date = jsonObject.optString("date", ""),
                expires = jsonObject.optString("expires", "")
            )
        }
        
        /**
         * 创建带日期和过期信息的WeeklyResponse
         */
        fun createWithDate(data: JSONArray, date: String, expires: String): WeeklyResponse {
            return WeeklyResponse(
                code = 200,
                success = true,
                data = data,
                msg = "操作成功",
                date = date,
                expires = expires
            )
        }
    }
    
    /**
     * 转换为JSON字符串
     */
    fun toJson(indentFactor: Int = 0): String {
        val jsonObject = JSONObject()
        jsonObject.put("code", code)
        jsonObject.put("success", success)
        jsonObject.put("data", data)
        jsonObject.put("msg", msg)
        jsonObject.put("date", date)
        jsonObject.put("expires", expires)
        return jsonObject.toString(indentFactor)
    }
}