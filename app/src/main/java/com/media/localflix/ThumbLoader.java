package com.media.localflix;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThumbLoader {
    private final Context ctx;
    private final LruCache<Long, Bitmap> cache;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Long> failed = Collections.synchronizedSet(new HashSet<Long>());

    public ThumbLoader(Context c) {
        ctx = c.getApplicationContext();
        int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        cache = new LruCache<Long, Bitmap>(maxKb / 8) {
            @Override
            protected int sizeOf(Long k, Bitmap b) {
                return b.getByteCount() / 1024;
            }
        };
    }

    public void load(final Video v, final ImageView iv) {
        iv.setTag(Long.valueOf(v.id));
        Bitmap b = cache.get(Long.valueOf(v.id));
        if (b != null) {
            iv.setImageBitmap(b);
            return;
        }
        iv.setImageDrawable(null);
        if (failed.contains(Long.valueOf(v.id))) return;
        try {
            pool.execute(new Runnable() {
                public void run() {
                    Object t = iv.getTag();
                    if (t == null || ((Long) t).longValue() != v.id) return;
                    final Bitmap bmp = grab(v);
                    if (bmp == null) {
                        failed.add(Long.valueOf(v.id));
                        return;
                    }
                    cache.put(Long.valueOf(v.id), bmp);
                    main.post(new Runnable() {
                        public void run() {
                            Object t2 = iv.getTag();
                            if (t2 != null && ((Long) t2).longValue() == v.id) {
                                iv.setImageBitmap(bmp);
                            }
                        }
                    });
                }
            });
        } catch (Throwable e) {
            // pool shut down
        }
    }

    private Bitmap grab(Video v) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(ctx, v.uri);
            long atMs = v.duration > 0 ? Math.min(v.duration / 10, 8000L) : 1000L;
            Bitmap f = r.getFrameAtTime(atMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (f == null) f = r.getFrameAtTime();
            if (f == null) return null;
            int w = 480;
            if (f.getWidth() <= w) return f;
            int h = Math.max(1, f.getHeight() * w / f.getWidth());
            Bitmap s = Bitmap.createScaledBitmap(f, w, h, true);
            if (s != f) f.recycle();
            return s;
        } catch (Throwable e) {
            return null;
        } finally {
            try {
                r.release();
            } catch (Throwable ignore) {
            }
        }
    }

    public void shutdown() {
        try {
            pool.shutdownNow();
        } catch (Throwable ignore) {
        }
    }
}
