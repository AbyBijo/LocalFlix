package com.media.localflix;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.reflect.Method;
import java.util.List;

/** Full custom video player: TextureView + MediaPlayer + gesture + overlay controls. */
public class PlayerView extends FrameLayout implements TextureView.SurfaceTextureListener {

    public interface Listener {
        void onClosed();
    }

    private static final int RED = 0xFFE50914;
    private static final String[] MODE_NAMES = {"Fit", "Fill", "Stretch", "16:9", "4:3", "21:9", "1:1", "100%"};
    private static final float[] MODE_ASPECT = {0f, 0f, 0f, 16f / 9f, 4f / 3f, 21f / 9f, 1f, 0f};
    private static final int M_FILL = 1, M_STRETCH = 2, M_ORIG = 7;
    private static final float[] SPEEDS = {0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f};
    private static final String[] ORIENT_NAMES = {"Auto", "Land", "Port", "Free", "Lock"};
    private static final int D_NONE = 0, D_SEEK = 1, D_BRIGHT = 2, D_VOL = 3, D_PAN = 4;

    private final Activity act;
    private final SharedPreferences prefs;
    private final AudioManager am;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Listener listener;

    private TextureView tv;
    private Surface surface;
    private MediaPlayer mp;
    private Video curVideo;
    private boolean prepared, pendingStart;
    private List<Video> list;
    private int index;
    private int vw, vh, dur;

    private int mode = 0, orientMode = 0;
    private float zoom = 1f, panX = 0f, panY = 0f, speed = 1f;
    private boolean loop, muted, locked, controlsVisible, dragging, boost;
    private long lastSave;

    private GestureDetector gd;
    private ScaleGestureDetector sgd;
    private boolean multi;
    private int dragMode = D_NONE, scrubStart, scrubTarget;
    private float brightStart, volStart;

    private FrameLayout controls;
    private TextView hud, errorText, title, timeCur, timeTotal, btnSpeed, btnAspect;
    private ImageView btnPlay, btnFav, btnLoop, btnMute;
    private TextView btnRotate, btnAudio, unlockBtn, zoomLabel, speedLabel;
    private SeekBar seek, speedSeek;
    private LinearLayout speedPanel, aspectPanel, audioPanel, audioRow;
    private final TextView[] modePills = new TextView[MODE_NAMES.length];
    private final TextView[] speedPills = new TextView[SPEEDS.length];

    private static Method seekPrecise;
    private static boolean seekPreciseTried;

    public PlayerView(Activity a) {
        super(a);
        act = a;
        prefs = a.getSharedPreferences("lf", Context.MODE_PRIVATE);
        am = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
        orientMode = prefs.getInt("orient", 0);
        init();
        setVisibility(GONE);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public boolean isOpen() {
        return getVisibility() == VISIBLE;
    }

    // ------------------------------------------------------------------ UI build

    private int dp(float v) {
        return Util.dp(act, v);
    }

    private TextView label(String t) {
        TextView v = new TextView(act);
        v.setText(t);
        v.setTextColor(0xFFFFFFFF);
        v.setTextSize(13);
        v.setPadding(dp(4), dp(2), dp(4), dp(6));
        return v;
    }

    private LinearLayout panel() {
        LinearLayout p = new LinearLayout(act);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(10), dp(8), dp(10), dp(8));
        p.setBackground(Util.rounded(act, 0xEE181818, 12));
        p.setVisibility(GONE);
        p.setClickable(true);
        return p;
    }

