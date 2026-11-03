package com.media.localflix;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;
import java.util.ArrayList;
import java.util.List;

public class VideoRepo {

    public static List<Video> load(Context c) {
        List<Video> out = new ArrayList<Video>();
        String[] proj = new String[] {
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        };
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj, null, null,
                MediaStore.Video.Media.DATE_ADDED + " DESC");
            if (cur == null) return out;
            while (cur.moveToNext()) {
                Video v = new Video();
                v.id = cur.getLong(0);
                String dn = cur.getString(1);
                if (dn == null) dn = "Video " + v.id;
                int dot = dn.lastIndexOf('.');
                v.name = dot > 0 ? dn.substring(0, dot) : dn;
                String b = cur.getString(2);
                v.folder = (b == null || b.length() == 0) ? "Other" : b;
                v.size = cur.getLong(3);
                v.duration = cur.getLong(4);
                v.added = cur.getLong(5);
                SeriesParser.Result parsed = SeriesParser.parse(v.name);
                v.series = parsed.series;
                v.season = parsed.season;
                v.episode = parsed.episode;
                v.episodeTitle = parsed.episodeTitle;
                v.width = cur.getInt(6);
                v.height = cur.getInt(7);
                if (v.size <= 0) continue;
                v.uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v.id);
                out.add(v);
            }
        } catch (Throwable t) {
            // ignore, return what we have
        } finally {
            if (cur != null) cur.close();
        }
        return out;
    }
}
