package com.example.steeldefectdetector.utils

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.example.steeldefectdetector.model.annotation.AnnotationBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object DatasetExporter {
 /**
 * 将图片与标注数据异步保存为 YOLO 格式数据集 (保存至公共 Downloads 目录)
 * 返回保存的图片文件路径，若失败则抛出异常
 */
 suspend fun saveYoloDataset(
 context: Context,
 imageBitmap: ImageBitmap,
 annotations: List<AnnotationBox>
 ): String = withContext(Dispatchers.IO) {
 // 1. 创建目录结构 (强制指向公共 Download 目录)
 val baseDir = File(
 Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
 "SteelDefectDataset"
 )
 val imagesDir = File(baseDir, "images")
 val labelsDir = File(baseDir, "labels")
 
 if (!imagesDir.exists()) {
 val created = imagesDir.mkdirs()
 if (!created) throw RuntimeException("无法在 Downloads 目录下创建 images 文件夹，请检查存储权限。")
 }
 if (!labelsDir.exists()) {
 val created = labelsDir.mkdirs()
 if (!created) throw RuntimeException("无法在 Downloads 目录下创建 labels 文件夹，请检查存储权限。")
 }

 // 2. 生成统一文件名前缀 (时间戳)
 val timestamp = System.currentTimeMillis()
 val baseFileName = "STEEL_DEFECT_$timestamp"

 val imageFile = File(imagesDir, "$baseFileName.jpg")
 val labelFile = File(labelsDir, "$baseFileName.txt")

 try {
 // 3. 保存图片
 FileOutputStream(imageFile).use { out ->
 imageBitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, 100, out)
 }

 // 4. 保存 YOLO 标签
 val androidBitmap = imageBitmap.asAndroidBitmap()
 val imageWidth = androidBitmap.width
 val imageHeight = androidBitmap.height

 labelFile.bufferedWriter().use { writer ->
 annotations.forEach { box ->
 val yoloLine = box.toYoloFormat(imageWidth, imageHeight)
 writer.write(yoloLine)
 writer.newLine()
 }
 }
 
 return@withContext imageFile.absolutePath
 } catch (e: Exception) {
 // 原子性防守：如果出错，清理可能残留的半截文件
 if (imageFile.exists()) imageFile.delete()
 if (labelFile.exists()) labelFile.delete()
 throw e
 }
 }
}