    private void addM(LinearLayout row, View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(3), dp(2), dp(3), dp(2));
        row.addView(v, lp);
    }

    private void tap(View v, final Runnable r) {
        v.setOnClickListener(new View.OnClickListener() {
            public void onClick(View x) {
                r.run();
                scheduleHide();
            }
        });
    }

    private void tint(SeekBar s) {
        try {
            s.setProgressTintList(ColorStateList.valueOf(RED));
            s.setThumbTintList(ColorStateList.valueOf(RED));
            s.setProgressBackgroundTintList(ColorStateList.valueOf(0x66FFFFFF));
        } catch (Throwable ignore) {
        }
    }

    private void init() {
        setBackgroundColor(0xFF000000);

        tv = new TextureView(act);
        tv.setSurfaceTextureListener(this);
        addView(tv, new FrameLayout.LayoutParams(-1, -1));

        hud = new TextView(act);
        hud.setTextColor(0xFFFFFFFF);
        hud.setTextSize(20);
        hud.setGravity(Gravity.CENTER);
        hud.setPadding(dp(18), dp(10), dp(18), dp(10));
        hud.setBackground(Util.rounded(act, 0xAA000000, 12));
        hud.setVisibility(GONE);
        addView(hud, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        errorText = new TextView(act);
        errorText.setTextColor(0xFFFFFFFF);
        errorText.setTextSize(15);
        errorText.setGravity(Gravity.CENTER);
        errorText.setPadding(dp(24), dp(16), dp(24), dp(16));
        errorText.setBackground(Util.rounded(act, 0xCC222222, 12));
        errorText.setVisibility(GONE);
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        ep.leftMargin = dp(30);
        ep.rightMargin = dp(30);
        addView(errorText, ep);

        unlockBtn = Util.pill(act, "Unlock");
        unlockBtn.setBackground(Util.rounded(act, 0xCC000000, 18));
        unlockBtn.setVisibility(GONE);
        FrameLayout.LayoutParams up = new FrameLayout.LayoutParams(-2, -2, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        up.leftMargin = dp(24);
        addView(unlockBtn, up);
        unlockBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                locked = false;
                unlockBtn.setVisibility(GONE);
                showHud("Unlocked");
                showControls();
            }
        });

        controls = new FrameLayout(act);
        addView(controls, new FrameLayout.LayoutParams(-1, -1));

        // ---- top bar
        LinearLayout top = new LinearLayout(act);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(10), dp(8), dp(10), dp(20));
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {0xCC000000, 0x00000000}));
        controls.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        ImageView back = Util.iconBtn(act, Icon.BACK, 20);
        addM(top, back);
        tap(back, new Runnable() {
            public void run() {
                close();
            }
        });

        title = new TextView(act);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(16);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(dp(8), 0, dp(8), 0);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        btnFav = Util.iconBtn(act, Icon.HEART, 20);
        addM(top, btnFav);
        tap(btnFav, new Runnable() {
            public void run() {
                if (curVideo != null) {
                    Util.toggleFav(prefs, curVideo.id);
                    updateFav();
                }
            }
        });
        btnAspect = Util.pill(act, "Fit");
        addM(top, btnAspect);
        tap(btnAspect, new Runnable() {
            public void run() {
                togglePanel(aspectPanel);
            }
        });
        btnRotate = Util.pill(act, "Rot: " + ORIENT_NAMES[orientMode]);
        addM(top, btnRotate);
        tap(btnRotate, new Runnable() {
            public void run() {
                orientMode = (orientMode + 1) % ORIENT_NAMES.length;
                btnRotate.setText("Rot: " + ORIENT_NAMES[orientMode]);
                prefs.edit().putInt("orient", orientMode).apply();
                applyOrientation();
            }
        });

        // ---- bottom bar
        LinearLayout bottom = new LinearLayout(act);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(12), dp(26), dp(12), dp(20));
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
            new int[] {0xDD000000, 0x00000000}));
        controls.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        // speed panel
        speedPanel = panel();
        speedLabel = label("Speed 1.0x");
        speedPanel.addView(speedLabel);
        HorizontalScrollView ssv = new HorizontalScrollView(act);
        ssv.setHorizontalScrollBarEnabled(false);
        LinearLayout srow = new LinearLayout(act);
        ssv.addView(srow);
        for (int i = 0; i < SPEEDS.length; i++) {
            final float s = SPEEDS[i];
            TextView p = Util.pill(act, Util.speed(s));
            speedPills[i] = p;
            tap(p, new Runnable() {
                public void run() {
                    setSpeed(s);
                }
            });
            addM(srow, p);
        }
        speedPanel.addView(ssv);
        speedSeek = new SeekBar(act);
        speedSeek.setMax(75);
        speedSeek.setProgress(15);
        tint(speedSeek);
        speedSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user) setSpeed(0.25f + p * 0.05f);
            }

            public void onStartTrackingTouch(SeekBar s) {
                h.removeCallbacks(autoHide);
            }

            public void onStopTrackingTouch(SeekBar s) {
                scheduleHide();
            }
        });
        speedPanel.addView(speedSeek);
        addPanel(bottom, speedPanel);

        // viewport panel
        aspectPanel = panel();
        aspectPanel.addView(label("Viewport  (pinch = zoom, drag = pan when zoomed)"));
        HorizontalScrollView asv = new HorizontalScrollView(act);
        asv.setHorizontalScrollBarEnabled(false);
        LinearLayout arow = new LinearLayout(act);
        asv.addView(arow);
        for (int i = 0; i < MODE_NAMES.length; i++) {
            final int m = i;
            TextView p = Util.pill(act, MODE_NAMES[i]);
            modePills[i] = p;
            tap(p, new Runnable() {
                public void run() {
                    setMode(m);
                }
            });
            addM(arow, p);
        }
        aspectPanel.addView(asv);
        LinearLayout zrow = new LinearLayout(act);
        zrow.setGravity(Gravity.CENTER_VERTICAL);
        TextView zm = Util.pill(act, "Zoom -");
        TextView zp = Util.pill(act, "Zoom +");
        TextView zr = Util.pill(act, "Reset");
        zoomLabel = label("100%");
        tap(zm, new Runnable() {
            public void run() {
                setZoom(zoom - 0.25f);
            }
        });
        tap(zp, new Runnable() {
            public void run() {
                setZoom(zoom + 0.25f);
            }
        });
        tap(zr, new Runnable() {
            public void run() {
                zoom = 1f;
                panX = 0f;
                panY = 0f;
                applyTransform();
                updateModeUi();
            }
        });
        addM(zrow, zm);
        addM(zrow, zp);
        addM(zrow, zr);
        addM(zrow, zoomLabel);
        aspectPanel.addView(zrow);
        addPanel(bottom, aspectPanel);

        // audio panel
        audioPanel = panel();
        audioPanel.addView(label("Audio track"));
        HorizontalScrollView aud = new HorizontalScrollView(act);
        aud.setHorizontalScrollBarEnabled(false);
        audioRow = new LinearLayout(act);
        aud.addView(audioRow);
        audioPanel.addView(aud);
        addPanel(bottom, audioPanel);

        // seek row
        LinearLayout sk = new LinearLayout(act);
        sk.setGravity(Gravity.CENTER_VERTICAL);
        timeCur = new TextView(act);
        timeCur.setTextColor(0xFFFFFFFF);
        timeCur.setTextSize(12);
        timeCur.setText("0:00");
        timeTotal = new TextView(act);
        timeTotal.setTextColor(0xFFFFFFFF);
        timeTotal.setTextSize(12);
        timeTotal.setText("0:00");
        seek = new SeekBar(act);
        seek.setPadding(dp(14), dp(12), dp(14), dp(12));
        tint(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user) timeCur.setText(Util.time(p));
            }

            public void onStartTrackingTouch(SeekBar s) {
                dragging = true;
                h.removeCallbacks(autoHide);
            }

            public void onStopTrackingTouch(SeekBar s) {
                dragging = false;
                if (mp != null && prepared) seekMs(s.getProgress());
                scheduleHide();
            }
        });
        sk.addView(timeCur);
        sk.addView(seek, new LinearLayout.LayoutParams(0, -2, 1f));
        sk.addView(timeTotal);
        bottom.addView(sk, new LinearLayout.LayoutParams(-1, -2));

        // transport row
        LinearLayout tr = new LinearLayout(act);
        tr.setGravity(Gravity.CENTER);
        ImageView prev = flat(Icon.PREV, 22);
        ImageView back10 = flat(Icon.REW, 30);
        ImageView fwd10 = flat(Icon.FWD, 30);
        ImageView next = flat(Icon.NEXT, 22);
        btnPlay = Util.iconBtn(act, Icon.PLAY, 28);
        btnPlay.setBackground(Util.rounded(act, RED, 32));
        btnPlay.setPadding(dp(18), dp(10), dp(18), dp(10));
        addM(tr, prev);
        addM(tr, back10);
        addM(tr, btnPlay);
        addM(tr, fwd10);
        addM(tr, next);
        bottom.addView(tr, new LinearLayout.LayoutParams(-1, -2));
        tap(prev, new Runnable() {
            public void run() {
                if (index > 0) {
                    index--;
                    startCurrent();
                } else if (mp != null && prepared) {
                    seekMs(0);
                }
            }
        });
        tap(next, new Runnable() {
            public void run() {
                if (list != null && index < list.size() - 1) {
                    index++;
                    startCurrent();
                }
            }
        });
        tap(back10, new Runnable() {
            public void run() {
                seekBy(-10000);
                showHud("-10s");
            }
        });
        tap(fwd10, new Runnable() {
            public void run() {
                seekBy(10000);
                showHud("+10s");
            }
        });
        tap(btnPlay, new Runnable() {
            public void run() {
                togglePlay();
            }
        });

        // utility row
        LinearLayout orow = new LinearLayout(act);
        orow.setGravity(Gravity.CENTER_VERTICAL);
        btnSpeed = new TextView(act);
        btnSpeed.setText("1.0x");
        btnSpeed.setTextColor(0xFFFFFFFF);
        btnSpeed.setTextSize(15);
        btnSpeed.setTypeface(null, android.graphics.Typeface.BOLD);
        btnSpeed.setGravity(Gravity.CENTER);
        btnSpeed.setPadding(0, dp(8), 0, dp(8));
        btnSpeed.setClickable(true);
        btnLoop = flat(Icon.ROTATE, 22);
        btnMute = flat(Icon.VOL, 22);
        btnAudio = new TextView(act);
        btnAudio.setText("Audio");
        btnAudio.setTextColor(0xFFFFFFFF);
        btnAudio.setTextSize(14);
        btnAudio.setGravity(Gravity.CENTER);
        btnAudio.setPadding(0, dp(8), 0, dp(8));
        btnAudio.setClickable(true);
        btnAudio.setVisibility(GONE);
        ImageView lock = flat(Icon.LOCK, 22);
        orow.addView(btnSpeed, new LinearLayout.LayoutParams(0, -2, 1f));
        orow.addView(btnLoop, new LinearLayout.LayoutParams(0, -2, 1f));
        orow.addView(btnMute, new LinearLayout.LayoutParams(0, -2, 1f));
        orow.addView(btnAudio, new LinearLayout.LayoutParams(0, -2, 1f));
        orow.addView(lock, new LinearLayout.LayoutParams(0, -2, 1f));
        bottom.addView(orow, new LinearLayout.LayoutParams(-1, -2));
        updateOpts();
        tap(btnSpeed, new Runnable() {
            public void run() {
                togglePanel(speedPanel);
            }
        });
        tap(btnLoop, new Runnable() {
            public void run() {
                loop = !loop;
                updateOpts();
                showHud(loop ? "Loop on" : "Loop off");
                if (mp != null) {
                    try {
                        mp.setLooping(loop);
                    } catch (Exception ignore) {
                    }
                }
            }
        });
        tap(btnMute, new Runnable() {
            public void run() {
                muted = !muted;
                updateOpts();
                applyMute();
                showHud(muted ? "Muted" : "Sound on");
            }
        });
        tap(btnAudio, new Runnable() {
            public void run() {
                togglePanel(audioPanel);
            }
        });
        tap(lock, new Runnable() {
            public void run() {
                locked = true;
                hideControls();
                showHud("Controls locked - tap screen to unlock");
            }
        });

        // gestures
        gd = new GestureDetector(act, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                onTap();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                onDouble(e);
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (locked || mp == null || !prepared || !mp.isPlaying()) return;
                boost = true;
                applySpeed();
                showHud(Util.speed(Math.max(2f, speed)) + " hold");
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                return onDrag(e1, e2, dx, dy);
            }
        });
        sgd = new ScaleGestureDetector(act, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                zoom = Math.max(1f, Math.min(8f, zoom * d.getScaleFactor()));
                applyTransform();
                zoomLabel.setText(Math.round(zoom * 100) + "%");
                showHud(Math.round(zoom * 100) + "%");
                return true;
            }
        });
        try {
            sgd.setQuickScaleEnabled(false);
        } catch (Throwable ignore) {
        }

        updateModeUi();
        updateSpeedUi();
    }

    private void addPanel(LinearLayout bottom, LinearLayout p) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        bottom.addView(p, lp);
    }

    // ------------------------------------------------------------------ open / close

    public void open(List<Video> l, int i) {
        list = l;
        index = i;
        setVisibility(VISIBLE);
        setKeepScreenOn(true);
        locked = false;
        unlockBtn.setVisibility(GONE);
        startCurrent();
    }

    private void startCurrent() {
        if (list == null || index < 0 || index >= list.size()) return;
        saveProgress();
        releasePlayer();
        Video v = list.get(index);
        curVideo = v;
        title.setText(v.name);
        updateFav();
        errorText.setVisibility(GONE);
        vw = v.width;
        vh = v.height;
        dur = (int) v.duration;
        seek.setMax(Math.max(1, dur));
        seek.setProgress(0);
        timeCur.setText("0:00");
        timeTotal.setText(Util.time(dur));
        btnAudio.setVisibility(GONE);
        zoom = 1f;
        panX = 0f;
        panY = 0f;
        updateModeUi();
        applyOrientation();
        applyTransform();
        updatePlayIcon();
        if (surface == null && tv.isAvailable()) {
            surface = new Surface(tv.getSurfaceTexture());
        }
        if (surface == null) {
            pendingStart = true;
        } else {
            createPlayer();
        }
        showControls();
    }

    private void createPlayer() {
        if (list == null || surface == null || index >= list.size()) return;
        final Video v = list.get(index);
        curVideo = v;
        try {
            final MediaPlayer p = new MediaPlayer();
            mp = p;
            p.setSurface(surface);
            p.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build());
            p.setDataSource(act, v.uri);
            p.setLooping(loop);
            p.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                public void onPrepared(MediaPlayer m) {
                    if (m == mp) onPreparedInternal(m, v);
                }
            });
            p.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                public void onVideoSizeChanged(MediaPlayer m, int w, int hh) {
                    if (m != mp || w <= 0 || hh <= 0) return;
                    vw = w;
                    vh = hh;
                    applyTransform();
                }
            });
            p.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer m) {
                    if (m != mp) return;
                    prefs.edit().remove("pos_" + v.id).apply();
                    if (list != null && prefs.getBoolean("autonext", true) && index < list.size() - 1) {
                        index++;
                        startCurrent();
                    } else {
                        updatePlayIcon();
                        showControls();
                    }
                }
            });
            p.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                public boolean onError(MediaPlayer m, int what, int extra) {
                    if (m != mp) return true;
                    showError("Cannot play this file.\nFormat or codec not supported by Android's built-in decoder.\n(" + what + "/" + extra + ")");
                    return true;
                }
            });
            p.prepareAsync();
        } catch (Exception e) {
            showError("Cannot open video.\n" + e.getClass().getSimpleName());
        }
    }

    private void onPreparedInternal(MediaPlayer p, Video v) {
        prepared = true;
        dur = p.getDuration();
        seek.setMax(Math.max(1, dur));
        timeTotal.setText(Util.time(dur));
        int w = p.getVideoWidth(), hh = p.getVideoHeight();
        if (w > 0 && hh > 0) {
            vw = w;
            vh = hh;
        }
        if (orientMode == 0) applyOrientation();
        applyTransform();
        long saved = prefs.getLong("pos_" + v.id, 0L);
        if (prefs.getBoolean("resume", true) && saved > 3000 && dur > 0 && saved < dur - 5000) {
            seekMs((int) saved);
            showHud("Resumed " + Util.time(saved));
        }
        applyMute();
        requestFocus2();
        try {
            p.start();
        } catch (Exception e) {
            showError("Cannot start playback.");
            return;
        }
        applySpeed();
        buildAudioTracks(p);
        updatePlayIcon();
        h.removeCallbacks(tick);
        h.post(tick);
        showControls();
    }

    private void buildAudioTracks(MediaPlayer p) {
        audioRow.removeAllViews();
        btnAudio.setVisibility(GONE);
        try {
            MediaPlayer.TrackInfo[] ti = p.getTrackInfo();
            int n = 0;
            for (int i = 0; i < ti.length; i++) {
                if (ti[i].getTrackType() != MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) continue;
                n++;
                final int idx = i;
                String lang = ti[i].getLanguage();
                TextView pill = Util.pill(act, "Track " + n + (lang != null && lang.length() > 0 && !"und".equals(lang) ? " (" + lang + ")" : ""));
                tap(pill, new Runnable() {
                    public void run() {
                        try {
                            if (mp != null) mp.selectTrack(idx);
                            showHud("Audio track changed");
                        } catch (Exception e) {
                            showHud("Track not switchable");
                        }
                    }
                });
                addM(audioRow, pill);
            }
            if (n > 1) btnAudio.setVisibility(VISIBLE);
        } catch (Throwable ignore) {
        }
    }

    public void close() {
        saveProgress();
        releasePlayer();
        hidePanels();
        h.removeCallbacks(autoHide);
        setVisibility(GONE);
        setKeepScreenOn(false);
        pendingStart = false;
        try {
            WindowManager.LayoutParams lp = act.getWindow().getAttributes();
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            act.getWindow().setAttributes(lp);
            act.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        } catch (Throwable ignore) {
        }
        if (listener != null) listener.onClosed();
    }

    public void onBack() {
        if (anyPanelOpen()) {
            hidePanels();
            return;
        }
        if (locked) {
            showUnlock();
            return;
        }
        close();
    }

    public void onPause() {
        if (mp != null && prepared) {
            try {
                if (mp.isPlaying()) mp.pause();
            } catch (Exception ignore) {
            }
            saveProgress();
            updatePlayIcon();
        }
    }

    public void shutdown() {
        saveProgress();
        releasePlayer();
    }

    private void releasePlayer() {
        h.removeCallbacks(tick);
        if (mp != null) {
            try {
                mp.setOnPreparedListener(null);
                mp.setOnCompletionListener(null);
                mp.setOnErrorListener(null);
                mp.setOnVideoSizeChangedListener(null);
                mp.release();
            } catch (Exception ignore) {
            }
            mp = null;
        }
        prepared = false;
        abandonFocus();
    }

    private void saveProgress() {
        if (mp == null || !prepared || curVideo == null) return;
        if (!prefs.getBoolean("resume", true)) return;
        try {
            int pos = mp.getCurrentPosition();
            int d = mp.getDuration();
            if (pos > 3000 && d > 0 && pos < d - 5000) {
                prefs.edit().putLong("pos_" + curVideo.id, pos)
                    .putLong("time_" + curVideo.id, System.currentTimeMillis()).apply();
            } else {
                prefs.edit().remove("pos_" + curVideo.id).remove("time_" + curVideo.id).apply();
            }
        } catch (Exception ignore) {
        }
    }

    // ------------------------------------------------------------------ playback

    private final Runnable tick = new Runnable() {
        public void run() {
            if (mp != null && prepared) {
                try {
                    int pos = mp.getCurrentPosition();
                    if (!dragging) {
                        seek.setProgress(pos);
                        timeCur.setText(Util.time(pos));
                    }
                    long now = SystemClock.uptimeMillis();
                    if (now - lastSave > 5000 && mp.isPlaying()) {
                        lastSave = now;
                        saveProgress();
                    }
                } catch (Exception ignore) {
                }
                h.postDelayed(this, 250);
            }
        }
    };

    public void togglePlay() {
        if (mp == null || !prepared) return;
        try {
            if (mp.isPlaying()) {
                mp.pause();
            } else {
                requestFocus2();
                mp.start();
                applySpeed();
            }
        } catch (Exception ignore) {
        }
        updatePlayIcon();
    }

    private ImageView flat(int type, int sizeDp) {
        ImageView v = new ImageView(act);
        v.setImageDrawable(new Icon(type, 0xFFFFFFFF, dp(sizeDp), false));
        v.setPadding(dp(10), dp(8), dp(10), dp(8));
        v.setClickable(true);
        return v;
    }

    private void updateOpts() {
        btnLoop.setImageDrawable(new Icon(Icon.ROTATE, loop ? RED : 0xFFFFFFFF, dp(22), false));
        btnMute.setImageDrawable(new Icon(muted ? Icon.MUTE : Icon.VOL, 0xFFFFFFFF, dp(22), false));
    }

    private void updateFav() {
        boolean fav = curVideo != null && Util.isFav(prefs, curVideo.id);
        btnFav.setImageDrawable(new Icon(Icon.HEART, fav ? RED : 0xFFFFFFFF, dp(20), fav));
    }

    private void updatePlayIcon() {
        boolean playing = false;
        try {
            playing = mp != null && prepared && mp.isPlaying();
        } catch (Exception ignore) {
        }
        btnPlay.setImageDrawable(new Icon(playing ? Icon.PAUSE : Icon.PLAY, 0xFFFFFFFF, dp(26), false));
    }

    private void seekBy(int delta) {
        if (mp == null || !prepared) return;
        try {
            int t = mp.getCurrentPosition() + delta;
            t = Math.max(0, Math.min(Math.max(0, mp.getDuration() - 200), t));
            seekMs(t);
            seek.setProgress(t);
            timeCur.setText(Util.time(t));
        } catch (Exception ignore) {
        }
    }

    /** Frame-accurate seek on API 26+ (via reflection so old android.jar still compiles). */
    private void seekMs(int t) {
        if (mp == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                if (!seekPreciseTried) {
                    seekPreciseTried = true;
                    seekPrecise = MediaPlayer.class.getMethod("seekTo", long.class, int.class);
                }
                if (seekPrecise != null) {
                    seekPrecise.invoke(mp, Long.valueOf(t), Integer.valueOf(3));
                    return;
                }
            } catch (Throwable ignore) {
            }
        }
        try {
            mp.seekTo(t);
        } catch (Exception ignore) {
        }
    }

    private void setSpeed(float s) {
        s = Math.max(0.25f, Math.min(4f, s));
        speed = Math.round(s * 20f) / 20f;
        updateSpeedUi();
        applySpeed();
    }

    private void applySpeed() {
        if (mp == null || !prepared) return;
        float eff = boost ? Math.max(2f, speed) : speed;
        try {
            boolean playing = mp.isPlaying();
            PlaybackParams pp = mp.getPlaybackParams();
            pp.setSpeed(eff);
            mp.setPlaybackParams(pp);
            if (!playing) mp.pause();
        } catch (Exception e) {
            if (eff != 1f) {
                Toast.makeText(act, "This device cannot play at " + Util.speed(eff), Toast.LENGTH_SHORT).show();
                speed = 1f;
                boost = false;
                updateSpeedUi();
            }
        }
    }

    private void applyMute() {
        if (mp == null) return;
        try {
            float v = muted ? 0f : 1f;
            mp.setVolume(v, v);
        } catch (Exception ignore) {
        }
    }

    private void updateSpeedUi() {
        speedLabel.setText("Speed " + Util.speed(speed));
        btnSpeed.setText(Util.speed(speed));
        for (int i = 0; i < SPEEDS.length; i++) {
            boolean sel = Math.abs(SPEEDS[i] - speed) < 0.001f;
            speedPills[i].setBackground(Util.rounded(act, sel ? RED : 0x33FFFFFF, 18));
        }
        speedSeek.setProgress(Math.round((speed - 0.25f) / 0.05f));
    }

    // ------------------------------------------------------------------ audio focus

    private final AudioManager.OnAudioFocusChangeListener focusL = new AudioManager.OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int change) {
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                try {
                    if (mp != null && prepared && mp.isPlaying()) {
                        mp.pause();
                        updatePlayIcon();
                    }
                } catch (Exception ignore) {
                }
            }
        }
    };

    private void requestFocus2() {
        try {
            am.requestAudioFocus(focusL, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        } catch (Exception ignore) {
        }
    }

    private void abandonFocus() {
        try {
            am.abandonAudioFocus(focusL);
        } catch (Exception ignore) {
        }
    }

    // ------------------------------------------------------------------ viewport

    private void setMode(int m) {
        mode = m;
        zoom = 1f;
        panX = 0f;
        panY = 0f;
        applyTransform();
        updateModeUi();
        showHud(MODE_NAMES[m]);
    }

    private void setZoom(float z) {
        zoom = Math.max(1f, Math.min(8f, z));
        applyTransform();
        updateModeUi();
        showHud(Math.round(zoom * 100) + "%");
    }

    private void updateModeUi() {
        btnAspect.setText(MODE_NAMES[mode]);
        for (int i = 0; i < MODE_NAMES.length; i++) {
            modePills[i].setBackground(Util.rounded(act, i == mode ? RED : 0x33FFFFFF, 18));
        }
        zoomLabel.setText(Math.round(zoom * 100) + "%");
    }

    private void applyTransform() {
        int W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0) return;
        float va = (vw > 0 && vh > 0) ? (float) vw / (float) vh : 16f / 9f;
        float ar = MODE_ASPECT[mode];
        float a = ar > 0f ? ar : va;
        float dw, dh;
        if (mode == M_STRETCH) {
            dw = W;
            dh = H;
        } else if (mode == M_FILL) {
            dw = Math.max((float) W, H * a);
            dh = dw / a;
        } else if (mode == M_ORIG && vw > 0 && vh > 0) {
            dw = vw;
            dh = vh;
        } else {
            dw = Math.min((float) W, H * a);
            dh = dw / a;
        }
        float maxX = Math.max(0f, (dw * zoom - W) / 2f);
        float maxY = Math.max(0f, (dh * zoom - H) / 2f);
        panX = Math.max(-maxX, Math.min(maxX, panX));
        panY = Math.max(-maxY, Math.min(maxY, panY));
        Matrix m = new Matrix();
        m.setScale(dw / W * zoom, dh / H * zoom, W / 2f, H / 2f);
        m.postTranslate(panX, panY);
        tv.setTransform(m);
    }

    @Override
    protected void onSizeChanged(int w, int hh, int ow, int oh) {
        super.onSizeChanged(w, hh, ow, oh);
        applyTransform();
    }

    private void applyOrientation() {
        int o;
        switch (orientMode) {
            case 1:
                o = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
                break;
            case 2:
                o = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
                break;
            case 3:
                o = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
                break;
            case 4:
                o = ActivityInfo.SCREEN_ORIENTATION_LOCKED;
                break;
            default:
                o = (vw > 0 && vh > vw) ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
        }
        try {
            act.setRequestedOrientation(o);
        } catch (Throwable ignore) {
        }
    }

    // ------------------------------------------------------------------ surface

    public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int hh) {
        surface = new Surface(st);
        if (pendingStart) {
            pendingStart = false;
            createPlayer();
        } else if (mp != null) {
            try {
                mp.setSurface(surface);
            } catch (Exception ignore) {
            }
        }
        applyTransform();
    }

    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int hh) {
        applyTransform();
    }

    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        if (mp != null) {
            try {
                mp.setSurface(null);
            } catch (Exception ignore) {
            }
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
        return true;
    }

    public void onSurfaceTextureUpdated(SurfaceTexture st) {
    }

    // ------------------------------------------------------------------ controls visibility

    private final Runnable autoHide = new Runnable() {
        public void run() {
            boolean playing = false;
            try {
                playing = mp != null && prepared && mp.isPlaying();
            } catch (Exception ignore) {
            }
            if (playing && !dragging && !anyPanelOpen()) hideControls();
        }
    };

    private final Runnable hideHud = new Runnable() {
        public void run() {
            hud.setVisibility(GONE);
        }
    };

    private final Runnable hideUnlock = new Runnable() {
        public void run() {
            unlockBtn.setVisibility(GONE);
        }
    };

    private boolean anyPanelOpen() {
        return speedPanel.getVisibility() == VISIBLE || aspectPanel.getVisibility() == VISIBLE
            || audioPanel.getVisibility() == VISIBLE;
    }

    private void hidePanels() {
        speedPanel.setVisibility(GONE);
        aspectPanel.setVisibility(GONE);
        audioPanel.setVisibility(GONE);
    }

    private void togglePanel(LinearLayout p) {
        boolean open = p.getVisibility() == VISIBLE;
        hidePanels();
        if (!open) p.setVisibility(VISIBLE);
        showControls();
    }

    private void showControls() {
        if (locked) {
            showUnlock();
            return;
        }
        controls.setVisibility(VISIBLE);
        controlsVisible = true;
        scheduleHide();
    }

    private void hideControls() {
        hidePanels();
        controls.setVisibility(GONE);
        controlsVisible = false;
    }

    private void scheduleHide() {
        h.removeCallbacks(autoHide);
        if (controlsVisible) h.postDelayed(autoHide, 4000);
    }

    private void showUnlock() {
        unlockBtn.setVisibility(VISIBLE);
        h.removeCallbacks(hideUnlock);
        h.postDelayed(hideUnlock, 2500);
    }

    private void showHud(String s) {
        hud.setText(s);
        hud.setVisibility(VISIBLE);
        h.removeCallbacks(hideHud);
        h.postDelayed(hideHud, 800);
    }

    private void showError(String m) {
        errorText.setText(m);
        errorText.setVisibility(VISIBLE);
        showControls();
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            multi = false;
            dragMode = D_NONE;
        }
        if (e.getPointerCount() > 1) multi = true;
        if (!locked) sgd.onTouchEvent(e);
        if (!multi) gd.onTouchEvent(e);
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) endDrag();
        return true;
    }

    private void onTap() {
        if (locked) {
            showUnlock();
            return;
        }
        if (controlsVisible) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void onDouble(MotionEvent e) {
        if (locked) return;
        float x = e.getX() / Math.max(1, getWidth());
        if (x < 0.35f) {
            seekBy(-10000);
            showHud("-10s");
        } else if (x > 0.65f) {
            seekBy(10000);
            showHud("+10s");
        } else {
            togglePlay();
        }
    }

    private boolean onDrag(MotionEvent e1, MotionEvent e2, float dx, float dy) {
        if (locked || multi || e1 == null) return false;
        if (dragMode == D_NONE) {
            float tx = e2.getX() - e1.getX();
            float ty = e2.getY() - e1.getY();
            if (zoom > 1.01f) {
                dragMode = D_PAN;
            } else if (mp == null || !prepared) {
                return false;
            } else if (Math.abs(tx) > Math.abs(ty)) {
                dragMode = D_SEEK;
                scrubStart = mp.getCurrentPosition();
                scrubTarget = scrubStart;
            } else if (e1.getX() < getWidth() / 2f) {
                dragMode = D_BRIGHT;
                brightStart = currentBrightness();
            } else {
                dragMode = D_VOL;
                int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                volStart = am.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max;
            }
        }
        float totalX = e2.getX() - e1.getX();
        float totalY = e1.getY() - e2.getY();
        if (dragMode == D_PAN) {
            panX -= dx;
            panY -= dy;
            applyTransform();
        } else if (dragMode == D_SEEK) {
            int range = Math.max(60000, Math.min(dur, 600000));
            int t = (int) (scrubStart + totalX / Math.max(1, getWidth()) * range);
            scrubTarget = Math.max(0, Math.min(Math.max(0, dur - 200), t));
            long d = (scrubTarget - scrubStart) / 1000;
            showHud(Util.time(scrubTarget) + " / " + Util.time(dur) + "\n" + (d >= 0 ? "+" : "") + d + "s");
            seek.setProgress(scrubTarget);
            timeCur.setText(Util.time(scrubTarget));
        } else if (dragMode == D_BRIGHT) {
            float b = Math.max(0.02f, Math.min(1f, brightStart + totalY / Math.max(1, getHeight()) * 1.2f));
            try {
                WindowManager.LayoutParams lp = act.getWindow().getAttributes();
                lp.screenBrightness = b;
                act.getWindow().setAttributes(lp);
            } catch (Throwable ignore) {
            }
            showHud("Brightness " + Math.round(b * 100) + "%");
        } else if (dragMode == D_VOL) {
            float f = Math.max(0f, Math.min(1f, volStart + totalY / Math.max(1, getHeight()) * 1.2f));
            int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            try {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(f * max), 0);
            } catch (Exception ignore) {
            }
            showHud("Volume " + Math.round(f * 100) + "%");
        }
        return true;
    }

    private float currentBrightness() {
        try {
            float b = act.getWindow().getAttributes().screenBrightness;
            if (b >= 0f) return b;
            return Settings.System.getInt(act.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 255f;
        } catch (Throwable e) {
            return 0.5f;
        }
    }

    private void endDrag() {
        if (dragMode == D_SEEK && mp != null && prepared) seekMs(scrubTarget);
        dragMode = D_NONE;
        if (boost) {
            boost = false;
            applySpeed();
            showHud(Util.speed(speed));
        }
    }
}
