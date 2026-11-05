package com.orbit.lantern;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.UiModeManager;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.RenderProcessGoneDetail;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.TextureView;
import android.widget.FrameLayout;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

/**
 * Hosts the bundled web interface and gives it what a web page can't do on its own:
 * rotating to landscape and going truly full screen while a video plays, keeping the
 * screen on, the TV launcher, and the hardware Back button.
 */
@OptIn(markerClass = UnstableApi.class)
public class MainActivity extends Activity {
    private static final String APP_URL = "file:///android_asset/www/index.html";
    private static final int REQ_PLAY = 42;

    private WebView web;
    private FrameLayout root;
    /* small native player drawn over the page's preview box (Live TV guide) */
    private ExoPlayer preview;
    private TextureView previewView;
    private boolean tv;
    private boolean playing;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tv = isTelevision();
        createWebView();
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(APP_URL);
        if (tv) applyImmersive(true);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void createWebView() {
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        if (root == null) {
            root = new FrameLayout(this);
            root.setBackgroundColor(Color.BLACK);
            setContentView(root);
        }
        root.addView(web, 0, new FrameLayout.LayoutParams(-1, -1));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        // The page is loaded from the app itself and talks straight to the user's own servers.
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + (tv ? " LanternTV" : " LanternApp"));

