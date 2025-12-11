package me.huidoudour.QRCode.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch
import me.huidoudour.QRCode.scan.databinding.FragmentScannerBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerFragment : Fragment() {

    private var _binding: FragmentScannerBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var db: AppDatabase
    private var isScanning = true

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), getString(R.string.camera_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraExecutor = Executors.newSingleThreadExecutor()
        db = AppDatabase.getDatabase(requireContext())

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(binding.previewView.surfaceProvider)

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val qrCodeAnalyzer = QrCodeAnalyzer { result, codeType ->
                if (isScanning) {
                    isScanning = false
                    requireActivity().runOnUiThread {
                        showConfirmationDialog(result, codeType)
                    }
                }
            }

            // 计算扫描框的坐标
            // 由于 ML Kit 的条形码坐标已经在图像坐标系中，
            // 我们需要根据预览和图像尺寸的比例来设置扫描框边界
            binding.previewView.post {
                val previewWidth = binding.previewView.width
                val previewHeight = binding.previewView.height
                val scanFrameSize = 280 // 扫描框大小（dp）
                val scanFrameSizePx = (scanFrameSize * requireContext().resources.displayMetrics.density).toInt()

                // 计算扫描框在预览视图中的位置（中央）
                val previewLeft = (previewWidth - scanFrameSizePx) / 2f
                val previewTop = (previewHeight - scanFrameSizePx) / 2f
                val previewRight = previewLeft + scanFrameSizePx
                val previewBottom = previewTop + scanFrameSizePx

                // 设置预览坐标的扫描框边界
                qrCodeAnalyzer.setScanFrameBounds(previewLeft, previewTop, previewRight, previewBottom)
                // 设置预览视图大小
                qrCodeAnalyzer.setPreviewSize(previewWidth.toFloat(), previewHeight.toFloat())
            }

            imageAnalysis.setAnalyzer(cameraExecutor, qrCodeAnalyzer)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun showConfirmationDialog(result: String, codeType: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_scan_result, null)
        val textInputLayout = dialogView.findViewById<TextInputLayout>(R.id.textInputLayout)
        val remarkEditText = dialogView.findViewById<TextInputEditText>(R.id.remarkEditText)
        
        textInputLayout.hint = getString(R.string.hint_remark_optional)
        
        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_CodeScan_Dialog)
            .setTitle(getString(R.string.dialog_title_scan_result))
            .setMessage(result)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.button_save)) { dialog, _ ->
                val remark = remarkEditText.text.toString()
                lifecycleScope.launch {
                    db.scanResultDao().insert(ScanResult(content = result, remark = remark, codeType = codeType, timestamp = System.currentTimeMillis()))
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), getString(R.string.toast_saved), Toast.LENGTH_SHORT).show()
                    }
                }
                isScanning = true
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.button_rescan)) { dialog, _ ->
                isScanning = true
                dialog.dismiss()
            }
            .setBackgroundInsetStart(32)
            .setBackgroundInsetEnd(32)
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        cameraExecutor.shutdown()
    }
}