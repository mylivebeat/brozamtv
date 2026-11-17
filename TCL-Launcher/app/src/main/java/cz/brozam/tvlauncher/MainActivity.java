package cz.brozam.tvlauncher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

public class MainActivity extends Activity {
    private WebView ui;
    private final String[][] apps = {
       {"oneplay", "cz.o2.oneplay", "cz.o2.o2tv"},
       {"netflix", "com.netflix.ninja", "com.netflix.mediaclient"},
       {"youtube", "com.google.android.youtube.tv", "com.google.android.youtube"},
       {"kodi", "org.xbmc.kodi"},
       {"ct", "cz.ceskatelevize.ivysilani", "cz.ceskatelevize.androidtv"},
       {"spotify", "com.spotify.tv.android", "com.spotify.music"},
       {"localsend", "org.localsend.localsend_app"}
    };
    @Override public void onCreate(Bundle b) { super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
        ui=new WebView(this); ui.getSettings().setJavaScriptEnabled(true);
        ui.getSettings().setDomStorageEnabled(true); ui.getSettings().setAllowFileAccess(true);
        ui.setWebViewClient(new WebViewClient()); ui.addJavascriptInterface(new Bridge(), "TV");
        setContentView(ui); ui.loadUrl("file:///android_asset/index.html");
        fetchWeather();
    }
    private void fetchWeather() {
        new Thread(() -> { try {
            String url="https://api.open-meteo.com/v1/forecast?latitude=48.98&longitude=17.47&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=Europe%2FPrague&forecast_days=3";
            HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setConnectTimeout(8000); c.setReadTimeout(8000);
            InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[4096]; int n;
            while((n=in.read(buf))!=-1) out.write(buf,0,n); in.close(); c.disconnect();
            String json=out.toString("UTF-8"); new JSONObject(json);
            runOnUiThread(()->ui.evaluateJavascript("window.setWeather(" + json + ")",null));
        } catch(Exception ignored) {runOnUiThread(()->ui.evaluateJavascript("window.weatherError()",null));} }).start();
    }
    private void startApp(String key) {
        for(String[] entry:apps) if(entry[0].equals(key)) {
            for(int i=1;i<entry.length;i++) {Intent launch=getPackageManager().getLaunchIntentForPackage(entry[i]); if(launch!=null) {startActivity(launch);return;} }
            Toast.makeText(this,"Aplikace není nainstalovaná: " + key,Toast.LENGTH_LONG).show();return;
        }
    }
    public class Bridge {
        @JavascriptInterface public void app(String key) {runOnUiThread(()->startApp(key));}
        @JavascriptInterface public void browse(String url) {runOnUiThread(()->{Intent i=new Intent(MainActivity.this,BrowserActivity.class);i.putExtra("url",url);startActivity(i);});}
        @JavascriptInterface public void files() {runOnUiThread(()->{ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,101); });}
        @JavascriptInterface public void share(String kind) {runOnUiThread(()->{
            if(kind.equals("localsend")){startApp("localsend");return;}
            if(kind.equals("drive")){ Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://drive.google.com/drive/u/0/my-drive"));startActivity(i);return;}
        });}
        @JavascriptInterface public void settings() {runOnUiThread(()->startActivity(new Intent(Settings.ACTION_SETTINGS)));}
        @JavascriptInterface public void apps() {runOnUiThread(()->startActivity(new Intent(Settings.ACTION_APPLICATION_SETTINGS)));}
        @JavascriptInterface public void hdmi() {runOnUiThread(()->Toast.makeText(MainActivity.this,"Přepni na HDMI tlačítkem Input na ovladači TCL",Toast.LENGTH_LONG).show()));}
        @JavascriptInterface public void mirroring() {runOnUiThread(()->Toast.makeText(MainActivity.this,"Zrcadlení otevři v nabídce vstupů televize TCL",Toast.LENGTH_LONG).show()));}
        @JavascriptInterface public void weather() {fetchWeather();}
    }
}
