package org.xjtuai.kqchecker.model

data class HomeworkRecord(
    val id: String,
    val courseName: String,
    val location: String,
    val teacher: String,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val title: String,
    val dueDateMillis: Long,
    val photoPath: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)
