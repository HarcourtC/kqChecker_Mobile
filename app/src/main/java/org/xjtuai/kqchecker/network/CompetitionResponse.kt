package org.xjtuai.kqchecker.network

import org.json.JSONArray
import org.json.JSONObject

/**
 * 竞赛数据响应模型
 */
data class CompetitionResponse(
    val status: String,
    val meta: CompetitionMeta,
    val data: List<CompetitionItem>
) {
    companion object {
        /**
         * 从JSON字符串创建CompetitionResponse实例
         */
        fun fromJson(jsonString: String): CompetitionResponse {
            val jsonObject = JSONObject(jsonString)
            return CompetitionResponse(
                status = jsonObject.optString("status"),
                meta = CompetitionMeta.fromJson(jsonObject.optJSONObject("meta") ?: JSONObject()),
                data = parseCompetitionItems(jsonObject.optJSONArray("data") ?: JSONArray())
            )
        }

        private fun parseCompetitionItems(jsonArray: JSONArray): List<CompetitionItem> {
            val items = mutableListOf<CompetitionItem>()
            for (i in 0 until jsonArray.length()) {
                if (jsonArray[i] is JSONObject) {
                    items.add(CompetitionItem.fromJson(jsonArray.getJSONObject(i)))
                }
            }
            return items
        }
    }
}

/**
 * 竞赛元数据
 */
data class CompetitionMeta(
    val updateTime: String,
    val total: Int,
    val method: String
) {
    companion object {
        /**
         * 从JSONObject创建CompetitionMeta实例
         */
        fun fromJson(jsonObject: JSONObject): CompetitionMeta {
            return CompetitionMeta(
                updateTime = jsonObject.optString("updateTime"),
                total = jsonObject.optInt("total"),
                method = jsonObject.optString("method")
            )
        }
    }
}

/**
 * 竞赛项目
 */
data class CompetitionItem(
    val id: String,
    val type: String,
    val category: String,
    val title: String,
    val url: String,
    val date: String,
    val isNew: Boolean
) {
    companion object {
        /**
         * 从JSONObject创建CompetitionItem实例
         */
        fun fromJson(jsonObject: JSONObject): CompetitionItem {
            return CompetitionItem(
                id = jsonObject.optString("id"),
                type = jsonObject.optString("type"),
                category = jsonObject.optString("category"),
                title = jsonObject.optString("title"),
                url = jsonObject.optString("url"),
                date = jsonObject.optString("date"),
                isNew = jsonObject.optBoolean("isNew")
            )
        }
    }
}
