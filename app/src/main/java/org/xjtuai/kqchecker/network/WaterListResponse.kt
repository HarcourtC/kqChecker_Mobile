package org.xjtuai.kqchecker.network

import org.json.JSONObject

/**
 * 水单数据响应模型（API2 实际格式）
 */
data class WaterListResponse(
    val code: Int,
    val success: Boolean,
    val data: WaterListData,
    val msg: String
) {
    companion object {
        fun fromJson(jsonString: String): WaterListResponse {
            val jsonObject = JSONObject(jsonString)
            return WaterListResponse(
                code = jsonObject.optInt("code"),
                success = jsonObject.optBoolean("success"),
                data = WaterListData.fromJson(jsonObject.optJSONObject("data") ?: JSONObject()),
                msg = jsonObject.optString("msg", "")
            )
        }
    }
}

/**
 * 水单分页数据（API2 实际格式）
 */
data class WaterListData(
    val totalCount: Int,
    val pageSize: Int,
    val totalPage: Int,
    val currPage: Int,
    val list: List<WaterRecord>
) {
    companion object {
        fun fromJson(jsonObject: JSONObject): WaterListData {
            val listArray = jsonObject.optJSONArray("list") ?: org.json.JSONArray()
            val records = mutableListOf<WaterRecord>()
            for (i in 0 until listArray.length()) {
                val item = listArray.optJSONObject(i)
                if (item != null) records.add(WaterRecord.fromJson(item))
            }
            return WaterListData(
                totalCount = jsonObject.optInt("totalCount"),
                pageSize = jsonObject.optInt("pageSize"),
                totalPage = jsonObject.optInt("totalPage"),
                currPage = jsonObject.optInt("currPage"),
                list = records
            )
        }
    }
}

/**
 * 单条考勤打卡记录（API2 实际格式）
 * - eqno: 打卡地点（例如"教2楼-西307"）
 * - watertime: 打卡时间
 * - intime: 系统入库时间
 * - isdone: 状态码（"0"=无效, "1"=有效, "2"=重复）
 * - fromtype: 打卡方式（"1"=人脸识别, "2"=手动等）
 */
data class WaterRecord(
    val bh: String,
    val sno: String,
    val eqno: String,       // 打卡地点
    val eqname: String,     // 设备名称（通常是编号，不直接显示）
    val watertime: String,  // 打卡时间
    val intime: String,     // 入库时间
    val isdone: String,     // 状态码
    val fromtype: String,   // 打卡方式
    val calendarBh: String,
    val sBh: String
) {
    /** isdone 转可读文字 */
    val statusText: String get() = when (isdone) {
        "0" -> "无效"
        "1" -> "有效"
        "2" -> "重复"
        else -> "状态($isdone)"
    }

    /** fromtype 转可读文字 */
    val fromTypeText: String get() = when (fromtype) {
        "1" -> "人脸识别"
        "2" -> "手动"
        "3" -> "补签"
        else -> "方式($fromtype)"
    }

    companion object {
        fun fromJson(jsonObject: JSONObject): WaterRecord {
            return WaterRecord(
                bh = jsonObject.optString("bh"),
                sno = jsonObject.optString("sno"),
                eqno = jsonObject.optString("eqno"),
                eqname = jsonObject.optString("eqname"),
                watertime = jsonObject.optString("watertime"),
                intime = jsonObject.optString("intime"),
                isdone = jsonObject.optString("isdone"),
                fromtype = jsonObject.optString("fromtype"),
                calendarBh = jsonObject.optString("calendarBh"),
                sBh = jsonObject.optString("sBh")
            )
        }
    }
}
