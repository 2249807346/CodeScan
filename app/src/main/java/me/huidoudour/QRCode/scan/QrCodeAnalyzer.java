package me.huidoudour.QRCode.scan;

import android.media.Image;
import android.graphics.Point;
import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

public class QrCodeAnalyzer implements ImageAnalysis.Analyzer {

    @FunctionalInterface
    public interface OnQrCodeScanned {
        void onScanned(String result);
    }

    private final OnQrCodeScanned onQrCodeScanned;
    private final BarcodeScanner scanner;
    private int imageWidth = 0;
    private int imageHeight = 0;
    private float scanFrameLeft = 0;
    private float scanFrameTop = 0;
    private float scanFrameRight = 0;
    private float scanFrameBottom = 0;
    private boolean frameBoundsCalculated = false;
    private float previewWidth = 0;
    private float previewHeight = 0;

    public QrCodeAnalyzer(OnQrCodeScanned onQrCodeScanned) {
        this.onQrCodeScanned = onQrCodeScanned;
        this.scanner = BarcodeScanning.getClient();
    }

    /**
     * 设置扫描框的边界（在预览坐标系中）
     * @param left 左边界
     * @param top 上边界
     * @param right 右边界
     * @param bottom 下边界
     */
    public void setScanFrameBounds(float left, float top, float right, float bottom) {
        this.scanFrameLeft = left;
        this.scanFrameTop = top;
        this.scanFrameRight = right;
        this.scanFrameBottom = bottom;
        this.frameBoundsCalculated = true;
    }

    /**
     * 设置相机图像的尺寸
     */
    public void setImageSize(int width, int height) {
        this.imageWidth = width;
        this.imageHeight = height;
    }

    /**
     * 设置预览视图的尺寸
     */
    public void setPreviewSize(float width, float height) {
        this.previewWidth = width;
        this.previewHeight = height;
    }

    /**
     * 检查条形码是否在扫描框范围内
     */
    private boolean isBarcodeInScanFrame(Barcode barcode) {
        if (!frameBoundsCalculated || imageWidth == 0 || imageHeight == 0 || previewWidth == 0 || previewHeight == 0) {
            return true; // 如果没有设置边界或氋寸，接受所有条形码
        }

        Point[] cornerPoints = barcode.getCornerPoints();
        if (cornerPoints == null || cornerPoints.length == 0) {
            return false;
        }

        // 计算预览坐标与图像坐标的比例
        float scaleX = previewWidth / imageWidth;
        float scaleY = previewHeight / imageHeight;

        // 检查条形码的所有角点是否都在扫描框内
        for (Point point : cornerPoints) {
            // 将图像坐标映射到预览坐标
            float previewX = point.x * scaleX;
            float previewY = point.y * scaleY;

            if (previewX < scanFrameLeft || previewX > scanFrameRight ||
                previewY < scanFrameTop || previewY > scanFrameBottom) {
                return false;
            }
        }
        return true;
    }

    @Override
    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    public void analyze(@NonNull ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            // 记录图像尺寸
            if (imageWidth == 0 || imageHeight == 0) {
                imageWidth = mediaImage.getWidth();
                imageHeight = mediaImage.getHeight();
            }

            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            scanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        if (!barcodes.isEmpty() && barcodes.get(0).getRawValue() != null) {
                            // 检查条形码是否在扫描框范围内
                            if (isBarcodeInScanFrame(barcodes.get(0))) {
                                onQrCodeScanned.onScanned(barcodes.get(0).getRawValue());
                            }
                        }
                    })
                    .addOnFailureListener(Exception::printStackTrace)
                    .addOnCompleteListener(task -> imageProxy.close());
        }
    }
}