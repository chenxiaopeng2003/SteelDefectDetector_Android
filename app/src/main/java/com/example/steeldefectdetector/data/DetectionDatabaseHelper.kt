package com.example.steeldefectdetector.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.steeldefectdetector.model.DetectionResult
import com.example.steeldefectdetector.model.DetectionHistory
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DetectionDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {
    
    companion object {
        private const val DATABASE_NAME = "steel_defect_detections.db"
        private const val DATABASE_VERSION = 2
        
        // 表名
        const val TABLE_DETECTIONS = "detections"
        const val TABLE_IMAGES = "images"
        
        // 检测记录表字段
        const val COLUMN_ID = "id"
        const val COLUMN_IMAGE_PATH = "image_path"
        const val COLUMN_MODEL_USED = "model_used"
        const val COLUMN_RESULTS_JSON = "results_json"
        const val COLUMN_INFERENCE_TIME = "inference_time"  // 新增：推理时间
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_NOTE = "note"
        
        // 图片记录表字段
        const val COLUMN_IMAGE_ID = "id"
        const val COLUMN_IMAGE_SOURCE = "source"
        const val COLUMN_IMAGE_TIMESTAMP = "timestamp"
        
        // 创建检测记录表SQL
        private const val SQL_CREATE_DETECTIONS_TABLE = """
            CREATE TABLE $TABLE_DETECTIONS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_IMAGE_PATH TEXT NOT NULL,
                $COLUMN_MODEL_USED TEXT NOT NULL,
                $COLUMN_RESULTS_JSON TEXT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_NOTE TEXT
            )
        """
        
        // 创建图片记录表SQL
        private const val SQL_CREATE_IMAGES_TABLE = """
            CREATE TABLE $TABLE_IMAGES (
                $COLUMN_IMAGE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_IMAGE_PATH TEXT NOT NULL,
                $COLUMN_IMAGE_SOURCE TEXT NOT NULL,
                $COLUMN_IMAGE_TIMESTAMP INTEGER NOT NULL
            )
        """
        
        // 删除表SQL
        private const val SQL_DROP_DETECTIONS_TABLE = "DROP TABLE IF EXISTS $TABLE_DETECTIONS"
        private const val SQL_DROP_IMAGES_TABLE = "DROP TABLE IF EXISTS $TABLE_IMAGES"
    }
    
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_DETECTIONS_TABLE)
        db.execSQL(SQL_CREATE_IMAGES_TABLE)
    }
    
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // 版本1到版本2：添加推理时间字段
            db.execSQL("ALTER TABLE $TABLE_DETECTIONS ADD COLUMN $COLUMN_INFERENCE_TIME INTEGER DEFAULT 0")
        }
        // 如果需要更多升级，可以继续添加
    }
    
    /**
     * 保存检测结果
     */
    fun saveDetection(
        imagePath: String,
        modelUsed: String,
        results: List<DetectionResult>,
        inferenceTime: Long = 0,  // 新增：推理时间
        timestamp: Long = System.currentTimeMillis(),
        note: String? = null
    ): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_IMAGE_PATH, imagePath)
            put(COLUMN_MODEL_USED, modelUsed)
            put(COLUMN_RESULTS_JSON, convertResultsToJson(results))
            put(COLUMN_TIMESTAMP, timestamp)
            note?.let { put(COLUMN_NOTE, it) }
        }
        
        return db.insert(TABLE_DETECTIONS, null, values)
    }
    
    /**
     * 获取所有检测历史记录
     */
    fun getAllDetectionHistory(): List<DetectionHistory> {
        val historyList = mutableListOf<DetectionHistory>()
        val db = readableDatabase
        
        val cursor = db.query(
            TABLE_DETECTIONS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_TIMESTAMP DESC"  // 按时间倒序排列
        )
        
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID))
                val imagePath = it.getString(it.getColumnIndexOrThrow(COLUMN_IMAGE_PATH))
                val modelUsed = it.getString(it.getColumnIndexOrThrow(COLUMN_MODEL_USED))
                val resultsJson = it.getString(it.getColumnIndexOrThrow(COLUMN_RESULTS_JSON))

                val timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                val note = it.getString(it.getColumnIndexOrThrow(COLUMN_NOTE))
                // 在 while (it.moveToNext()) 循环内
                val inferenceTimeIdx = it.getColumnIndex(COLUMN_INFERENCE_TIME)
                val inferenceTime = if (inferenceTimeIdx != -1) it.getLong(inferenceTimeIdx) else 0L
                // 解析结果JSON获取缺陷数量
                val results = parseResultsFromJson(resultsJson)
                val defectCount = results.size
                
                // 创建历史记录对象
                val history = DetectionHistory(
                    id = id,
                    timestamp = timestamp,
                    modelName = modelUsed,
                    imagePath = imagePath,
                    imageWidth = 0,  // 可以从图片文件获取，这里简化
                    imageHeight = 0,
                    defectCount = defectCount,
                    inferenceTime = inferenceTime,
                    comparisonData = note ?: "检测时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(timestamp))}\n使用模型: $modelUsed\n缺陷数量: $defectCount\n推理耗时: ${inferenceTime}ms",
                    results = results
                )
                
                historyList.add(history)
            }
        }
        
        return historyList
    }
    
    /**
     * 根据ID获取检测历史详情
     */
    fun getDetectionHistoryById(id: Long): DetectionHistory? {
        val db = readableDatabase
        
        val cursor = db.query(
            TABLE_DETECTIONS,
            null,
            "$COLUMN_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        
        cursor.use {
            if (it.moveToFirst()) {
                val imagePath = it.getString(it.getColumnIndexOrThrow(COLUMN_IMAGE_PATH))
                val modelUsed = it.getString(it.getColumnIndexOrThrow(COLUMN_MODEL_USED))
                val resultsJson = it.getString(it.getColumnIndexOrThrow(COLUMN_RESULTS_JSON))
                val inferenceTime = it.getLong(it.getColumnIndexOrThrow(COLUMN_INFERENCE_TIME))
                val timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                val note = it.getString(it.getColumnIndexOrThrow(COLUMN_NOTE))
                
                // 解析结果JSON获取缺陷数量
                val results = parseResultsFromJson(resultsJson)
                val defectCount = results.size
                
                return DetectionHistory(
                    id = id,
                    timestamp = timestamp,
                    modelName = modelUsed,
                    imagePath = imagePath,
                    imageWidth = 0,
                    imageHeight = 0,
                    defectCount = defectCount,
                    inferenceTime = inferenceTime,
                    comparisonData = note ?: "检测时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(timestamp))}\n使用模型: $modelUsed\n缺陷数量: $defectCount\n推理耗时: ${inferenceTime}ms\n\n详细结果:\n${results.joinToString("\n") { "- ${it.className}: ${it.confidence}%" }}",
                    results = results
                )
            }
        }
        
        return null
    }
    
    /**
     * 保存图片记录
     */
    fun saveImageRecord(
        imagePath: String,
        source: String,
        timestamp: Long = System.currentTimeMillis()
    ): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_IMAGE_PATH, imagePath)
            put(COLUMN_IMAGE_SOURCE, source)
            put(COLUMN_IMAGE_TIMESTAMP, timestamp)
        }
        
        return db.insert(TABLE_IMAGES, null, values)
    }
    
    /**
     * 获取所有检测记录
     */
    fun getAllDetections(): List<DetectionRecord> {
        val records = mutableListOf<DetectionRecord>()
        val db = readableDatabase
        
        val projection = arrayOf(
            COLUMN_ID,
            COLUMN_IMAGE_PATH,
            COLUMN_MODEL_USED,
            COLUMN_RESULTS_JSON,
            COLUMN_TIMESTAMP,
            COLUMN_NOTE
        )
        
        val cursor = db.query(
            TABLE_DETECTIONS,
            projection,
            null,
            null,
            null,
            null,
            "$COLUMN_TIMESTAMP DESC"
        )
        
        with(cursor) {
            while (moveToNext()) {
                val id = getLong(getColumnIndexOrThrow(COLUMN_ID))
                val imagePath = getString(getColumnIndexOrThrow(COLUMN_IMAGE_PATH))
                val modelUsed = getString(getColumnIndexOrThrow(COLUMN_MODEL_USED))
                val resultsJson = getString(getColumnIndexOrThrow(COLUMN_RESULTS_JSON))
                val timestamp = getLong(getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                val note = getString(getColumnIndexOrThrow(COLUMN_NOTE))
                
                val results = parseResultsFromJson(resultsJson)
                
                records.add(DetectionRecord(id, imagePath, modelUsed, results, timestamp, note))
            }
        }
        
        cursor.close()
        return records
    }
    
    /**
     * 获取所有图片记录
     */
    fun getAllImages(): List<ImageRecord> {
        val records = mutableListOf<ImageRecord>()
        val db = readableDatabase
        
        val projection = arrayOf(
            COLUMN_IMAGE_ID,
            COLUMN_IMAGE_PATH,
            COLUMN_IMAGE_SOURCE,
            COLUMN_IMAGE_TIMESTAMP
        )
        
        val cursor = db.query(
            TABLE_IMAGES,
            projection,
            null,
            null,
            null,
            null,
            "$COLUMN_IMAGE_TIMESTAMP DESC"
        )
        
        with(cursor) {
            while (moveToNext()) {
                val id = getLong(getColumnIndexOrThrow(COLUMN_IMAGE_ID))
                val imagePath = getString(getColumnIndexOrThrow(COLUMN_IMAGE_PATH))
                val source = getString(getColumnIndexOrThrow(COLUMN_IMAGE_SOURCE))
                val timestamp = getLong(getColumnIndexOrThrow(COLUMN_IMAGE_TIMESTAMP))
                
                records.add(ImageRecord(id, imagePath, source, timestamp))
            }
        }
        
        cursor.close()
        return records
    }
    
    /**
     * 根据ID获取检测记录
     */
    fun getDetectionById(id: Long): DetectionRecord? {
        val db = readableDatabase
        
        val projection = arrayOf(
            COLUMN_ID,
            COLUMN_IMAGE_PATH,
            COLUMN_MODEL_USED,
            COLUMN_RESULTS_JSON,
            COLUMN_TIMESTAMP,
            COLUMN_NOTE
        )
        
        val selection = "$COLUMN_ID = ?"
        val selectionArgs = arrayOf(id.toString())
        
        val cursor = db.query(
            TABLE_DETECTIONS,
            projection,
            selection,
            selectionArgs,
            null,
            null,
            null
        )
        
        return with(cursor) {
            if (moveToFirst()) {
                val detectionId = getLong(getColumnIndexOrThrow(COLUMN_ID))
                val imagePath = getString(getColumnIndexOrThrow(COLUMN_IMAGE_PATH))
                val modelUsed = getString(getColumnIndexOrThrow(COLUMN_MODEL_USED))
                val resultsJson = getString(getColumnIndexOrThrow(COLUMN_RESULTS_JSON))
                val timestamp = getLong(getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                val note = getString(getColumnIndexOrThrow(COLUMN_NOTE))
                
                val results = parseResultsFromJson(resultsJson)
                
                DetectionRecord(detectionId, imagePath, modelUsed, results, timestamp, note)
            } else {
                null
            }
        }.also {
            cursor.close()
        }
    }
    
    /**
     * 删除检测记录
     */
    fun deleteDetection(id: Long): Int {
        val db = writableDatabase
        val selection = "$COLUMN_ID = ?"
        val selectionArgs = arrayOf(id.toString())
        
        return db.delete(TABLE_DETECTIONS, selection, selectionArgs)
    }
    
    /**
     * 删除图片记录
     */
    fun deleteImage(id: Long): Int {
        val db = writableDatabase
        val selection = "$COLUMN_IMAGE_ID = ?"
        val selectionArgs = arrayOf(id.toString())
        
        return db.delete(TABLE_IMAGES, selection, selectionArgs)
    }
    
    /**
     * 清空所有记录
     */
    fun clearAllRecords(): Int {
        val db = writableDatabase
        var count = 0
        count += db.delete(TABLE_DETECTIONS, null, null)
        count += db.delete(TABLE_IMAGES, null, null)
        return count
    }
    
    /**
     * 获取记录数量
     */
    fun getDetectionsCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_DETECTIONS", null)
        
        return if (cursor.moveToFirst()) {
            cursor.getInt(0)
        } else {
            0
        }.also {
            cursor.close()
        }
    }
    
    fun getImagesCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_IMAGES", null)
        
        return if (cursor.moveToFirst()) {
            cursor.getInt(0)
        } else {
            0
        }.also {
            cursor.close()
        }
    }
    
    /**
     * 导出检测记录为CSV
     */
    fun exportDetectionsToCsv(): String {
        val detections = getAllDetections()
        val csvBuilder = StringBuilder()
        
        // 添加表头
        csvBuilder.append("ID,图片路径,使用模型,缺陷数量,检测时间,备注\n")
        
        // 添加数据行
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        
        detections.forEach { detection ->
            val date = Date(detection.timestamp)
            val formattedDate = dateFormat.format(date)
            
            csvBuilder.append("${detection.id},")
            csvBuilder.append("\"${detection.imagePath}\",")
            csvBuilder.append("\"${detection.modelUsed}\",")
            csvBuilder.append("${detection.results.size},")
            csvBuilder.append("\"$formattedDate\",")
            csvBuilder.append("\"${detection.note ?: ""}\"\n")
        }
        
        return csvBuilder.toString()
    }
    
    /**
     * 导出图片记录为CSV
     */
    fun exportImagesToCsv(): String {
        val images = getAllImages()
        val csvBuilder = StringBuilder()
        
        // 添加表头
        csvBuilder.append("ID,图片路径,来源,保存时间\n")
        
        // 添加数据行
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        
        images.forEach { image ->
            val date = Date(image.timestamp)
            val formattedDate = dateFormat.format(date)
            
            csvBuilder.append("${image.id},")
            csvBuilder.append("\"${image.imagePath}\",")
            csvBuilder.append("\"${image.source}\",")
            csvBuilder.append("\"$formattedDate\"\n")
        }
        
        return csvBuilder.toString()
    }
    
    /**
     * 将检测结果列表转换为JSON字符串
     */
    private fun convertResultsToJson(results: List<DetectionResult>): String {
        val jsonArray = JSONArray()
        
        results.forEach { result ->
            val jsonObject = JSONObject().apply {
                put("className", result.className)
                put("confidence", result.confidence)
                put("x1", result.x1)
                put("y1", result.y1)
                put("x2", result.x2)
                put("y2", result.y2)
                put("description", result.description)
            }
            jsonArray.put(jsonObject)
        }
        
        return jsonArray.toString()
    }
    
    /**
     * 从JSON字符串解析检测结果列表
     */
    private fun parseResultsFromJson(jsonString: String): List<DetectionResult> {
        val results = mutableListOf<DetectionResult>()
        
        try {
            val jsonArray = JSONArray(jsonString)
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                
                val result = DetectionResult(
                    className = jsonObject.getString("className"),
                    confidence = jsonObject.getDouble("confidence").toFloat(),
                    x1 = jsonObject.getDouble("x1").toFloat(),
                    y1 = jsonObject.getDouble("y1").toFloat(),
                    x2 = jsonObject.getDouble("x2").toFloat(),
                    y2 = jsonObject.getDouble("y2").toFloat(),
                    description = jsonObject.getString("description")
                )
                
                results.add(result)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return results
    }
}

/**
 * 检测记录数据类
 */
data class DetectionRecord(
    val id: Long,
    val imagePath: String,
    val modelUsed: String,
    val results: List<DetectionResult>,
    val timestamp: Long,
    val note: String?
)

/**
 * 图片记录数据类
 */
data class ImageRecord(
    val id: Long,
    val imagePath: String,
    val source: String,
    val timestamp: Long
)