package me.huidoudour.QRCode.scan
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.huidoudour.QRCode.scan.databinding.ActivityScannerLayoutBinding

@OptIn(ExperimentalGetImage::class)
class NewScannerActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityScannerLayoutBinding
    private lateinit var previewView: PreviewView
    private lateinit var toolbar: MaterialToolbar
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentCamera: androidx.camera.core.Camera? = null
    private var isFlashOn = false
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "需要相机权限才能使用扫描功能", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScannerLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置状态栏文字颜色适配
        updateStatusBarStyle()
        
        previewView = binding.previewView
        toolbar = binding.toolbar
        
        // 设置Toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.scanner_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
         return when (item.itemId) {
             android.R.id.home -> {
                 finish()
                 true
             }
             R.id.action_flash -> {
                 toggleFlashlight()
                 true
             }
             R.id.action_gallery -> {
                 openGallery()
                 true
             }
             else -> super.onOptionsItemSelected(item)
         }
     }
    
    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }
    
    @OptIn(ExperimentalGetImage::class)
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        
        val preview = Preview.Builder().build()
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_ALL_FORMATS)
            .build()
        
        val scanner = BarcodeScanning.getClient(options)
        
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
            val image = imageProxy.image
            if (image != null) {
                val visionImage = com.google.mlkit.vision.common.InputImage.fromMediaImage(
                    image, imageProxy.imageInfo.rotationDegrees
                )
                
                scanner.process(visionImage)
                    .addOnSuccessListener { barcodes ->
                        if (barcodes.isNotEmpty()) {
                            handleScanResult(barcodes[0])
                        }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
        
        try {
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalysis
            )
            
            preview.setSurfaceProvider(previewView.surfaceProvider)
            
            // 保存相机实例用于闪光灯控制
            currentCamera = camera
            
            // 设置闪光灯控制
            toolbar.menu.findItem(R.id.action_flash)?.isEnabled = camera.cameraInfo.hasFlashUnit()
            
        } catch (e: Exception) {
            Log.e(TAG, "Binding use cases failed", e)
        }
    }
    
    private fun handleScanResult(barcode: Barcode) {
        val content = barcode.rawValue ?: return
        val codeType = when (barcode.format) {
            Barcode.FORMAT_QR_CODE -> "QR_CODE"
            else -> "UNKNOWN"
        }
        
        lifecycleScope.launch(Dispatchers.Main) {
            if (barcode.format == Barcode.FORMAT_QR_CODE && isUrl(content)) {
                // 如果是URL，直接打开浏览器
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(content))
                startActivity(intent)
            } else {
                // 保存扫描结果
                saveScanResult(content, codeType)
                Toast.makeText(this@NewScannerActivity, "扫描结果已保存", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun isUrl(text: String): Boolean {
        return text.startsWith("http://") || text.startsWith("https://") || 
               text.startsWith("www.")
    }
    
    private fun saveScanResult(content: String, codeType: String) {
        val scanResult = ScanResult(
            content = content,
            remark = "",
            codeType = codeType
        )
        
        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.getDatabase(this@NewScannerActivity).scanResultDao().insert(scanResult)
        }
    }
    
    private fun toggleFlashlight() {
        val camera = currentCamera ?: return
        
        try {
            if (camera.cameraInfo.hasFlashUnit()) {
                camera.cameraControl.enableTorch(!isFlashOn)
                isFlashOn = !isFlashOn
                
                val message = if (isFlashOn) "闪光灯已开启" else "闪光灯已关闭"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                
                // 使用相同的图标，通过颜色或状态来区分
                toolbar.menu.findItem(R.id.action_flash)?.setIcon(R.drawable.ic_flash_on)
            } else {
                Toast.makeText(this, "设备不支持闪光灯", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Flash toggle failed", e)
        }
    }
    
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        galleryLauncher.launch(intent)
    }
    
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                processImageFromGallery(uri)
            }
        }
    }
    
    private fun processImageFromGallery(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (bitmap != null) {
                analyzeImageForBarcode(bitmap)
            } else {
                Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image from gallery", e)
            Toast.makeText(this, "处理图片时出错", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun analyzeImageForBarcode(bitmap: android.graphics.Bitmap) {
        val visionImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_ALL_FORMATS)
            .build()
        val scanner = BarcodeScanning.getClient(options)
        
        scanner.process(visionImage)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    handleScanResult(barcodes[0])
                } else {
                    Toast.makeText(this, "未检测到二维码", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Barcode analysis failed", e)
                Toast.makeText(this, "二维码识别失败", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun updateStatusBarStyle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val isDarkMode = isDarkMode()
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = !isDarkMode
            insetsController.isAppearanceLightNavigationBars = !isDarkMode
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (isDarkMode()) {
                // 暗色模式：使用浅色文字
                0
            } else {
                // 浅色模式：使用深色文字
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }
    
    private fun isDarkMode(): Boolean {
        return (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
    }
    
    companion object {
        private const val TAG = "NewScannerActivity"
    }
}