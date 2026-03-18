package org.xjtuai.kqchecker.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import org.xjtuai.kqchecker.model.HomeworkRecord
import java.io.File
import java.util.UUID

class HomeworkRepository(private val context: Context) {
    companion object {
        private const val TAG = "HomeworkRepository"
        private const val HOMEWORK_FILE = "homework_records.json"
        private const val IMAGE_DIR = "homework_images"
    }

    private val homeworkFile: File
        get() = File(context.filesDir, HOMEWORK_FILE)

    data class ImageCaptureDestination(
        val uri: Uri,
        val absolutePath: String
    )

    fun getAllRecords(): List<HomeworkRecord> {
        if (!homeworkFile.exists()) return emptyList()
        return try {
            val json = JSONArray(homeworkFile.readText())
            buildList {
                for (index in 0 until json.length()) {
                    val item = json.optJSONObject(index) ?: continue
                    add(item.toHomeworkRecord())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read homework records", e)
            emptyList()
        }
    }

    fun saveRecord(record: HomeworkRecord): HomeworkRecord {
        val records = getAllRecords().toMutableList()
        val index = records.indexOfFirst { it.id == record.id }
        val normalized = record.copy(updatedAtMillis = System.currentTimeMillis())
        if (index >= 0) {
            records[index] = normalized
        } else {
            records.add(normalized)
        }
        writeRecords(records)
        return normalized
    }

    fun createRecord(
        courseName: String,
        location: String,
        teacher: String,
        dayOfWeek: Int,
        startPeriod: Int,
        endPeriod: Int,
        title: String,
        dueDateMillis: Long,
        photoPath: String?
    ): HomeworkRecord {
        val now = System.currentTimeMillis()
        return HomeworkRecord(
            id = UUID.randomUUID().toString(),
            courseName = courseName,
            location = location,
            teacher = teacher,
            dayOfWeek = dayOfWeek,
            startPeriod = startPeriod,
            endPeriod = endPeriod,
            title = title,
            dueDateMillis = dueDateMillis,
            photoPath = photoPath,
            createdAtMillis = now,
            updatedAtMillis = now
        )
    }

    fun copyImageToAppStorage(uri: Uri): String? {
        return try {
            val imageDir = File(context.filesDir, IMAGE_DIR).apply { mkdirs() }
            val extension = context.contentResolver.getType(uri)
                ?.substringAfterLast('/', "jpg")
                ?: "jpg"
            val destination = File(imageDir, "${UUID.randomUUID()}.$extension")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destination.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy homework image", e)
            null
        }
    }

    fun createImageCaptureDestination(): ImageCaptureDestination? {
        return try {
            val imageDir = File(context.filesDir, IMAGE_DIR).apply { mkdirs() }
            val destination = File(imageDir, "${UUID.randomUUID()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destination
            )
            ImageCaptureDestination(uri = uri, absolutePath = destination.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create image capture destination", e)
            null
        }
    }

    fun deleteImage(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
            .onFailure { Log.w(TAG, "Failed to delete image: $path", it) }
    }

    private fun writeRecords(records: List<HomeworkRecord>) {
        val json = JSONArray()
        records.sortedBy { it.dueDateMillis }.forEach { record ->
            json.put(record.toJson())
        }
        homeworkFile.writeText(json.toString())
    }

    private fun HomeworkRecord.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("courseName", courseName)
            put("location", location)
            put("teacher", teacher)
            put("dayOfWeek", dayOfWeek)
            put("startPeriod", startPeriod)
            put("endPeriod", endPeriod)
            put("title", title)
            put("dueDateMillis", dueDateMillis)
            put("photoPath", photoPath)
            put("createdAtMillis", createdAtMillis)
            put("updatedAtMillis", updatedAtMillis)
        }
    }

    private fun JSONObject.toHomeworkRecord(): HomeworkRecord {
        return HomeworkRecord(
            id = optString("id"),
            courseName = optString("courseName"),
            location = optString("location"),
            teacher = optString("teacher"),
            dayOfWeek = optInt("dayOfWeek"),
            startPeriod = optInt("startPeriod"),
            endPeriod = optInt("endPeriod"),
            title = optString("title"),
            dueDateMillis = optLong("dueDateMillis"),
            photoPath = optString("photoPath").ifBlank { null },
            createdAtMillis = optLong("createdAtMillis"),
            updatedAtMillis = optLong("updatedAtMillis")
        )
    }
}
