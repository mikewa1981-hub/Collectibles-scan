package io.github.mikewa1981hub.minifigscanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.Image;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Size;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.annotation.OptIn;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Camera preview on top, scanner page below. Every camera frame is checked for a
 * Data Matrix code; whenever a different code shows up it is handed to the page,
 * which swaps the figure shown. No button press needed.
 */
public class MainActivity extends ComponentActivity {

    private static final int REQ_CAMERA = 1;
    private static final long SAME_CODE_REPEAT_MS = 3000;

    private WebView web;
    private PreviewView preview;
    private TextView status;
    private Camera camera;
    private boolean torchOn = false;
    private boolean useFront = false;
    private TextView torchBtn;
    private TextView flipBtn;
    private BarcodeScanner scanner;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();

    private String lastCode = "";
    private long lastSeenAt = 0;

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        float dp = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        // --- camera area with an aiming frame
        FrameLayout camBox = new FrameLayout(this);
        preview = new PreviewView(this);
        preview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        camBox.addView(preview, new FrameLayout.LayoutParams(-1, -1));

        android.view.View frame = new android.view.View(this);
        GradientDrawable border = new GradientDrawable();
        border.setStroke((int) (3 * dp), Color.parseColor("#FFD21F"));
        border.setCornerRadius(12 * dp);
        border.setColor(Color.TRANSPARENT);
        frame.setBackground(border);
        int side = (int) (170 * dp);
        camBox.addView(frame, new FrameLayout.LayoutParams(side, side, Gravity.CENTER));

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(13);
        status.setShadowLayer(4, 0, 0, Color.BLACK);
        status.setText("Code in den Rahmen halten");
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        sp.bottomMargin = (int) (8 * dp);
        camBox.addView(status, sp);

        // --- buttons along the right edge of the camera image
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        torchBtn = makeButton("Licht", dp);
        torchBtn.setOnClickListener(v -> toggleTorch());
        flipBtn = makeButton("Selfie", dp);
        flipBtn.setOnClickListener(v -> switchCamera());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams((int) (64 * dp), (int) (64 * dp));
        buttons.addView(torchBtn, bp);
        LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams((int) (64 * dp), (int) (64 * dp));
        bp2.topMargin = (int) (12 * dp);
        buttons.addView(flipBtn, bp2);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL);
        blp.rightMargin = (int) (10 * dp);
        camBox.addView(buttons, blp);
        root.addView(camBox, new LinearLayout.LayoutParams(-1, 0, 4f));

        // --- result page
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        web.addJavascriptInterface(new Object(), "Android");
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 6f));

        setContentView(root);
        web.loadUrl("file:///android_asset/index.html");

        scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_DATA_MATRIX)
                .build());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_CAMERA) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            status.setText("Ohne Kamerazugriff kein Scan. In den App-Einstellungen erlauben.");
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview p = new Preview.Builder().build();
                p.setSurfaceProvider(preview.getSurfaceProvider());

                ResolutionSelector res = new ResolutionSelector.Builder()
                        .setResolutionStrategy(new ResolutionStrategy(new Size(1920, 1080),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build();
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setResolutionSelector(res)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(analysisExecutor, this::analyze);

                provider.unbindAll();
                CameraSelector selector = useFront ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
                if (!provider.hasCamera(selector)) {
                    useFront = !useFront;
                    selector = useFront ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
                }
                camera = provider.bindToLifecycle(this, selector, p, analysis);
                torchOn = false;
                updateButtons();

                // The codes are tiny: a little zoom lets the phone stay far enough away to focus.
                ZoomState z = camera.getCameraInfo().getZoomState().getValue();
                float max = z != null ? z.getMaxZoomRatio() : 1f;
                camera.getCameraControl().setZoomRatio(Math.min(2f, max));
            } catch (Exception e) {
                status.setText("Kamera konnte nicht gestartet werden.");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyze(ImageProxy proxy) {
        Image img = proxy.getImage();
        if (img == null) {
            proxy.close();
            return;
        }
        InputImage input = InputImage.fromMediaImage(img, proxy.getImageInfo().getRotationDegrees());
        scanner.process(input)
                .addOnSuccessListener(this::onCodes)
                .addOnCompleteListener(t -> proxy.close());
    }

    private void onCodes(List<Barcode> codes) {
        if (codes.isEmpty()) return;
        String value = codes.get(0).getRawValue();
        if (value == null || value.isEmpty()) return;
        long now = SystemClock.elapsedRealtime();
        boolean changed = !value.equals(lastCode);
        boolean reappeared = now - lastSeenAt > SAME_CODE_REPEAT_MS;
        lastSeenAt = now;
        if (!changed && !reappeared) return;
        lastCode = value;
        runOnUiThread(() -> web.evaluateJavascript(
                "window.onNativeScan && window.onNativeScan(" + JSONObject.quote(value) + ")", null));
    }

    private void toggleTorch() {
        if (camera == null || !camera.getCameraInfo().hasFlashUnit()) return;
        torchOn = !torchOn;
        camera.getCameraControl().enableTorch(torchOn);
        updateButtons();
    }

    private void switchCamera() {
        if (camera == null) return;
        useFront = !useFront;
        startCamera();
    }

    private void updateButtons() {
        boolean hasFlash = camera != null && camera.getCameraInfo().hasFlashUnit();
        torchBtn.setEnabled(hasFlash);
        torchBtn.setAlpha(hasFlash ? 1f : 0.35f);
        torchBtn.setText(torchOn ? "Licht\naus" : "Licht");
        styleButton(torchBtn, torchOn);
        flipBtn.setText(useFront ? "Haupt-\nkamera" : "Selfie");
    }

    private TextView makeButton(String label, float dp) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(12);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setClickable(true);
        b.setFocusable(true);
        b.setTag(dp);
        styleButton(b, false);
        return b;
    }

    private void styleButton(TextView b, boolean active) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(active ? Color.parseColor("#FFD21F") : Color.parseColor("#99000000"));
        bg.setStroke((int) (2 * (float) b.getTag()), Color.parseColor("#FFD21F"));
        b.setBackground(bg);
        b.setTextColor(active ? Color.parseColor("#1A1600") : Color.WHITE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        analysisExecutor.shutdown();
        scanner.close();
    }
}
