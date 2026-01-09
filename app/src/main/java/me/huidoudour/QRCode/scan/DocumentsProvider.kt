package me.huidoudour.QRCode.scan

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

// 添加辅助扩展函数
private fun String?.isNullOrEmpty(): Boolean = this == null || this.isEmpty()

class DocumentsProvider : DocumentsProvider() {

    companion object {
        const val AUTHORITY = "me.huidoudour.QRCode.scan.documents"
        const val ROOT_DOCUMENT_ID = "root"
        const val HISTORY_DOCUMENT_ID = "history"
        const val EXPORT_DOCUMENT_ID = "export"
        
        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES,
            DocumentsContract.Root.COLUMN_ICON
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val row = cursor.newRow()
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_DOCUMENT_ID)
        row.add(DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.FLAG_SUPPORTS_CREATE or
            DocumentsContract.Root.FLAG_LOCAL_ONLY)
        row.add(DocumentsContract.Root.COLUMN_TITLE, context?.getString(R.string.app_name))
        row.add(DocumentsContract.Root.COLUMN_SUMMARY, "QR Code Scanner Documents")
        row.add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*")
        row.add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, null)
        row.add(DocumentsContract.Root.COLUMN_ICON, R.drawable.ic_launcher_foreground)
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        // 检查documentId是否为null或字符串"null"
        val actualDocumentId = when {
            documentId.isNullOrEmpty() || documentId == "null" -> ROOT_DOCUMENT_ID
            else -> documentId
        }
        
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        
        when (actualDocumentId) {
            ROOT_DOCUMENT_ID -> {
                val row = cursor.newRow()
                row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, actualDocumentId)
                row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, "QR Code Scanner")
                row.add(DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE)
                row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
                row.add(DocumentsContract.Document.COLUMN_SIZE, null)
                row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, null)
            }
            HISTORY_DOCUMENT_ID -> {
                val row = cursor.newRow()
                row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, actualDocumentId)
                row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, "History")
                row.add(DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE)
                row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
                row.add(DocumentsContract.Document.COLUMN_SIZE, null)
                row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, null)
            }
            EXPORT_DOCUMENT_ID -> {
                val row = cursor.newRow()
                row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, actualDocumentId)
                row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, "Export")
                row.add(DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE)
                row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
                row.add(DocumentsContract.Document.COLUMN_SIZE, null)
                row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, null)
            }
            else -> {
                // 检查是否为子目录或文件
                val context = context ?: throw FileNotFoundException("Context is null")
                
                // 检查是否在history目录下
                val historyFile = File(context.getExternalFilesDir(null), "history/$actualDocumentId")
                if (historyFile.exists()) {
                    val row = cursor.newRow()
                    row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, actualDocumentId)
                    row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, historyFile.name)
                    row.add(DocumentsContract.Document.COLUMN_FLAGS, 0)
                    row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, 
                        if (historyFile.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else getMimeType(historyFile.name))
                    row.add(DocumentsContract.Document.COLUMN_SIZE, 
                        if (historyFile.isDirectory) null else historyFile.length())
                    row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, historyFile.lastModified())
                    return cursor
                }
                
                // 检查是否在export目录下
                val exportFile = File(context.getExternalFilesDir(null), "export/$actualDocumentId")
                if (exportFile.exists()) {
                    val row = cursor.newRow()
                    row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, actualDocumentId)
                    row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, exportFile.name)
                    row.add(DocumentsContract.Document.COLUMN_FLAGS, 0)
                    row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, 
                        if (exportFile.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else getMimeType(exportFile.name))
                    row.add(DocumentsContract.Document.COLUMN_SIZE, 
                        if (exportFile.isDirectory) null else exportFile.length())
                    row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, exportFile.lastModified())
                    return cursor
                }
                
                // 如果都不是，则抛出异常
                throw FileNotFoundException("Unknown document: $actualDocumentId")
            }
        }
        
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val actualParentDocumentId = when {
            parentDocumentId.isNullOrEmpty() || parentDocumentId == "null" -> ROOT_DOCUMENT_ID
            else -> parentDocumentId
        }
        
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        
        when (actualParentDocumentId) {
            ROOT_DOCUMENT_ID -> {
                addRow(cursor, HISTORY_DOCUMENT_ID, "History", true)
                addRow(cursor, EXPORT_DOCUMENT_ID, "Export", true)
            }
            HISTORY_DOCUMENT_ID -> {
                // 查询历史目录下的文件
                val context = context ?: throw FileNotFoundException("Context is null")
                val historyDir = File(context.getExternalFilesDir(null), "history")
                if (historyDir.exists() && historyDir.isDirectory) {
                    historyDir.listFiles()?.forEach { file ->
                        addRow(cursor, file.name, file.name, file.isDirectory)
                    }
                }
            }
            EXPORT_DOCUMENT_ID -> {
                // 查询导出目录下的文件
                val context = context ?: throw FileNotFoundException("Context is null")
                val exportDir = File(context.getExternalFilesDir(null), "export")
                if (exportDir.exists() && exportDir.isDirectory) {
                    exportDir.listFiles()?.forEach { file ->
                        addRow(cursor, file.name, file.name, file.isDirectory)
                    }
                }
            }
            else -> {
                // 对于未知的父目录ID，抛出异常
                throw FileNotFoundException("Unknown parent document: $actualParentDocumentId")
            }
        }
        
        return cursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor? {
        // 获取应用的私有存储目录
        val context = context ?: throw FileNotFoundException("Context is null")
        
        // 处理可能的null值或字符串"null"情况
        val actualDocumentId = when {
            documentId.isNullOrEmpty() || documentId == "null" -> ROOT_DOCUMENT_ID
            else -> documentId
        }
        
        val targetFile = when (actualDocumentId) {
            HISTORY_DOCUMENT_ID -> File(context.getExternalFilesDir(null), "history")
            EXPORT_DOCUMENT_ID -> File(context.getExternalFilesDir(null), "export")
            else -> {
                // 检查是否为history目录下的文件
                val historyFile = File(context.getExternalFilesDir(null), "history/$actualDocumentId")
                if (historyFile.exists()) {
                    historyFile
                } 
                // 检查是否为export目录下的文件
                else {
                    val exportFile = File(context.getExternalFilesDir(null), "export/$actualDocumentId")
                    if (exportFile.exists()) {
                        exportFile
                    } else {
                        // 对于未知的documentId，抛出异常而不是创建任意路径的文件
                        throw FileNotFoundException("Unknown document: $actualDocumentId")
                    }
                }
            }
        }
        
        // 创建目录（如果不存在）
        if (!targetFile.exists()) {
            if (targetFile.parentFile?.exists() == false) {
                targetFile.parentFile?.mkdirs()
            }
            targetFile.mkdirs()
        }
        
        // 根据模式返回合适的 ParcelFileDescriptor
        return when (mode) {
            "r" -> ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_READ_ONLY)
            "w", "wt" -> ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_WRITE_ONLY or 
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE)
            "rw" -> ParcelFileDescriptor.open(targetFile, 
                ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE)
            "wa" -> ParcelFileDescriptor.open(targetFile, 
                ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE)
            else -> throw IllegalArgumentException("Invalid mode: $mode")
        }
    }

    private fun addRow(
        cursor: MatrixCursor,
        documentId: String,
        displayName: String,
        isDirectory: Boolean
    ) {
        val row = cursor.newRow()
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
        row.add(DocumentsContract.Document.COLUMN_FLAGS, 
            if (isDirectory) DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE else 0)
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, 
            if (isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else getMimeType(displayName))
        row.add(DocumentsContract.Document.COLUMN_SIZE, null)
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis())
    }

    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String? {
        val context = context ?: return null
        val actualParentDocumentId = when {
            parentDocumentId.isNullOrEmpty() || parentDocumentId == "null" -> ROOT_DOCUMENT_ID
            else -> parentDocumentId
        }
        
        val parentFile = when (actualParentDocumentId) {
            HISTORY_DOCUMENT_ID -> File(context.getExternalFilesDir(null), "history")
            EXPORT_DOCUMENT_ID -> File(context.getExternalFilesDir(null), "export")
            else -> {
                // 对于未知的父目录ID，抛出异常
                throw FileNotFoundException("Unknown parent document: $actualParentDocumentId")
            }
        }

        if (!parentFile.exists()) {
            parentFile.mkdirs()
        }

        val newFile = File(parentFile, displayName)
        newFile.createNewFile()
        return newFile.name
    }

    override fun deleteDocument(documentId: String) {
        val context = context ?: return
        val actualDocumentId = when {
            documentId.isNullOrEmpty() || documentId == "null" -> ROOT_DOCUMENT_ID
            else -> documentId
        }
        
        val file = when (actualDocumentId) {
            HISTORY_DOCUMENT_ID -> File(context.getExternalFilesDir(null), "history")
            EXPORT_DOCUMENT_ID -> File(context.getExternalFilesDir(null), "export")
            else -> {
                // 对于未知的documentId，抛出异常
                throw FileNotFoundException("Unknown document: $actualDocumentId")
            }
        }
        file.deleteRecursively()
    }
}