
package com.example.drive;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.util.Base64;
import android.content.ContentValues;
import android.provider.MediaStore;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends Activity {
    private WebView webView;
    private FrameLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(20, 43, 74));
        getWindow().setNavigationBarColor(Color.rgb(20, 43, 74));

        root = new FrameLayout(this);
        webView = createWebView();

        root.addView(webView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        setContentView(root);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private WebView createWebView() {
        WebView w = new WebView(this);
        WebSettings s = w.getSettings();

        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setSupportMultipleWindows(true);

        w.addJavascriptInterface(this, "Android");
        w.setWebViewClient(new WebViewClient());

        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(
                WebView view,
                boolean isDialog,
                boolean isUserGesture,
                android.os.Message resultMsg
            ) {
                WebView popup = createWebView();
                WebView.WebViewTransport transport =
                    (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }
        });

        return w;
    }

    @JavascriptInterface
    public void saveCsv(String base64Data, String filename) {
        try {
            byte[] data = Base64.decode(base64Data, Base64.DEFAULT);

            String safeName = filename.replaceAll(
                "[^a-zA-Z0-9._-]", "_"
            );

            if (!safeName.toLowerCase().endsWith(".csv")) {
                safeName += ".csv";
            }

            OutputStream output;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, safeName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
                values.put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS
                );

                android.net.Uri uri = getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                );

                if (uri == null) {
                    throw new IOException("Could not create CSV file");
                }

                output = getContentResolver().openOutputStream(uri);
                if (output == null) {
                    throw new IOException("Could not open CSV file");
                }
            } else {
                File directory = getExternalFilesDir(
                    Environment.DIRECTORY_DOWNLOADS
                );
                if (directory == null) {
                    throw new IOException("Downloads folder unavailable");
                }
                if (!directory.exists()) directory.mkdirs();

                output = new FileOutputStream(
                    new File(directory, safeName)
                );
            }

            try (OutputStream stream = output) {
                stream.write(data);
                stream.flush();
            }

            runOnUiThread(() -> Toast.makeText(
                this,
                "CSV saved: Downloads/" + safeName,
                Toast.LENGTH_LONG
            ).show());

        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(
                this,
                "CSV download failed: " + e.getMessage(),
                Toast.LENGTH_LONG
            ).show());
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
