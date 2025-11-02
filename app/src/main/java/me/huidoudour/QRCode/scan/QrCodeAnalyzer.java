package me.huidoudour.QRCode.scan;

import android.media.Image;
import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
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

    public QrCodeAnalyzer(OnQrCodeScanned onQrCodeScanned) {
        this.onQrCodeScanned = onQrCodeScanned;
        this.scanner = BarcodeScanning.getClient();
    }

    @Override
    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    public void analyze(@NonNull ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            scanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        if (!barcodes.isEmpty() && barcodes.get(0).getRawValue() != null) {
                            onQrCodeScanned.onScanned(barcodes.get(0).getRawValue());
                        }
                    })
                    .addOnFailureListener(Exception::printStackTrace)
                    .addOnCompleteListener(task -> imageProxy.close());
        }
    }
}