package io.github.mikewa1981hub.minifigscanner;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import org.json.JSONObject;

/**
 * Shows the scanner page from the app's assets. The page's scan button calls
 * {@link Bridge#scan()}, which opens the phone's Google code scanner and hands
 * the decoded text back to the page.
 */
public class MainActivity extends Activity {

    private WebView web;
    private GmsBarcodeScanner scanner;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Only Data Matrix: the EAN barcode next to it is the same for every figure.
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_DATA_MATRIX)
                .enableAutoZoom()
                .build();
        scanner = GmsBarcodeScanning.getClient(this, options);

        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    private void startScan() {
        scanner.startScan()
                .addOnSuccessListener(barcode -> call("onNativeScan", barcode.getRawValue()))
                .addOnFailureListener(e -> call("onNativeScanError", e.getMessage()));
    }

    private void call(String function, String value) {
        String arg = JSONObject.quote(value == null ? "" : value);
        web.evaluateJavascript("window." + function + " && window." + function + "(" + arg + ")", null);
    }

    private class Bridge {
        @JavascriptInterface
        public void scan() {
            runOnUiThread(MainActivity.this::startScan);
        }
    }
}
