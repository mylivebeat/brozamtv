package cz.brozam.tvlauncher;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class BrowserActivity extends Activity {
    private WebView browser;
    @Override public void onCreate(Bundle b){super.onCreate(b);
        browser=new WebView(this); browser.getSettings().setJavaScriptEnabled(true);browser.getSettings().setDomStorageEnabled(true);
        browser.getSettings().setSupportZoom(true);browser.getSettings().setBuiltInZoomControls(true);browser.getSettings().setDisplayZoomControls(false);
        browser.setWebViewClient(new WebViewClient());browser.setWebChromeClient(new WebChromeClient());setContentView(browser);
        String url=getIntent().getStringExtra("url");browser.loadUrl(url==null?"https://www.google.com/":url);
        browser.setDownloadListener((url0,userAgent,contentDisposition,mimeType,contentLength)->{
            try { DownloadManager.Request request=new DownloadManager.Request(Uri.parse(url0));request.addRequestHeader("User-Agent",userAgent);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,android.webkit.URLUtil.guessFileName(url0,contentDisposition,mimeType));
                ((DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(request);
                Toast.makeText(this,"Stahuji do složky Download",Toast.LENGTH_LONG).show();
            }catch(Exception e){Toast.makeText(this,"Stažení se nezdařilo",Toast.LENGTH_LONG).show();}
        });
    }
    @Override public void onBackPressed(){if(browser.canGoBack())browser.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){if(browser!=null)browser.destroy();super.onDestroy();}
}
