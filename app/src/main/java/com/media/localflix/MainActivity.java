package com.media.localflix;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class MainActivity extends Activity {

    static final int BG = 0xFF090909, PANEL = 0xFF141414, RED = 0xFFE50914, MUTED = 0xFF999999;
    static final int REQ_PERM = 7;
    static final String[] SORTS = {"Newest", "Name", "Longest", "Largest"};
    static final String[] TAB_NAMES = {"Home", "Folders", "Favorites", "Settings"};
    static final int[] TAB_ICONS = {Icon.HOME, Icon.FOLDER, Icon.HEART, Icon.GEAR};
    static final String[] ORIENTS = {"Auto (by video)", "Landscape", "Portrait", "Free (sensor)", "Locked"};

    SharedPreferences prefs;
    ThumbLoader thumbs;
    FrameLayout root;
    LinearLayout library;
    GridView grid;
    ScrollView settingsView;
    FrameLayout gridHolder;
    EditText search;
    TextView countView, emptyView, sortBtn, titleView;
    ImageView backBtn;
    ImageView[] navIcons = new ImageView[4];
    TextView[] navLabels = new TextView[4];
    PlayerView player;
    VideoAdapter adapter;
    final List<Video> all = new ArrayList<Video>();
    final List<Video> shown = new ArrayList<Video>();
    final List<String> folderNames = new ArrayList<String>();
    final TreeMap<String, List<Video>> groups = new TreeMap<String, List<Video>>();
    int tab = 0;
    String folder = null;
    int sort = 0, cols = 2, colW = 300, thumbH = 170;
    boolean loading;
    TextView orientBtn;
    LinearLayout cwSection, cwRow;

    int dp(float v) {
        return Util.dp(this, v);
    }

    boolean foldersMode() {
        return tab == 1 && folder == null;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("lf", MODE_PRIVATE);
        thumbs = new ThumbLoader(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(BG));
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(0xFF111111);
        buildUi();
        setContentView(root);
        refreshUi();
        ensurePermission();
    }

    // ------------------------------------------------------------------ UI

    void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        library = new LinearLayout(this);
        library.setOrientation(LinearLayout.VERTICAL);

        // header
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(14), dp(10), dp(14), dp(6));

        backBtn = Util.iconBtn(this, Icon.BACK, 18);
        backBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                folder = null;
                refreshUi();
            }
        });
        LinearLayout.LayoutParams bkp = new LinearLayout.LayoutParams(-2, -2);
        bkp.rightMargin = dp(10);
        head.addView(backBtn, bkp);

        titleView = new TextView(this);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(22);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        sortBtn = Util.pill(this, "Sort: " + SORTS[0]);
        sortBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sort = (sort + 1) % SORTS.length;
                sortBtn.setText("Sort: " + SORTS[sort]);
                applyFilter();
            }
        });
        ImageView scan = Util.iconBtn(this, Icon.ROTATE, 18);
        scan.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ensurePermission();
            }
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
        bp.leftMargin = dp(6);
        head.addView(sortBtn, bp);
        head.addView(scan, bp);
        library.addView(head, new LinearLayout.LayoutParams(-1, -2));

        search = new EditText(this);
        search.setHint("Search videos");
        search.setHintTextColor(MUTED);
        search.setTextColor(Color.WHITE);
        search.setTextSize(15);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setBackground(Util.rounded(this, 0xFF1C1C1C, 10));
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable e) {
                applyFilter();
            }
        });
        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent ev) {
                hideKeyboard();
                return true;
            }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.setMargins(dp(14), dp(2), dp(14), dp(6));
        library.addView(search, sp);

        countView = new TextView(this);
        countView.setTextColor(MUTED);
        countView.setTextSize(12);
        countView.setPadding(dp(16), dp(2), dp(16), dp(6));
        library.addView(countView, new LinearLayout.LayoutParams(-1, -2));

        // Iteration 2 — Continue Watching strip (Home tab only, see refreshCw).
        cwSection = new LinearLayout(this);
        cwSection.setOrientation(LinearLayout.VERTICAL);
        cwSection.setVisibility(View.GONE);
        TextView cwTitle = new TextView(this);
        cwTitle.setText("CONTINUE WATCHING");
        cwTitle.setTextColor(RED);
        cwTitle.setTextSize(12);
        cwTitle.setTypeface(null, Typeface.BOLD);
        cwTitle.setPadding(dp(16), dp(4), dp(16), dp(6));
        cwSection.addView(cwTitle, new LinearLayout.LayoutParams(-1, -2));
        HorizontalScrollView cwScroll = new HorizontalScrollView(this);
        cwScroll.setHorizontalScrollBarEnabled(false);
        cwRow = new LinearLayout(this);
        cwRow.setOrientation(LinearLayout.HORIZONTAL);
        cwRow.setPadding(dp(12), 0, dp(12), dp(10));
        cwScroll.addView(cwRow, new LinearLayout.LayoutParams(-2, -2));
        cwSection.addView(cwScroll, new LinearLayout.LayoutParams(-1, -2));
        library.addView(cwSection, new LinearLayout.LayoutParams(-1, -2));

        // content
        FrameLayout content = new FrameLayout(this);
        gridHolder = new FrameLayout(this);
        grid = new GridView(this);
        computeCols();
        grid.setNumColumns(cols);
        grid.setHorizontalSpacing(dp(10));
        grid.setVerticalSpacing(dp(12));
        grid.setPadding(dp(12), dp(4), dp(12), dp(12));
        grid.setClipToPadding(false);
        grid.setSelector(new ColorDrawable(0));
        grid.setFastScrollEnabled(true);
        adapter = new VideoAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (foldersMode()) {
                    if (pos < folderNames.size()) {
                        folder = folderNames.get(pos);
                        search.setText("");
                        refreshUi();
                    }
                } else {
                    play(pos);
                }
            }
        });
        gridHolder.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        emptyView = new TextView(this);
        emptyView.setTextColor(MUTED);
        emptyView.setTextSize(15);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(30), dp(30), dp(30), dp(30));
        emptyView.setVisibility(View.GONE);
        gridHolder.addView(emptyView, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        content.addView(gridHolder, new FrameLayout.LayoutParams(-1, -1));

        settingsView = new ScrollView(this);
        settingsView.addView(buildSettings());
        settingsView.setVisibility(View.GONE);
        content.addView(settingsView, new FrameLayout.LayoutParams(-1, -1));
        library.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        // bottom nav
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(0xFF111111);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(8), 0, dp(8));
            navIcons[i] = new ImageView(this);
            navIcons[i].setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(24)));
            navLabels[i] = new TextView(this);
            navLabels[i].setText(TAB_NAMES[i]);
            navLabels[i].setTextSize(11);
            navLabels[i].setGravity(Gravity.CENTER);
            item.addView(navIcons[i]);
            item.addView(navLabels[i]);
            item.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    setTab(idx);
                }
            });
            nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        library.addView(nav, new LinearLayout.LayoutParams(-1, -2));

        root.addView(library, new FrameLayout.LayoutParams(-1, -1));

        player = new PlayerView(this);
        player.setListener(new PlayerView.Listener() {
            public void onClosed() {
                setFullMode(false);
                applyFilter();
            }
        });
        root.addView(player, new FrameLayout.LayoutParams(-1, -1));
    }

    LinearLayout buildSettings() {
        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setPadding(dp(16), dp(8), dp(16), dp(24));

        s.addView(sectionTitle("Playback"));
        s.addView(switchRow("Resume where you left off", "resume"));
        s.addView(switchRow("Autoplay next video", "autonext"));

        orientBtn = Util.pill(this, "");
        orientBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int o = (prefs.getInt("orient", 0) + 1) % ORIENTS.length;
                prefs.edit().putInt("orient", o).apply();
                updateOrientBtn();
            }
        });
        LinearLayout orow = row("Player orientation");
        orow.addView(orientBtn);
        s.addView(orow);
        updateOrientBtn();

        s.addView(sectionTitle("Data"));
        s.addView(actionRow("Clear watch history", new Runnable() {
            public void run() {
                SharedPreferences.Editor ed = prefs.edit();
                for (String k : prefs.getAll().keySet()) {
                    if (k.startsWith("pos_") || k.startsWith("time_")) ed.remove(k);
                }
                ed.apply();
                adapter.notifyDataSetChanged();
                Toast.makeText(MainActivity.this, "Watch history cleared", Toast.LENGTH_SHORT).show();
            }
        }));
        s.addView(actionRow("Clear favorites", new Runnable() {
            public void run() {
                prefs.edit().remove("favs").apply();
                applyFilter();
                Toast.makeText(MainActivity.this, "Favorites cleared", Toast.LENGTH_SHORT).show();
            }
        }));

        s.addView(sectionTitle("About"));
        s.addView(info("LocalFlix", "Version 1.0.0 + Continue Watching"));
        s.addView(info("Creator", "Aby Bijo"));
        s.addView(actionRow("GitHub: AbyBijo  (github.com/AbyBijo)", new Runnable() {
            public void run() {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AbyBijo")));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "github.com/AbyBijo", Toast.LENGTH_LONG).show();
                }
            }
        }));
        return s;
    }

    void updateOrientBtn() {
        orientBtn.setText(ORIENTS[prefs.getInt("orient", 0)]);
    }

    TextView sectionTitle(String t) {
        TextView v = new TextView(this);
        v.setText(t.toUpperCase(Locale.US));
        v.setTextColor(RED);
        v.setTextSize(12);
        v.setTypeface(null, Typeface.BOLD);
        v.setPadding(dp(4), dp(20), 0, dp(6));
        return v;
    }

    LinearLayout row(String label) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), dp(12), dp(14), dp(12));
        r.setBackground(Util.rounded(this, PANEL, 8));
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(Color.WHITE);
        t.setTextSize(15);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(6);
        r.setLayoutParams(lp);
        return r;
    }

    LinearLayout switchRow(String label, final String key) {
        LinearLayout r = row(label);
        Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean(key, true));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                prefs.edit().putBoolean(key, on).apply();
            }
        });
        r.addView(sw);
        return r;
    }

    LinearLayout actionRow(String label, final Runnable run) {
        LinearLayout r = row(label);
        r.setClickable(true);
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                run.run();
            }
        });
        return r;
    }

    LinearLayout info(String label, String value) {
        LinearLayout r = row(label);
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(MUTED);
        v.setTextSize(15);
        r.addView(v);
        return r;
    }

    void computeCols() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float widthDp = dm.widthPixels / dm.density;
        cols = Math.max(2, (int) (widthDp / 190f));
        int pad = dp(12) * 2;
        int gap = dp(10) * (cols - 1);
        colW = Math.max(100, (dm.widthPixels - pad - gap) / cols);
        thumbH = colW * 9 / 16;
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        computeCols();
        grid.setNumColumns(cols);
        adapter.notifyDataSetChanged();
    }

    void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
            search.clearFocus();
        } catch (Exception ignore) {
        }
    }

    void setFullMode(boolean on) {
        View d = getWindow().getDecorView();
        if (on) {
            d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
        library.setVisibility(on ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------------ tabs

    void setTab(int t) {
        tab = t;
        folder = null;
        hideKeyboard();
        refreshUi();
    }

    void refreshUi() {
        boolean settings = tab == 3;
        boolean list = foldersMode();
        boolean inFolder = tab == 1 && folder != null;
        backBtn.setVisibility(inFolder ? View.VISIBLE : View.GONE);
        if (inFolder) {
            titleView.setText(folder);
        } else if (tab == 0) {
            SpannableStringBuilder sb = new SpannableStringBuilder("LOCALFLIX");
            sb.setSpan(new ForegroundColorSpan(RED), 5, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            titleView.setText(sb);
        } else {
            titleView.setText(TAB_NAMES[tab]);
        }
        search.setVisibility(settings || list ? View.GONE : View.VISIBLE);
        sortBtn.setVisibility(settings || list ? View.GONE : View.VISIBLE);
        countView.setVisibility(settings ? View.GONE : View.VISIBLE);
        gridHolder.setVisibility(settings ? View.GONE : View.VISIBLE);
        settingsView.setVisibility(settings ? View.VISIBLE : View.GONE);
        for (int i = 0; i < 4; i++) {
            int col = i == tab ? RED : MUTED;
            navIcons[i].setImageDrawable(new Icon(TAB_ICONS[i], col, dp(24), i == 2 && tab == 2));
            navLabels[i].setTextColor(col);
        }
        if (!settings) applyFilter();
        refreshCw();
    }

    // ------------------------------------------------------------------ data

    // Iteration 1 fix: Android 13+ no longer grants READ_EXTERNAL_STORAGE
    // for media. Request READ_MEDIA_VIDEO there, the old permission below it.
    static String videoPermission() {
        return Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_VIDEO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    void ensurePermission() {
        if (checkSelfPermission(videoPermission()) == PackageManager.PERMISSION_GRANTED) {
            scan();
        } else {
            requestPermissions(new String[] {videoPermission()}, REQ_PERM);
        }
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] perms, int[] res) {
        super.onRequestPermissionsResult(rc, perms, res);
        if (rc == REQ_PERM) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
                scan();
            } else {
                setEmpty("Storage permission denied.\nAllow it in Settings > Apps > LocalFlix > Permissions, then tap the scan button.");
            }
        }
    }

    void scan() {
        if (loading) return;
        loading = true;
        countView.setText("Scanning...");
        new Thread(new Runnable() {
            public void run() {
                final List<Video> found = VideoRepo.load(MainActivity.this);
                runOnUiThread(new Runnable() {
                    public void run() {
                        loading = false;
                        all.clear();
                        all.addAll(found);
                        refreshUi();
                    }
                });
            }
        }).start();
    }

    void setEmpty(String msg) {
        emptyView.setText(msg);
        emptyView.setVisibility(View.VISIBLE);
        countView.setText("");
    }

    void applyFilter() {
        if (tab == 3) return;
        shown.clear();
        folderNames.clear();
        groups.clear();
        if (foldersMode()) {
            for (Video v : all) {
                List<Video> l = groups.get(v.folder);
                if (l == null) {
                    l = new ArrayList<Video>();
                    groups.put(v.folder, l);
                }
                l.add(v);
            }
            folderNames.addAll(groups.keySet());
            adapter.notifyDataSetChanged();
            countView.setText(folderNames.size() + " folder" + (folderNames.size() == 1 ? "" : "s"));
            if (folderNames.isEmpty()) {
                setEmpty("No videos found.\nPut videos on the device and tap the scan button.");
            } else {
                emptyView.setVisibility(View.GONE);
            }
            return;
        }
        String q = search.getText().toString().trim().toLowerCase(Locale.US);
        for (Video v : all) {
            if (tab == 1 && !folder.equals(v.folder)) continue;
            if (tab == 2 && !Util.isFav(prefs, v.id)) continue;
            if (q.length() > 0 && !(v.name + " " + v.folder).toLowerCase(Locale.US).contains(q)) continue;
            shown.add(v);
        }
        Comparator<Video> cmp;
        switch (sort) {
            case 1:
                cmp = new Comparator<Video>() {
                    public int compare(Video a, Video b) {
                        return a.name.compareToIgnoreCase(b.name);
                    }
                };
                break;
            case 2:
                cmp = new Comparator<Video>() {
                    public int compare(Video a, Video b) {
                        return Long.valueOf(b.duration).compareTo(Long.valueOf(a.duration));
                    }
                };
                break;
            case 3:
                cmp = new Comparator<Video>() {
                    public int compare(Video a, Video b) {
                        return Long.valueOf(b.size).compareTo(Long.valueOf(a.size));
                    }
                };
                break;
            default:
                cmp = new Comparator<Video>() {
                    public int compare(Video a, Video b) {
                        return Long.valueOf(b.added).compareTo(Long.valueOf(a.added));
                    }
                };
        }
        Collections.sort(shown, cmp);
        adapter.notifyDataSetChanged();
        countView.setText(shown.size() + " video" + (shown.size() == 1 ? "" : "s"));
        if (shown.isEmpty()) {
            String m;
            if (all.isEmpty()) m = "No videos found.\nPut videos on the device and tap the scan button.";
            else if (tab == 2 && search.getText().length() == 0) m = "No favorites yet.\nTap the heart on a video.";
            else m = "No match.";
            setEmpty(m);
            countView.setText("0 videos");
        } else {
            emptyView.setVisibility(View.GONE);
        }
    }

    void play(int pos) {
        if (pos < 0 || pos >= shown.size()) return;
        hideKeyboard();
        setFullMode(true);
        player.open(new ArrayList<Video>(shown), pos);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onResume() {
        super.onResume();
        if (all.isEmpty() && !loading
            && checkSelfPermission(videoPermission()) == PackageManager.PERMISSION_GRANTED) {
            scan();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        player.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        player.shutdown();
        thumbs.shutdown();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && player != null && player.isOpen()) setFullMode(true);
    }

    @Override
    public void onBackPressed() {
        if (player.isOpen()) {
            player.onBack();
            return;
        }
        if (tab == 1 && folder != null) {
            folder = null;
            refreshUi();
            return;
        }
        if (tab != 0) {
            setTab(0);
            return;
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------------------ Continue Watching

    void refreshCw() {
        if (cwSection == null) return;
        boolean show = tab == 0 && !foldersMode()
            && search.getText().toString().trim().length() == 0;
        if (!show) {
            cwSection.setVisibility(View.GONE);
            return;
        }
        List<ContinueWatching.Entry> entries = ContinueWatching.list(prefs, all);
        if (entries.isEmpty()) {
            cwSection.setVisibility(View.GONE);
            return;
        }
        cwSection.setVisibility(View.VISIBLE);
        cwRow.removeAllViews();
        int n = Math.min(entries.size(), 10);
        for (int i = 0; i < n; i++) {
            final ContinueWatching.Entry e = entries.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(Util.rounded(this, PANEL, 6));
            FrameLayout box = new FrameLayout(this);
            box.setBackgroundColor(0xFF222222);
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            box.addView(img, new FrameLayout.LayoutParams(-1, -1));
            View bar = new View(this);
            bar.setBackgroundColor(RED);
            int barW = Math.max(dp(2), (int) (dp(160) * e.progress));
            box.addView(bar, new FrameLayout.LayoutParams(barW, dp(3), Gravity.BOTTOM | Gravity.LEFT));
            card.addView(box, new LinearLayout.LayoutParams(dp(160), dp(90)));
            TextView title = new TextView(this);
            title.setTextColor(Color.WHITE);
            title.setTextSize(13);
            title.setTypeface(null, Typeface.BOLD);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setPadding(dp(8), dp(7), dp(8), 0);
            title.setText(e.video.episodeTitle != null ? e.video.episodeTitle : e.video.name);
            card.addView(title, new LinearLayout.LayoutParams(-1, -2));
            TextView meta = new TextView(this);
            meta.setTextColor(MUTED);
            meta.setTextSize(11);
            meta.setSingleLine(true);
            meta.setEllipsize(TextUtils.TruncateAt.END);
            meta.setPadding(dp(8), dp(2), dp(8), dp(9));
            String label = e.label();
            String metaText = (label.length() > 0 ? label + " \u2022 " : "")
                + Util.time(e.remaining()) + " left";
            Video next = ContinueWatching.nextEpisode(all, e.video);
            if (next != null) {
                metaText += " \u2022 Next: S" + next.season + " E" + next.episode;
            }
            meta.setText(metaText);
            card.addView(meta, new LinearLayout.LayoutParams(-1, -2));
            card.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    playVideo(e.video);
                }
            });
            thumbs.load(e.video, img);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(10);
            cwRow.addView(card, lp);
        }
    }

    void playVideo(Video v) {
        int idx = shown.indexOf(v);
        if (idx >= 0) {
            play(idx);
            return;
        }
        hideKeyboard();
        setFullMode(true);
        List<Video> single = new ArrayList<Video>();
        single.add(v);
        player.open(single, 0);
    }

    // ------------------------------------------------------------------ grid

    class VideoAdapter extends BaseAdapter {
        public int getCount() {
            return foldersMode() ? folderNames.size() : shown.size();
        }

        public Object getItem(int i) {
            return null;
        }

        public long getItemId(int i) {
            return i;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int i) {
            return foldersMode() ? 1 : 0;
        }

        public View getView(int pos, View cv, ViewGroup parent) {
            if (foldersMode()) {
                FolderCard f = cv == null ? new FolderCard(MainActivity.this) : (FolderCard) cv;
                if (pos < folderNames.size()) f.bind(folderNames.get(pos));
                return f;
            }
            Card c = cv == null ? new Card(MainActivity.this) : (Card) cv;
            if (pos < shown.size()) c.bind(shown.get(pos));
            return c;
        }
    }

    class Card extends LinearLayout {
        final FrameLayout box;
        final ImageView img, heart;
        final TextView dur, title, meta;
        final View bar;
        Video cur;

        Card(Context c) {
            super(c);
            setOrientation(VERTICAL);
            setBackground(Util.rounded(c, PANEL, 6));

            box = new FrameLayout(c);
            box.setBackgroundColor(0xFF222222);

            ImageView ph = new ImageView(c);
            ph.setImageDrawable(new Icon(Icon.PLAY, 0xFF444444, dp(34), false));
            box.addView(ph, new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER));

            img = new ImageView(c);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            box.addView(img, new FrameLayout.LayoutParams(-1, -1));

            dur = new TextView(c);
            dur.setTextColor(Color.WHITE);
            dur.setTextSize(11);
            dur.setPadding(dp(5), dp(1), dp(5), dp(1));
            dur.setBackground(Util.rounded(c, 0xCC000000, 4));
            FrameLayout.LayoutParams dp1 = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.RIGHT);
            dp1.setMargins(0, 0, dp(6), dp(8));
            box.addView(dur, dp1);

            heart = new ImageView(c);
            heart.setPadding(dp(6), dp(6), dp(6), dp(6));
            heart.setBackground(Util.rounded(c, 0x66000000, 16));
            FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.TOP | Gravity.RIGHT);
            hp.setMargins(0, dp(6), dp(6), 0);
            box.addView(heart, hp);
            heart.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (cur == null) return;
                    Util.toggleFav(prefs, cur.id);
                    if (tab == 2) applyFilter();
                    else adapter.notifyDataSetChanged();
                }
            });

            bar = new View(c);
            bar.setBackgroundColor(RED);
            box.addView(bar, new FrameLayout.LayoutParams(0, dp(3), Gravity.BOTTOM | Gravity.LEFT));

            addView(box, new LinearLayout.LayoutParams(-1, thumbH));

            title = new TextView(c);
            title.setTextColor(Color.WHITE);
            title.setTextSize(13);
            title.setTypeface(null, Typeface.BOLD);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setPadding(dp(8), dp(7), dp(8), 0);
            addView(title, new LinearLayout.LayoutParams(-1, -2));

            meta = new TextView(c);
            meta.setTextColor(MUTED);
            meta.setTextSize(11);
            meta.setSingleLine(true);
            meta.setEllipsize(TextUtils.TruncateAt.END);
            meta.setPadding(dp(8), dp(2), dp(8), dp(9));
            addView(meta, new LinearLayout.LayoutParams(-1, -2));
        }

        void bind(Video v) {
            cur = v;
            box.getLayoutParams().height = thumbH;
            title.setText(v.name);
            meta.setText(v.folder + " \u2022 " + Util.size(v.size));
            if (v.duration > 0) {
                dur.setText(Util.time(v.duration));
                dur.setVisibility(View.VISIBLE);
            } else {
                dur.setVisibility(View.GONE);
            }
            boolean fav = Util.isFav(prefs, v.id);
            heart.setImageDrawable(new Icon(Icon.HEART, fav ? RED : Color.WHITE, dp(20), fav));
            long pos = prefs.getLong("pos_" + v.id, 0L);
            FrameLayout.LayoutParams bp = (FrameLayout.LayoutParams) bar.getLayoutParams();
            if (pos > 0 && v.duration > 0) {
                bp.width = Math.max(dp(2), (int) (colW * (pos / (float) v.duration)));
                bar.setVisibility(View.VISIBLE);
            } else {
                bp.width = 0;
                bar.setVisibility(View.GONE);
            }
            bar.setLayoutParams(bp);
            thumbs.load(v, img);
        }
    }

    class FolderCard extends LinearLayout {
        final FrameLayout box;
        final ImageView img;
        final TextView title, meta, badge;

        FolderCard(Context c) {
            super(c);
            setOrientation(VERTICAL);
            setBackground(Util.rounded(c, PANEL, 6));

            box = new FrameLayout(c);
            box.setBackgroundColor(0xFF222222);
            img = new ImageView(c);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            box.addView(img, new FrameLayout.LayoutParams(-1, -1));

            View shade = new View(c);
            shade.setBackgroundColor(0x66000000);
            box.addView(shade, new FrameLayout.LayoutParams(-1, -1));

            ImageView fi = new ImageView(c);
            fi.setImageDrawable(new Icon(Icon.FOLDER, Color.WHITE, dp(40), false));
            box.addView(fi, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));

            badge = new TextView(c);
            badge.setTextColor(Color.WHITE);
            badge.setTextSize(11);
            badge.setPadding(dp(6), dp(1), dp(6), dp(1));
            badge.setBackground(Util.rounded(c, RED, 8));
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.RIGHT);
            bp.setMargins(0, 0, dp(6), dp(6));
            box.addView(badge, bp);

            addView(box, new LinearLayout.LayoutParams(-1, thumbH));

            title = new TextView(c);
            title.setTextColor(Color.WHITE);
            title.setTextSize(14);
            title.setTypeface(null, Typeface.BOLD);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setPadding(dp(8), dp(7), dp(8), 0);
            addView(title, new LinearLayout.LayoutParams(-1, -2));

            meta = new TextView(c);
            meta.setTextColor(MUTED);
            meta.setTextSize(11);
            meta.setPadding(dp(8), dp(2), dp(8), dp(9));
            addView(meta, new LinearLayout.LayoutParams(-1, -2));
        }

        void bind(String name) {
            box.getLayoutParams().height = thumbH;
            List<Video> l = groups.get(name);
            title.setText(name);
            int n = l == null ? 0 : l.size();
            meta.setText(n + " video" + (n == 1 ? "" : "s"));
            badge.setText(String.valueOf(n));
            if (n > 0) {
                thumbs.load(l.get(0), img);
            } else {
                img.setImageDrawable(null);
            }
        }
    }
}
