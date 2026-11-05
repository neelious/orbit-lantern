package com.orbit.lantern;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

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
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Native player for IPTV (live channels, films and episodes). Plays MPEG-TS, HLS and most
 * file formats directly, starts quickly, and switches channel with Up/Down or CH+/CH-.
 * Reports where playback stopped back to the page.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends Activity {
    public static final String EXTRA_SPEC = "spec";
    public static final String EXTRA_TV = "tv";

    private ExoPlayer player;
    private PlayerView view;
    private TextView osd, msg;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private JSONArray channels;
    private int index;
    private boolean live, tv;
    private String itemId, title, subtitle;
    private int retries;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tv = getIntent().getBooleanExtra(EXTRA_TV, false);
        try {
            JSONObject spec = new JSONObject(getIntent().getStringExtra(EXTRA_SPEC));
            live = spec.optBoolean("live");
            itemId = spec.optString("itemId");
            title = spec.optString("title");
            subtitle = spec.optString("subtitle");
            channels = spec.optJSONArray("channels");
            index = spec.optInt("index", 0);
            if (channels == null || channels.length() == 0) {
                channels = new JSONArray();
                JSONObject one = new JSONObject();
                one.put("url", spec.optString("url"));
                one.put("title", title);
                one.put("id", itemId);
                channels.put(one);
                index = 0;
            }
            buildUi();
            buildPlayer();
            load(index, (long) (spec.optDouble("start", 0) * 1000));
        } catch (Exception e) {
            finish();
        }
    }

    private void buildUi() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (!tv) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        view = new PlayerView(this);
        view.setControllerShowTimeoutMs(3500);
        view.setShowNextButton(false);
        view.setShowPreviousButton(false);
        view.setKeepScreenOn(true);
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));

        float d = getResources().getDisplayMetrics().density;
        osd = new TextView(this);
        osd.setTextColor(Color.WHITE);
        osd.setTextSize(22);
        osd.setShadowLayer(6, 0, 2, Color.BLACK);
        osd.setPadding((int) (28 * d), (int) (22 * d), (int) (28 * d), 0);
        osd.setVisibility(View.GONE);
        root.addView(osd, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));

        msg = new TextView(this);
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(18);
        msg.setGravity(Gravity.CENTER);
        msg.setBackgroundColor(0xAA000000);
        msg.setPadding((int) (24 * d), (int) (16 * d), (int) (24 * d), (int) (16 * d));
        msg.setVisibility(View.GONE);
        root.addView(msg, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        setContentView(root);
        immersive();
    }

    private void buildPlayer() {
        // Small buffers so channels start and switch quickly.
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(2500, 30000, 700, 1500)
                .build();
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("Lantern/1.7 (Linux; Android)")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(10000)
                .setReadTimeoutMs(20000);
        player = new ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(this).setDataSourceFactory(http))
                .build();
        view.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                if (retries++ < 2) {
                    showMsg(live ? "Reconnecting…" : "Retrying…");
                    handler.postDelayed(() -> { player.prepare(); player.play(); }, 1200);
                } else {
                    showMsg((live ? "This channel isn't playing right now." : "This title can't be played.")
                            + (live && channels.length() > 1 ? "\nPress Up or Down for another channel." : ""));
                }
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    retries = 0;
                    msg.setVisibility(View.GONE);
                } else if (state == Player.STATE_ENDED && !live) {
                    finishWithResult(true);
                }
            }
        });
    }

    private void load(int i, long startMs) {
        try {
            JSONObject c = channels.getJSONObject(i);
            String url = c.optString("url");
            MediaItem.Builder b = new MediaItem.Builder().setUri(url);
            String lower = url.toLowerCase();
            if (lower.contains(".m3u8")) b.setMimeType(MimeTypes.APPLICATION_M3U8);
            else if (lower.matches(".*\\.ts(\\?.*)?$")) b.setMimeType(MimeTypes.VIDEO_MP2T);
            msg.setVisibility(View.GONE);
            player.setMediaItem(b.build(), Math.max(0, startMs));
            player.prepare();
            player.play();
            String num = c.optString("num");
            String name = c.optString("title", title);
            showOsd(live ? ((num.isEmpty() ? "" : num + "  ") + name + "   ● LIVE") : (subtitle.isEmpty() ? name : name + "\n" + subtitle));
        } catch (Exception e) {
            showMsg("This can't be played.");
        }
    }

    private void zap(int delta) {
        if (!live || channels.length() < 2) return;
        index = (index + delta + channels.length()) % channels.length();
        retries = 0;
        load(index, 0);
    }

    private void showOsd(String text) {
        osd.setText(text);
        osd.setVisibility(View.VISIBLE);
        handler.removeCallbacksAndMessages("osd");
        handler.postAtTime(() -> osd.setVisibility(View.GONE), "osd", android.os.SystemClock.uptimeMillis() + 4000);
    }

    private void showMsg(String text) {
        msg.setText(text);
        msg.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN && live && !view.isControllerFullyVisible()) {
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_CHANNEL_UP:
                case KeyEvent.KEYCODE_PAGE_UP:
                    zap(-1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_CHANNEL_DOWN:
                case KeyEvent.KEYCODE_PAGE_DOWN:
                    zap(1);
                    return true;
                default:
                    break;
            }
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && live
                && (e.getKeyCode() == KeyEvent.KEYCODE_CHANNEL_UP || e.getKeyCode() == KeyEvent.KEYCODE_CHANNEL_DOWN)) {
            zap(e.getKeyCode() == KeyEvent.KEYCODE_CHANNEL_UP ? -1 : 1);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (view.isControllerFullyVisible()) {
            view.hideController();
            return;
        }
        finishWithResult(false);
    }

    private void finishWithResult(boolean ended) {
        Intent data = new Intent();
        try {
            JSONObject r = new JSONObject();
            JSONObject c = channels.getJSONObject(index);
            r.put("itemId", live ? c.optString("id", itemId) : itemId);
            r.put("pos", player == null ? 0 : player.getCurrentPosition() / 1000.0);
            long dur = player == null ? 0 : player.getDuration();
            r.put("dur", dur > 0 ? dur / 1000.0 : 0);
            r.put("ended", ended);
            r.put("live", live);
            r.put("index", index);
            data.putExtra("result", r.toString());
        } catch (Exception ignored) {
        }
        setResult(RESULT_OK, data);
        finish();
    }

    @SuppressWarnings("deprecation")
    private void immersive() {
        View decor = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = decor.getWindowInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null && !isFinishing()) player.pause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
