package me.huidoudour.QRCode.scan

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import me.huidoudour.QRCode.scan.databinding.ActivityHistoryBinding

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)

        binding.historyRecyclerView.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            val scanResults = db.scanResultDao().getAll()
            binding.historyRecyclerView.adapter = HistoryAdapter(scanResults)
        }
    }
}