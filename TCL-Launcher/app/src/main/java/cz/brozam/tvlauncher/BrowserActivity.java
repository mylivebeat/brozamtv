package cz.brozam.tvlauncher;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class BrowserActivity extends Activity {
    private WebView browser;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.rgb(8, 24, 44));

        Button refresh = new Button(this);
        refresh.setText("⟳ Obnovit stránku");
        refresh.setTextColor(Color.WHITE);
        refresh.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(12, 76, 127)));
        LinearLayout.LayoutParams controls = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        refresh.setGravity(Gravity.CENTER);
        layout.addView(refresh, controls);

        browser = new WebView(this);
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        browser.getSettings().setSupportZoom(true);
        browser.getSettings().setBuiltInZoomControls(true);
        browser.getSettings().setDisplayZoomControls(false);
        browser.setWebViewClient(new WebViewClient());
        browser.setWebChromeClient(new WebChromeClient());
        layout.addView(browser, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(layout);
        refresh.setOnClickListener(v -> browser.reload());

        String url = getIntent().getStringExtra("url");
        browser.loadUrl(url == null ? "https://www.google.com/" : url);
        browser.setDownloadListener((downloadUrl, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
                if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                    android.webkit.URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType));
                ((DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(request);
                Toast.makeText(this, "Stahuji do složky Download", Toast.LENGTH_LONG).show();
            } catch(Exception e) {
                Toast.makeText(this, "Stažení se nezdařilo", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override public void onBackPressed() {
        if(browser != null && browser.canGoBack()) browser.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if(browser != null) browser.destroy();
        super.onDestroy();
    }
}