        web.addJavascriptInterface(new Bridge(), "LanternNative");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("file".equals(u.getScheme())) return false;
                openExternal(u.toString());
                return true;
            }

            /*
             * Android killed the page (usually out of memory on a huge IPTV list). Without this the
             * whole app closes; instead rebuild the view and reopen on the source screen.
             */
            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
                view.destroy();
                playing = false;
                previewStop();
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                createWebView();
                web.loadUrl(APP_URL + "#recovered");
                return true;
            }
        });
    }

    private void previewStart(String url, float x, float y, float w, float h) {
        if (preview == null) {
            DefaultLoadControl lc = new DefaultLoadControl.Builder().setBufferDurationsMs(2000, 15000, 500, 1000).build();
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                    .setUserAgent("Lantern/1.8 (Linux; Android)")
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(8000)
                    .setReadTimeoutMs(15000);
            preview = new ExoPlayer.Builder(this)
                    .setLoadControl(lc)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(this).setDataSourceFactory(http))
                    .build();
            preview.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(PlaybackException error) {
                    if (web != null) web.evaluateJavascript("window.lanternPreviewState && window.lanternPreviewState('error')", null);
                }

                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY && web != null)
                        web.evaluateJavascript("window.lanternPreviewState && window.lanternPreviewState('ready')", null);
                }
            });
            previewView = new TextureView(this);
            preview.setVideoTextureView(previewView);
            root.addView(previewView, new FrameLayout.LayoutParams(1, 1));
        }
        previewMove(x, y, w, h);
        MediaItem.Builder b = new MediaItem.Builder().setUri(url);
        String lower = url.toLowerCase();
        if (lower.contains(".m3u8")) b.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (lower.matches(".*\\.ts(\\?.*)?$")) b.setMimeType(MimeTypes.VIDEO_MP2T);
        preview.setMediaItem(b.build());
        preview.prepare();
        preview.play();
        previewView.setVisibility(View.VISIBLE);
    }

    private void previewMove(float x, float y, float w, float h) {
        if (previewView == null) return;
        float d = getResources().getDisplayMetrics().density;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.max(1, Math.round(w * d)), Math.max(1, Math.round(h * d)));
        lp.leftMargin = Math.round(x * d);
        lp.topMargin = Math.round(y * d);
        previewView.setLayoutParams(lp);
    }

    private void previewStop() {
        if (preview != null) {
            preview.stop();
            preview.clearMediaItems();
        }
        if (previewView != null) previewView.setVisibility(View.GONE);
    }

    private boolean isTelevision() {
        UiModeManager ui = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        if (ui != null && ui.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) return true;
        PackageManager pm = getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
    }

    private void setPlaying(boolean on) {
        playing = on;
        if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (!tv) {
            setRequestedOrientation(on ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        applyImmersive(on || tv);
    }

    @SuppressWarnings("deprecation")
    private void applyImmersive(boolean on) {
        View decor = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(!on);
            WindowInsetsController c = decor.getWindowInsetsController();
            if (c == null) return;
            if (on) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else {
                c.show(WindowInsets.Type.systemBars());
            }
        } else {
            decor.setSystemUiVisibility(on
                    ? View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    : View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
            // TVs often have no browser; the page shows the link as text too.
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && (playing || tv)) applyImmersive(true);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // Let the page close the player, a panel or a dialog first; leave the app only from Home.
        web.evaluateJavascript("(window.lanternBack && window.lanternBack()) ? 'y' : 'n'", v -> {
            if (v == null || !v.contains("y")) moveTaskToBack(true);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        previewStop();
        web.evaluateJavascript("window.lanternPause && window.lanternPause()", null);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PLAY || web == null) return;
        String r = data == null ? null : data.getStringExtra("result");
        if (r == null) r = "{}";
        web.evaluateJavascript("window.lanternNativeEnded && window.lanternNativeEnded(" + org.json.JSONObject.quote(r) + ")", null);
        if (tv) applyImmersive(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (preview != null) {
            preview.release();
            preview = null;
        }
        web.destroy();
        super.onDestroy();
    }

    /** Exposed to the page as window.LanternNative. */
    private class Bridge {
        @JavascriptInterface
        public void setPlaying(boolean on) {
            runOnUiThread(() -> MainActivity.this.setPlaying(on));
        }

        @JavascriptInterface
        public boolean isTv() {
            return tv;
        }

        @JavascriptInterface
        public void openExternal(String url) {
            runOnUiThread(() -> MainActivity.this.openExternal(url));
        }

        /* Key-value files in the app's private storage, used to keep big playlists out of memory. */
        private java.io.File storeFile(String key) {
            java.io.File dir = new java.io.File(getFilesDir(), "store");
            if (!dir.exists()) dir.mkdirs();
            return new java.io.File(dir, key.replaceAll("[^A-Za-z0-9._-]", "_"));
        }

        @JavascriptInterface
        public boolean putData(String key, String value) {
            try (java.io.Writer w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(storeFile(key)), java.nio.charset.StandardCharsets.UTF_8)) {
                w.write(value);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public String getData(String key) {
            java.io.File f = storeFile(key);
            if (!f.exists()) return null;
            try (java.io.InputStream in = new java.io.FileInputStream(f)) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream((int) f.length());
                byte[] buf = new byte[65536];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                return null;
            }
        }

        @JavascriptInterface
        public void deleteData(String prefix) {
            String p = prefix.replaceAll("[^A-Za-z0-9._-]", "_");
            java.io.File[] files = new java.io.File(getFilesDir(), "store").listFiles();
            if (files == null) return;
            for (java.io.File f : files) if (f.getName().startsWith(p)) f.delete();
        }

        /* Open the native player; the result comes back through window.lanternNativeEnded(). */
        @JavascriptInterface
        public void playNative(String spec) {
            runOnUiThread(() -> {
                previewStop();
                Intent i = new Intent(MainActivity.this, PlayerActivity.class);
                i.putExtra(PlayerActivity.EXTRA_SPEC, spec);
                i.putExtra(PlayerActivity.EXTRA_TV, tv);
                startActivityForResult(i, REQ_PLAY);
            });
        }

        /* Live TV preview: x/y/w/h are the preview box in page (CSS) pixels. */
        @JavascriptInterface
        public void previewStart(String url, float x, float y, float w, float h) {
            runOnUiThread(() -> MainActivity.this.previewStart(url, x, y, w, h));
        }

        @JavascriptInterface
        public void previewMove(float x, float y, float w, float h) {
            runOnUiThread(() -> MainActivity.this.previewMove(x, y, w, h));
        }

        @JavascriptInterface
        public void previewStop() {
            runOnUiThread(MainActivity.this::previewStop);
        }

        @JavascriptInterface
        public String version() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "";
            }
        }
    }
}
