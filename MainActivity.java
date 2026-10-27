
package com.example.drive;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.os.CancellationSignal;
import android.webkit.*;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.util.Base64;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PageRange;

import java.io.*;

public class MainActivity extends Activity {
    private WebView webView;
    private FrameLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(20,43,74));
        getWindow().setNavigationBarColor(Color.rgb(20,43,74));

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
        w.setWebChromeClient(new WebChromeClient());
        return w;
    }

    @JavascriptInterface
    public void savePdf(String html, String filename) {
        runOnUiThread(() -> {
            try {
                if (Build.VERSION.SDK_INT < 21) {
                    Toast.makeText(this,
                        "PDF requires Android 5 or newer",
                        Toast.LENGTH_LONG).show();
                    return;
                }

                String safeName = filename.replaceAll(
                    "[^a-zA-Z0-9._-]", "_"
                );
                if (!safeName.toLowerCase().endsWith(".pdf")) {
                    safeName += ".pdf";
                }

                WebView pdfView = createWebView();
                root.addView(pdfView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ));
                webView.bringToFront();

                File temp = new File(getCacheDir(), safeName);

                pdfView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        PrintDocumentAdapter adapter =
                            view.createPrintDocumentAdapter(safeName);

                        PrintAttributes attrs =
                            new PrintAttributes.Builder()
                                .setMediaSize(
                                    PrintAttributes.MediaSize.ISO_A4)
                                .setResolution(new PrintAttributes.Resolution(
                                    "pdf", "PDF", 300, 300))
                                .setMinMargins(
                                    PrintAttributes.Margins.NO_MARGINS)
                                .build();

                        adapter.onLayout(null, attrs,
                            new CancellationSignal(),
                            new PrintDocumentAdapter.LayoutResultCallback() {
                                @Override
                                public void onLayoutFinished(
                                    PrintDocumentInfo info,
                                    boolean changed) {
                                    try {
                                        ParcelFileDescriptor pfd =
                                            ParcelFileDescriptor.open(temp,
                                                ParcelFileDescriptor.MODE_CREATE |
                                                ParcelFileDescriptor.MODE_TRUNCATE |
                                                ParcelFileDescriptor.MODE_READ_WRITE);

                                        adapter.onWrite(
                                            new PageRange[]{PageRange.ALL_PAGES},
                                            pfd,
                                            new CancellationSignal(),
                                            new PrintDocumentAdapter.WriteResultCallback() {
                                                @Override
                                                public void onWriteFinished(
                                                    PageRange[] pages) {
                                                    try {
                                                        savePdfFile(temp, safeName);
                                                    } catch (Exception e) {
                                                        showMessage(
                                                            "PDF save failed: " +
                                                            e.getMessage());
                                                    }
                                                    root.removeView(pdfView);
                                                    pdfView.destroy();
                                                }

                                                @Override
                                                public void onWriteFailed(
                                                    CharSequence error) {
                                                    showMessage(
                                                        "PDF creation failed: " +
                                                        error);
                                                    root.removeView(pdfView);
                                                    pdfView.destroy();
                                                }
                                            });
                                    } catch (Exception e) {
                                        showMessage(
                                            "PDF error: " + e.getMessage());
                                        root.removeView(pdfView);
                                        pdfView.destroy();
                                    }
                                }

                                @Override
                                public void onLayoutFailed(CharSequence error) {
                                    showMessage("PDF layout failed: " + error);
                                    root.removeView(pdfView);
                                    pdfView.destroy();
                                }
                            }, new Bundle());
                    }
                });

                pdfView.loadDataWithBaseURL(
                    "file:///android_asset/", html,
                    "text/html", "UTF-8", null);

            } catch (Exception e) {
                showMessage("PDF error: " + e.getMessage());
            }
        });
    }

    private void savePdfFile(File temp, String filename)
        throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS);

            Uri uri = getContentResolver().insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Cannot create PDF");

            try (InputStream in = new FileInputStream(temp);
                 OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IOException("Cannot open Downloads");
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            }
        } else {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) throw new IOException("Downloads unavailable");
            if (!dir.exists()) dir.mkdirs();
            try (InputStream in = new FileInputStream(temp);
                 OutputStream out = new FileOutputStream(
                     new File(dir, filename))) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            }
        }

        temp.delete();
        showMessage("PDF saved in Downloads: " + filename);
    }

    private void showMessage(String message) {
        runOnUiThread(() -> Toast.makeText(
            this, message, Toast.LENGTH_LONG).show());
    }

    @JavascriptInterface
    public void saveCsv(String base64Data, String filename) {
        try {
            byte[] data = Base64.decode(base64Data, Base64.DEFAULT);
            String safeName = filename.replaceAll(
                "[^a-zA-Z0-9._-]", "_");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, safeName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS);

                Uri uri = getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IOException("Cannot create CSV");

                try (OutputStream out =
                    getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("Cannot open CSV");
                    out.write(data);
                }
            } else {
                File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) throw new IOException("Downloads unavailable");
                if (!dir.exists()) dir.mkdirs();
                try (OutputStream out = new FileOutputStream(
                    new File(dir, safeName))) {
                    out.write(data);
                }
            }
            showMessage("CSV saved in Downloads: " + safeName);
        } catch (Exception e) {
            showMessage("CSV save failed: " + e.getMessage());
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
