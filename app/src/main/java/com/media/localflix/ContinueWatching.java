package com.media.localflix;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Standout feature, part 2: Continue Watching, done properly.
 *
 * Rules, kept in one place so the row, the cards and the player agree:
 * - A video counts as "in progress" only if it has a saved position
 *   over 3 seconds and at least 5 seconds remain — the same thresholds
 *   the player already uses to save a position.
 * - Finished videos (position removed, or over 97% watched) never appear.
 * - Most recently watched first, using the time_ key the player saves
 *   alongside every position. Files watched before that key existed
 *   fall back to their library order.
 * - Next episode: same series, next episode in the same season; if
 *   there is none, the first episode of the next season.
 */
public final class ContinueWatching {

    public static final class Entry {
        public final Video video;
        public final long position;
        public final long lastSeen;
        public final float progress;

        Entry(Video video, long position, long lastSeen) {
            this.video = video;
            this.position = position;
            this.lastSeen = lastSeen;
            this.progress = video.duration > 0 ? position / (float) video.duration : 0f;
        }

        public long remaining() {
            return Math.max(0, video.duration - position);
        }

        public String label() {
            if (video.series != null && video.episode > 0) {
                return "S" + video.season + " E" + video.episode;
            }
            return "";
        }
    }

    public static List<Entry> list(SharedPreferences prefs, List<Video> all) {
        List<Entry> out = new ArrayList<Entry>();
        if (prefs == null || all == null) return out;
        for (Video v : all) {
            long pos = prefs.getLong("pos_" + v.id, 0L);
            if (pos <= 3000 || v.duration <= 0 || pos >= v.duration - 5000) continue;
            if (pos / (float) v.duration > 0.97f) continue;
            long seen = prefs.getLong("time_" + v.id, 0L);
            out.add(new Entry(v, pos, seen));
        }
        Collections.sort(out, new Comparator<Entry>() {
            public int compare(Entry a, Entry b) {
                if (a.lastSeen != b.lastSeen) return Long.valueOf(b.lastSeen).compareTo(a.lastSeen);
                return Long.valueOf(b.video.added).compareTo(a.video.added);
            }
        });
        return out;
    }

    public static Video nextEpisode(List<Video> all, Video cur) {
        if (all == null || cur == null || cur.series == null || cur.episode <= 0) return null;
        Video nextSeasonFirst = null;
        for (Video v : all) {
            if (v == cur || v.series == null || !v.series.equalsIgnoreCase(cur.series)) continue;
            if (v.season == cur.season && v.episode == cur.episode + 1) return v;
            if (v.season == cur.season + 1) {
                if (nextSeasonFirst == null || v.episode < nextSeasonFirst.episode) nextSeasonFirst = v;
            }
        }
        return nextSeasonFirst;
    }
}
