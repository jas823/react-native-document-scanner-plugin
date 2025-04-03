package com.reactnativedocumentscanner;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.util.Base64;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.module.annotations.ReactModule;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult.Page;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Objects;

@ReactModule(name = DocumentScannerModule.NAME)
public class DocumentScannerModule extends ReactContextBaseJavaModule {
    public static final String NAME = "DocumentScanner";

    public DocumentScannerModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    @NonNull
    public String getName() {
        return NAME;
    }

    private Bitmap convertToGrayscale(Bitmap original) {
        Bitmap bwBitmap = Bitmap.createBitmap(original.getWidth(), original.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bwBitmap);
        Paint paint = new Paint();
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
        paint.setColorFilter(filter);
        canvas.drawBitmap(original, 0, 0, paint);
        return bwBitmap;
    }

    private Bitmap convertToBlackAndWhite(Bitmap original) {
        Bitmap bwBitmap = Bitmap.createBitmap(original.getWidth(), original.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bwBitmap);
        Paint paint = new Paint();

        // Convert the image to grayscale first
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);  // Converts to grayscale
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
        paint.setColorFilter(filter);
        canvas.drawBitmap(original, 0, 0, paint);

        // Now, threshold the grayscale image to convert to black and white (binary)
        int width = bwBitmap.getWidth();
        int height = bwBitmap.getHeight();
        int threshold = 128; // The threshold value can be adjusted (0-255)

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixelColor = bwBitmap.getPixel(x, y);

                // Get the brightness of the pixel (luminosity)
                int r = Color.red(pixelColor);
                int g = Color.green(pixelColor);
                int b = Color.blue(pixelColor);
                int brightness = (r + g + b) / 3;

                // Apply threshold: if brightness is less than the threshold, make it black, else white
                int newColor = brightness < threshold ? Color.BLACK : Color.WHITE;

                bwBitmap.setPixel(x, y, newColor);
            }
        }

        return bwBitmap;
    }

    public String getImageInBase64(Activity currentActivity, Uri croppedImageUri, int quality) throws FileNotFoundException {
        Bitmap bitmap = convertToGrayscale(BitmapFactory.decodeStream(
            currentActivity.getContentResolver().openInputStream(croppedImageUri)
        ));
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

    @ReactMethod
    public void scanDocument(ReadableMap options, Promise promise) {
        Activity currentActivity = getCurrentActivity();
        WritableMap response = new WritableNativeMap();

        GmsDocumentScannerOptions.Builder documentScannerOptionsBuilder = new GmsDocumentScannerOptions.Builder()
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL);

        if (options.hasKey("maxNumDocuments")) {
            documentScannerOptionsBuilder.setPageLimit(
                options.getInt("maxNumDocuments")
            );
        }

        int croppedImageQuality;
        if (options.hasKey("croppedImageQuality")) {
            croppedImageQuality = options.getInt("croppedImageQuality");
        } else {
            croppedImageQuality = 100;
        }

        GmsDocumentScanner scanner = GmsDocumentScanning.getClient(documentScannerOptionsBuilder.build());
        ActivityResultLauncher<IntentSenderRequest> scannerLauncher = ((ComponentActivity) currentActivity).getActivityResultRegistry().register(
                "document-scanner",
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        GmsDocumentScanningResult documentScanningResult = GmsDocumentScanningResult.fromActivityResultIntent(
                            result.getData()
                        );
                        WritableArray docScanResults = new WritableNativeArray();

                        if (documentScanningResult != null) {
                            List<Page> pages = documentScanningResult.getPages();
                            if (pages != null) {
                                for (Page page : pages) {
                                    Uri croppedImageUri = page.getImageUri();
                                    String croppedImageResults = croppedImageUri.toString();

                                    if (options.hasKey("responseType") && Objects.equals(options.getString("responseType"), "base64")) {
                                        try {
                                            croppedImageResults = this.getImageInBase64(currentActivity, croppedImageUri, croppedImageQuality);
                                        } catch (FileNotFoundException error) {
                                            promise.reject("document scan error", error.getMessage());
                                        }
                                    }

                                    docScanResults.pushString(croppedImageResults);
                                }
                            }
                        }

                        response.putArray(
                            "scannedImages",
                            docScanResults
                        );
                        response.putString("status", "success");
                        promise.resolve(response);
                    } else if (result.getResultCode() == Activity.RESULT_CANCELED) {
                        // when user cancels document scan
                        response.putString("status", "cancel");
                        promise.resolve(response);
                    }
                }
        );

        scanner.getStartScanIntent(currentActivity)
            .addOnSuccessListener(intentSender ->
                scannerLauncher.launch(new IntentSenderRequest.Builder(intentSender).build()))
            .addOnFailureListener(error -> {
                // document scan error
                promise.reject("document scan error", error.getMessage());
            });
    }
}
