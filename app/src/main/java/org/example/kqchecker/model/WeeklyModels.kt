package org.example.kqchecker.model

data class EventRaw(
    val accountWeeknum: String? = null,
    val accountJtNo: String? = null,
    val subjectSCode: String? = null,
    val teachNameList: String? = null
)

data class EventItem(
    val course: String? = null,
    val room: String? = null,
    val raw: EventRaw? = null
)
