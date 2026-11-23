package cz.brozam.tvlauncher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {

    private static final int REQ_FILE = 101;

    private static final String HOME_PAGE =
        "file:///android_asset/index.html";

    // forecast_days=4: dnešek + 3 další dny (stránka ukazuje zítřek a dál)
    private static final String WEATHER_URL =
        "https://api.open-meteo.com/v1/forecast"
        + "?latitude=48.98"
        + "&longitude=17.47"
        + "&current=temperature_2m,weather_code"
        + "&daily=weather_code,"
        + "temperature_2m_max,"
        + "temperature_2m_min"
        + "&timezone=Europe%2FPrague"
        + "&forecast_days=4";

    private static final String DRIVE_URL =
        "https://drive.google.com/drive/u/0/my-drive";

    private static final long WEATHER_RETRY_MS = 60_000;
    private static final int WEATHER_MAX_RETRIES = 5;
    private static final long WEATHER_MAX_AGE_MS = 30 * 60_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean weatherLoading = new AtomicBoolean(false);

    private WebView ui;
    private boolean pageReady = false;
    private int weatherRetries = 0;
    private long lastWeatherOk = 0;
    private volatile String lastAppSignature = null;

    // Klíč dlaždice a možné názvy balíčků (první nalezený se použije).
    private final String[][] apps = {
        {"oneplay", "cz.o2.oneplay", "cz.o2.o2tv"},
        {"netflix", "com.netflix.ninja", "com.netflix.mediaclient"},
        {"youtube", "com.google.android.youtube.tv",
            "com.google.android.youtube"},
        {"kodi", "org.xbmc.kodi"},
        {"ct", "cz.ceskatelevize.ivysilani",
            "cz.ceskatelevize.androidtv", "cz.motion.ivysilani"},
        {"spotify", "com.spotify.tv.android",
            "com.spotify.music"},
        {"localsend", "org.localsend.localsend_app"}
    };

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        getWindow().getDecorView().setSystemUiVisibility(
            5894 | 1024 | 512
        );

        ui = new WebView(this);

        // Tmavé pozadí hned od startu (bez bílého bliknutí).
        ui.setBackgroundColor(Color.rgb(3, 17, 38));

        ui.getSettings().setJavaScriptEnabled(true);
        ui.getSettings().setDomStorageEnabled(true);

        ui.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (pageReady) {
                    return;
                }
                pageReady = true;
                fetchWeather();
                loadAppImages();
            }

            // Hlavní stránka má přístup k funkcím aplikace (objekt TV),
            // proto nesmí přejít na cizí web. Odkazy jdou do prohlížeče.
            @Override
            public boolean shouldOverrideUrlLoading(
                WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();

                if (url.startsWith("file:///android_asset/")) {
                    return false;
                }
                if (url.startsWith("https://")
                    || url.startsWith("http://")) {
                    openInBrowser(url);
                }
                return true;
            }

            // Když spadne vykreslování stránky, aplikace nespadne,
            // jen se obrazovka načte znovu.
            @Override
            public boolean onRenderProcessGone(
                WebView view, RenderProcessGoneDetail detail) {
                if (ui != null) {
                    ui.destroy();
                    ui = null;
                }
                recreate();
                return true;
            }
        });

        ui.addJavascriptInterface(new Bridge(), "TV");

        setContentView(ui);
        ui.requestFocus();

        ui.loadUrl(HOME_PAGE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!pageReady) {
            return;
        }

        // Po návratu z jiné aplikace: obnovit dlaždice (jen při změně)
        // a počasí, pokud je starší než 30 minut.
        loadAppImages();

        if (System.currentTimeMillis() - lastWeatherOk
            > WEATHER_MAX_AGE_MS) {
            fetchWeather();
        }
    }

    // Tlačítko Zpět: nejdřív zavřít otevřené okno ve stránce,
    // teprve potom odejít z aplikace.
    @Override
    public void onBackPressed() {
        if (ui == null || !pageReady) {
            super.onBackPressed();
            return;
        }

        ui.evaluateJavascript(
            "!!(window.handleBack && window.handleBack())",
            value -> {
                if (!"true".equals(value)) {
                    super.onBackPressed();
                }
            });
    }

    private void runJs(String js) {
        runOnUiThread(() -> {
            if (ui != null) {
                ui.evaluateJavascript(js, null);
            }
        });
    }

    private void toast(String msg) {
        runOnUiThread(() ->
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        );
    }

    private boolean safeStart(Intent i, String errorMsg) {
        try {
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            if (errorMsg != null) {
                toast(errorMsg);
            }
            return false;
        }
    }

    // ----- Počasí -----

    private void fetchWeather() {
        if (!weatherLoading.compareAndSet(false, true)) {
            return;
        }

        new Thread(() -> {
            HttpURLConnection c = null;
            boolean ok = false;

            try {
                c = (HttpURLConnection)
                    new URL(WEATHER_URL).openConnection();

                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);

                ByteArrayOutputStream out =
                    new ByteArrayOutputStream();

                try (InputStream in = c.getInputStream()) {
                    byte[] buf = new byte[4096];
                    int n;

                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                    }
                }

                String json = new JSONObject(
                    out.toString("UTF-8")
                ).toString();

                runJs("window.setWeather(" + json + ")");
                ok = true;

            } catch (Exception e) {
                runJs("window.weatherError()");

            } finally {
                if (c != null) {
                    c.disconnect();
                }
                weatherLoading.set(false);
            }

            final boolean success = ok;
            handler.post(() -> onWeatherDone(success));
        }).start();
    }

    // Po zapnutí TV často ještě není síť: zkusit znovu za minutu
    // (nejvýše 5×), pak už jen běžná obnova každých 30 minut.
    private void onWeatherDone(boolean success) {
        if (success) {
            weatherRetries = 0;
            lastWeatherOk = System.currentTimeMillis();
            handler.removeCallbacks(weatherRetry);
        } else if (weatherRetries < WEATHER_MAX_RETRIES) {
            weatherRetries++;
            handler.removeCallbacks(weatherRetry);
            handler.postDelayed(weatherRetry, WEATHER_RETRY_MS);
        }
    }

    private final Runnable weatherRetry = this::fetchWeather;

    // ----- Spouštění aplikací -----

    private Intent launchIntent(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);

        if (i == null) {
            i = pm.getLaunchIntentForPackage(pkg);
        }
        return i;
    }

    private String installedPackage(String[] entry) {
        for (int i = 1; i < entry.length; i++) {
            if (launchIntent(entry[i]) != null) {
                return entry[i];
            }
        }
        return null;
    }

    private void startApp(String key) {
        for (String[] entry : apps) {
            if (!entry[0].equals(key)) {
                continue;
            }

            String pkg = installedPackage(entry);

            if (pkg != null) {
                safeStart(
                    launchIntent(pkg),
                    "Aplikaci se nepodařilo spustit: " + key
                );
            } else {
                toast("Aplikace není nainstalovaná: " + key);
            }
            return;
        }
    }

    // ----- Bannery a ikony aplikací pro dlaždice -----

    private Drawable findBanner(PackageManager pm, String pkg) {
        Drawable d = null;

        Intent leanback = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (leanback != null) {
            try {
                d = pm.getActivityBanner(leanback);
            } catch (Exception ignored) {
            }
        }

        if (d == null) {
            try {
                d = pm.getApplicationBanner(pkg);
            } catch (Exception ignored) {
            }
        }
        return d;
    }

    private String toDataUrl(Drawable d, int maxW, int maxH) {
        int w = d.getIntrinsicWidth();
        int h = d.getIntrinsicHeight();

        if (w <= 0 || h <= 0) {
            w = maxW;
            h = maxH;
        }

        float s = Math.min(1f,
            Math.min((float) maxW / w, (float) maxH / h));
        int bw = Math.max(1, Math.round(w * s));
        int bh = Math.max(1, Math.round(h * s));

        Bitmap bmp = Bitmap.createBitmap(
            bw, bh, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        d.setBounds(0, 0, bw, bh);
        d.draw(canvas);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        bmp.recycle();

        return "data:image/png;base64,"
            + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    // Otisk nainstalovaných aplikací (balík + čas aktualizace).
    // Bannery se znovu posílají jen tehdy, když se otisk změní.
    private String appSignature(PackageManager pm) {
        StringBuilder sb = new StringBuilder();

        for (String[] entry : apps) {
            String pkg = installedPackage(entry);
            sb.append(entry[0]).append('=');

            if (pkg != null) {
                sb.append(pkg);
                try {
                    sb.append('@').append(
                        pm.getPackageInfo(pkg, 0).lastUpdateTime);
                } catch (Exception ignored) {
                }
            }
            sb.append(';');
        }
        return sb.toString();
    }

    private void loadAppImages() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();

            String signature = appSignature(pm);
            if (signature.equals(lastAppSignature)) {
                return;
            }

            JSONObject result = new JSONObject();

            for (String[] entry : apps) {
                try {
                    JSONObject info = new JSONObject();
                    String pkg = installedPackage(entry);

                    if (pkg == null) {
                        info.put("installed", false);
                    } else {
                        info.put("installed", true);

                        Drawable banner = findBanner(pm, pkg);
                        if (banner != null) {
                            info.put("banner",
                                toDataUrl(banner, 640, 360));
                        } else {
                            info.put("icon", toDataUrl(
                                pm.getApplicationIcon(pkg), 192, 192));
                        }
                    }
                    result.put(entry[0], info);
                } catch (Exception ignored) {
                    // Dlaždice zůstane textová.
                }
            }

            lastAppSignature = signature;
            runJs("window.setAppImages && window.setAppImages("
                + result + ")");
        }).start();
    }

    // ----- Výběr a otevření souboru -----

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQ_FILE || resultCode != RESULT_OK
            || data == null || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();
        String type = getContentResolver().getType(uri);

        Intent view = new Intent(Intent.ACTION_VIEW);
        if (type != null) {
            view.setDataAndType(uri, type);
        } else {
            view.setData(uri);
        }
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        safeStart(view,
            "Pro tento typ souboru není v TV žádná aplikace");
    }

    private void openInBrowser(String url) {
        Intent i = new Intent(this, BrowserActivity.class);
        i.putExtra("url", url);
        safeStart(i, "Prohlížeč se nepodařilo otevřít");
    }

    // Google Disk: Google blokuje přihlášení ve vestavěných
    // prohlížečích, proto nejdřív zkusit jiný prohlížeč v TV
    // (např. Chrome). Když žádný není, otevřít vestavěný.
    private void openDrive() {
        Intent ext = new Intent(Intent.ACTION_VIEW, Uri.parse(DRIVE_URL));
        ext.addCategory(Intent.CATEGORY_BROWSABLE);

        if (!safeStart(ext, null)) {
            toast("V TV není jiný prohlížeč. Přihlášení Google "
                + "ve vestavěném prohlížeči nemusí fungovat.");
            openInBrowser(DRIVE_URL);
        }
    }

    public class Bridge {

        @JavascriptInterface
        public void app(String key) {
            runOnUiThread(() -> startApp(key));
        }

        @JavascriptInterface
        public void browse(String url) {
            runOnUiThread(() -> openInBrowser(url));
        }

        @JavascriptInterface
        public void files() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.setType("*/*");
                i.addCategory(Intent.CATEGORY_OPENABLE);

                try {
                    startActivityForResult(i, REQ_FILE);
                } catch (ActivityNotFoundException e) {
                    toast("Systémový výběr souborů není v TV dostupný");
                }
            });
        }

        @JavascriptInterface
        public void share(String kind) {
            runOnUiThread(() -> {
                if ("localsend".equals(kind)) {
                    startApp("localsend");
                } else if ("drive".equals(kind)) {
                    openDrive();
                }
            });
        }

        // Tlačítko „Domů“: přejít na domovskou obrazovku televize
        // (Google TV). BroZam TV zůstane na pozadí.
        @JavascriptInterface
        public void home() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_HOME);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                if (!safeStart(i, null)) {
                    moveTaskToBack(true);
                }
            });
        }

        @JavascriptInterface
        public void weather() {
            fetchWeather();
        }

        @JavascriptInterface
        public String version() {
            try {
                return getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "";
            }
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);

        if (ui != null) {
            ui.destroy();
            ui = null;
        }

        super.onDestroy();
    }
}
