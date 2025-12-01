package org.example.kqchecker.network

import org.json.JSONObject

/**
 * 水单数据响应模型
 */
data class WaterListResponse(
    val code: Int,
    val success: Boolean,
    val data: WaterListData,
    val msg: String
) {
    companion object {
        /**
         * 从JSON字符串创建WaterListResponse实例
         */
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
 * 水单数据内容
 */
data class WaterListData(
    val records: List<WaterRecord>,
    val total: Int,
    val size: Int,
    val current: Int,
    val orders: List<Order>,
    val optimizeCountSql: Boolean,
    val hitCount: Boolean,
    val countId: String,
    val maxLimit: String,
    val searchCount: Boolean,
    val pages: Int
) {
    companion object {
        /**
         * 从JSONObject创建WaterListData实例
         */
        fun fromJson(jsonObject: JSONObject): WaterListData {
            val recordsArray = jsonObject.optJSONArray("records") ?: org.json.JSONArray()
            val records = mutableListOf<WaterRecord>()
            for (i in 0 until recordsArray.length()) {
                if (recordsArray[i] is JSONObject) {
                    records.add(WaterRecord.fromJson(recordsArray.getJSONObject(i)))
                }
            }
            
            val ordersArray = jsonObject.optJSONArray("orders") ?: org.json.JSONArray()
            val orders = mutableListOf<Order>()
            for (i in 0 until ordersArray.length()) {
                if (ordersArray[i] is JSONObject) {
                    orders.add(Order.fromJson(ordersArray.getJSONObject(i)))
                }
            }
            
            return WaterListData(
                records = records,
                total = jsonObject.optInt("total"),
                size = jsonObject.optInt("size"),
                current = jsonObject.optInt("current"),
                orders = orders,
                optimizeCountSql = jsonObject.optBoolean("optimizeCountSql"),
                hitCount = jsonObject.optBoolean("hitCount"),
                countId = jsonObject.optString("countId"),
                maxLimit = jsonObject.optString("maxLimit"),
                searchCount = jsonObject.optBoolean("searchCount"),
                pages = jsonObject.optInt("pages")
            )
        }
    }
}

/**
 * 水单记录项
 */
data class WaterRecord(
    val waterBh: String,
    val waterName: String,
    val waterTime: String,
    val waterAddress: String,
    val waterContent: String,
    val waterPeople: String,
    val createTime: String,
    val updateTime: String,
    val waterStatus: String,
    val accountName: String,
    val accountBh: String,
    val accountXh: String,
    val calendarBh: String,
    val calendarName: String
) {
    companion object {
        /**
         * 从JSONObject创建WaterRecord实例
         */
        fun fromJson(jsonObject: JSONObject): WaterRecord {
            return WaterRecord(
                waterBh = jsonObject.optString("waterBh"),
                waterName = jsonObject.optString("waterName"),
                waterTime = jsonObject.optString("waterTime"),
                waterAddress = jsonObject.optString("waterAddress"),
                waterContent = jsonObject.optString("waterContent"),
                waterPeople = jsonObject.optString("waterPeople"),
                createTime = jsonObject.optString("createTime"),
                updateTime = jsonObject.optString("updateTime"),
                waterStatus = jsonObject.optString("waterStatus"),
                accountName = jsonObject.optString("accountName"),
                accountBh = jsonObject.optString("accountBh"),
                accountXh = jsonObject.optString("accountXh"),
                calendarBh = jsonObject.optString("calendarBh"),
                calendarName = jsonObject.optString("calendarName")
            )
        }
    }
}

/**
 * 排序信息
 */
data class Order(
    val column: String,
    val asc: Boolean
) {
    companion object {
        /**
         * 从JSONObject创建Order实例
         */
        fun fromJson(jsonObject: JSONObject): Order {
            return Order(
                column = jsonObject.optString("column"),
                asc = jsonObject.optBoolean("asc")
            )
        }
    }
}