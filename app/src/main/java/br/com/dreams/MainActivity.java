package br.com.dreams;

import android.app.Activity;
import android.content.Intent;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import java.io.OutputStream;
import android.view.HapticFeedbackConstants;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final int REQ_FILE = 11;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private Uri sharedUri;
    private String sharedName;
    private Uri saveUri;
    private OutputStream saveOut;
    private boolean failed;

    /** Ponte JS -> Android: vibração fina e PDF recebido de outros apps */
    public class Bridge {
        @JavascriptInterface public void haptic(String kind) {
            final int c;
            if ("ok".equals(kind) && Build.VERSION.SDK_INT >= 30) c = HapticFeedbackConstants.CONFIRM;
            else if ("err".equals(kind) && Build.VERSION.SDK_INT >= 30) c = HapticFeedbackConstants.REJECT;
            else if ("heavy".equals(kind)) c = HapticFeedbackConstants.LONG_PRESS;
            else c = HapticFeedbackConstants.CLOCK_TICK;
            web.post(() -> web.performHapticFeedback(c));
        }
        @JavascriptInterface public String sharedName() { return sharedName == null ? "" : sharedName; }
        @JavascriptInterface public boolean saveStart(String name, String mime) {
            try {
                saveEnd();
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, mime);
                v.put(MediaStore.Downloads.IS_PENDING, 1);
                saveUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (saveUri == null) return false;
                saveOut = getContentResolver().openOutputStream(saveUri);
                return saveOut != null;
            } catch (Exception e) { saveUri = null; saveOut = null; return false; }
        }
        @JavascriptInterface public void saveChunk(String b64) {
            try { if (saveOut != null) saveOut.write(Base64.decode(b64, Base64.DEFAULT)); } catch (Exception e) { failed = true; }
        }
        @JavascriptInterface public boolean saveEnd() {
            boolean ok = saveOut != null && !failed;
            try {
                if (saveOut != null) saveOut.close();
                if (saveUri != null) {
                    if (ok) {
                        ContentValues v = new ContentValues();
                        v.put(MediaStore.Downloads.IS_PENDING, 0);
                        getContentResolver().update(saveUri, v, null, null);
                    } else getContentResolver().delete(saveUri, null, null);
                }
            } catch (Exception e) { ok = false; }
            saveOut = null; saveUri = null; failed = false;
            return ok;
        }
        @JavascriptInterface public void clearShared() { sharedUri = null; sharedName = null; }
    }

    private void handleIntent(Intent i) {
        if (i == null) return;
        Uri u = null;
        if (Intent.ACTION_SEND.equals(i.getAction())) u = i.getParcelableExtra(Intent.EXTRA_STREAM);
        else if (Intent.ACTION_VIEW.equals(i.getAction())) u = i.getData();
        if (u == null) return;
        String n = "documento.pdf";
        try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int ix = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (ix >= 0 && c.getString(ix) != null) n = c.getString(ix);
            }
        } catch (Exception ignore) {}
        sharedUri = u; sharedName = n;
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handleIntent(i);
        if (web != null) web.evaluateJavascript("window.dreamsShared&&window.dreamsShared()", null);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        setContentView(web);
        web.addJavascriptInterface(new Bridge(), "AndroidApp");
        handleIntent(getIntent());
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // IndexedDB / localStorage (onde ficam os PDFs)
        s.setDatabaseEnabled(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(false);
        s.setTextZoom(100);
        // os arquivos do app (pasta assets) são servidos em https para o armazenamento do navegador funcionar
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                v.evaluateJavascript("window.dreamsShared&&window.dreamsShared()", null);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                String p = u.getPath();
                if ("/__shared.pdf".equals(p) && sharedUri != null) {
                    try {
                        return new WebResourceResponse("application/pdf", null, getContentResolver().openInputStream(sharedUri));
                    } catch (Exception e) {
                        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, new ByteArrayInputStream(new byte[0]));
                    }
                }
                if (p == null || p.equals("/")) p = "/index.html";
                try {
                    InputStream in = getAssets().open(p.substring(1));
                    return new WebResourceResponse(mime(p), "utf-8", in);
                } catch (IOException e) {
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, new ByteArrayInputStream(new byte[0]));
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                try {
                    startActivityForResult(p.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "Não foi possível abrir o seletor de arquivos", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        });
        web.loadUrl("https://" + HOST + "/index.html");
    }

    private static String mime(String p) {
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        // fecha o leitor ou a folha de cadastro antes de sair do app
        web.evaluateJavascript("(function(){try{return window.dreamsBack?window.dreamsBack():false}catch(e){return false}})()", v -> {
            if (!"true".equals(v)) super.onBackPressed();
        });
    }
}
