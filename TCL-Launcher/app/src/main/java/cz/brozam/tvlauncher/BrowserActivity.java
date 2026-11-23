package cz.brozam.tvlauncher;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

public class BrowserActivity extends Activity {

    private static final int REQ_UPLOAD = 201;

    private FrameLayout root;
    private LinearLayout content;
    private WebView browser;

    // Video přes celou obrazovku
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;

    // Výběr souboru pro <input type="file"> (nahrávání)
    private ValueCallback<Uri[]> fileCallback;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8, 24, 44));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        Button refresh = new Button(this);
        refresh.setText("⟳ Obnovit stránku");
        refresh.setTextColor(Color.WHITE);
        refresh.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(
                Color.rgb(12, 76, 127)));
        refresh.setGravity(Gravity.CENTER);
        content.addView(refresh, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        browser = new WebView(this);
        browser.setBackgroundColor(Color.rgb(8, 24, 44));
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        browser.getSettings().setSupportZoom(true);
        browser.getSettings().setBuiltInZoomControls(true);
        browser.getSettings().setDisplayZoomControls(false);
        browser.getSettings().setMediaPlaybackRequiresUserGesture(false);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(browser, true);

        browser.setWebViewClient(new WebViewClient());
        browser.setWebChromeClient(new ChromeClient());

        content.addView(browser, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        refresh.setOnClickListener(v -> browser.reload());

        browser.setDownloadListener(this::download);

        String url = getIntent().getStringExtra("url");
        browser.loadUrl(url == null ? "https://www.google.cz/" : url);

        // Ovladač ovládá rovnou stránku, ne tlačítko Obnovit.
        browser.requestFocus();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    // ----- Stahování -----

    private void download(String url, String userAgent,
                          String contentDisposition, String mimeType,
                          long contentLength) {
        if (url == null
            || !(url.startsWith("https://") || url.startsWith("http://"))) {
            toast("Tento typ stahování prohlížeč neumí");
            return;
        }

        try {
            String fileName = URLUtil.guessFileName(
                url, contentDisposition, mimeType);

            DownloadManager.Request request =
                new DownloadManager.Request(Uri.parse(url));

            // Přihlášení ze stránky (cookies), jinak se ze stránek
            // s přihlášením stáhne jen chybová stránka.
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) {
                request.addRequestHeader("Cookie", cookie);
            }
            if (userAgent != null) {
                request.addRequestHeader("User-Agent", userAgent);
            }
            if (mimeType != null && !mimeType.isEmpty()) {
                request.setMimeType(mimeType);
            }

            request.setTitle(fileName);
            request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS, fileName);

            ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE))
                .enqueue(request);

            toast("Stahuji do složky Download: " + fileName);
        } catch (Exception e) {
            toast("Stažení se nezdařilo");
        }
    }

    // ----- Video přes celou obrazovku a výběr souboru -----

    private class ChromeClient extends WebChromeClient {

        @Override
        public void onShowCustomView(View view, CustomViewCallback cb) {
            if (customView != null) {
                cb.onCustomViewHidden();
                return;
            }
            customView = view;
            customCallback = cb;
            customView.setBackgroundColor(Color.BLACK);
            root.addView(customView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
            content.setVisibility(View.GONE);
            customView.requestFocus();
        }

        @Override
        public void onHideCustomView() {
            hideCustomView();
        }

        @Override
        public boolean onShowFileChooser(
            WebView view, ValueCallback<Uri[]> cb,
            FileChooserParams params) {

            if (fileCallback != null) {
                fileCallback.onReceiveValue(null);
            }
            fileCallback = cb;

            Intent i;
            try {
                i = params.createIntent();
            } catch (Exception e) {
                i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("*/*");
            }
            i.addCategory(Intent.CATEGORY_OPENABLE);
            if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }

            try {
                startActivityForResult(i, REQ_UPLOAD);
                return true;
            } catch (ActivityNotFoundException e) {
                fileCallback = null;
                toast("Výběr souborů není v TV dostupný");
                return false;
            }
        }
    }

    private void hideCustomView() {
        if (customView == null) {
            return;
        }
        root.removeView(customView);
        customView = null;
        content.setVisibility(View.VISIBLE);

        if (customCallback != null) {
            customCallback.onCustomViewHidden();
            customCallback = null;
        }
        browser.requestFocus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQ_UPLOAD || fileCallback == null) {
            return;
        }

        Uri[] result = null;

        if (resultCode == RESULT_OK && data != null) {
            ClipData clip = data.getClipData();
            if (clip != null && clip.getItemCount() > 0) {
                result = new Uri[clip.getItemCount()];
                for (int i = 0; i < clip.getItemCount(); i++) {
                    result[i] = clip.getItemAt(i).getUri();
                }
            } else if (data.getData() != null) {
                result = new Uri[] {data.getData()};
            }
        }

        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
        } else if (browser != null && browser.canGoBack()) {
            browser.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Uložit přihlášení na disk hned, ne až někdy později.
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
        if (browser != null) {
            browser.destroy();
        }
        super.onDestroy();
    }
}
