package me.huidoudour.QRCode.scan

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_results")
data class ScanResult(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val content: String,
    val remark: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)