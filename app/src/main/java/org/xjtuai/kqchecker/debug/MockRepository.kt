package org.xjtuai.kqchecker.debug

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.xjtuai.kqchecker.model.EventItem
import java.io.InputStreamReader
import org.xjtuai.kqchecker.model.PeriodItem

/**
 * Mock repository for loading test data from assets
 * Used for debugging and testing without live API calls
 */
class MockRepository(private val context: Context) {
    companion object {
        private const val TAG = "MockRepository"
    }
    private val moshi = Moshi.Builder().build()

    fun loadWeeklyFromAssets(): Map<String, List<EventItem>> {
        val assetName = "weekly.json"
        context.assets.open(assetName).use { stream ->
            val reader = InputStreamReader(stream, Charsets.UTF_8)
            val type = Types.newParameterizedType(Map::class.java, String::class.java,
                Types.newParameterizedType(List::class.java, EventItem::class.java))
            val adapter = moshi.adapter<Map<String, List<EventItem>>>(type)
            return adapter.fromJson(reader.readText()) ?: emptyMap()
        }
    }

    fun loadPeriodsFromAssets(): List<PeriodItem> {
        try {
            context.assets.open("periods.json").use { stream ->
                val reader = InputStreamReader(stream, Charsets.UTF_8)
                val itemType = Types.newParameterizedType(List::class.java, PeriodItem::class.java)
                val adapter = moshi.adapter<List<PeriodItem>>(itemType)
                return adapter.fromJson(reader.readText()) ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading periods", e)
        }
        return emptyList()
    }
}
