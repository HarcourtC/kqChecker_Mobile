package org.example.kqchecker.repository

import android.content.Context

/**
 * Repository提供者，提供应用中所有Repository实例的统一访问点
 * 使用单例模式管理Repository实例，避免重复创建
 */
object RepositoryProvider {
    private var weeklyRepository: WeeklyRepository? = null
    private var waterListRepository: WaterListRepository? = null
    private var debugRepository: DebugRepository? = null
    private var weeklyCleaner: WeeklyCleaner? = null
    private var cacheManager: CacheManager? = null
    
    /**
     * 初始化Repository提供者
     * @param context 应用上下文
     */
    fun initialize(context: Context) {
        // 确保使用应用上下文，避免内存泄漏
        val appContext = context.applicationContext
        cacheManager = CacheManager(appContext)
        weeklyRepository = WeeklyRepository(appContext)
        waterListRepository = WaterListRepository(appContext)
        debugRepository = DebugRepository(appContext)
        weeklyCleaner = WeeklyCleaner(appContext)
    }
    
    /**
     * 获取WeeklyRepository实例
     */
    fun getWeeklyRepository(): WeeklyRepository {
        return weeklyRepository ?: throw IllegalStateException("RepositoryProvider not initialized")
    }
    
    /**
     * 获取WaterListRepository实例
     */
    fun getWaterListRepository(): WaterListRepository {
        return waterListRepository ?: throw IllegalStateException("RepositoryProvider not initialized")
    }

    fun getWeeklyCleaner(): WeeklyCleaner {
        return weeklyCleaner ?: throw IllegalStateException("RepositoryProvider not initialized")
    }
    
    /**
     * 获取CacheManager实例
     */
    fun getCacheManager(): CacheManager {
        return cacheManager ?: throw IllegalStateException("RepositoryProvider not initialized")
    }

    fun getDebugRepository(): DebugRepository {
        return debugRepository ?: throw IllegalStateException("RepositoryProvider not initialized")
    }
    
    /**
     * 重置所有Repository实例（用于测试或特定场景）
     */
    fun reset() {
        weeklyRepository = null
        waterListRepository = null
        cacheManager = null
        weeklyCleaner = null
    }
}