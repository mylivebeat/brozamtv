package cz.brozam.tvlauncher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private static final int REQ_FILE = 101;

    private static final String WEATHER_URL =
        "https://api.open-meteo.com/v1/forecast"
        + "?latitude=48.98"
        + "&longitude=17.47"
        + "&current=temperature_2m,weather_code"
        + "&daily=weather_code,"
        + "temperature_2m_max,"
        + "temperature_2m_min"
        + "&timezone=Europe%2FPrague"
        + "&forecast_days=3";

    private static final String DRIVE_URL =
        "https://drive.google.com/drive/u/0/my-drive";

    private WebView ui;
    private boolean pageReady = false;

    // Klíč dlaždice a možné názvy balíčků (první nalezený se použije).
    private final String[][] apps = {
        {"oneplay", "cz.o2.oneplay", "cz.o2.o2tv"},
        {"netflix", "com.netflix.ninja", "com.netflix.mediaclient"},
        {"youtube", "com.google.android.youtube.tv",
            "com.google.android.youtube"},
        {"kodi", "org.xbmc.kodi"},
        {"ct", "cz.ceskatelevize.ivysilani",
            "cz.ceskatelevize.androidtv"},
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

        ui.getSettings().setJavaScriptEnabled(true);
        ui.getSettings().setDomStorageEnabled(true);
        ui.getSettings().setAllowFileAccess(true);

        // Počasí a ikony aplikací se posílají až po načtení stránky,
        // jinak by se výsledek mohl ztratit.
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
        });

        ui.addJavascriptInterface(new Bridge(), "TV");

        setContentView(ui);

        ui.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Po návratu z jiné aplikace obnovit dlaždice
        // (aplikace mohla být mezitím nainstalovaná nebo odinstalovaná).
        if (pageReady) {
            loadAppImages();
        }
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

    private void fetchWeather() {
        new Thread(() -> {
            HttpURLConnection c = null;

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

            } catch (Exception e) {
                runJs("window.weatherError()");

            } finally {
                if (c != null) {
                    c.disconnect();
                }
            }
        }).start();
    }

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

    private void loadAppImages() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
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
                    openInBrowser(DRIVE_URL);
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
    }

    @Override
    protected void onDestroy() {
        if (ui != null) {
            ui.destroy();
            ui = null;
        }

        super.onDestroy();
    }
}
