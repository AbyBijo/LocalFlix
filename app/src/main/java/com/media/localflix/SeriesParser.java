package com.media.localflix;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Standout feature, part 1: understand local files as shows.
 *
 * Pure Java, no Android dependency, so the parsing rules are easy to
 * reason about and to extend. It only claims a file is an episode when
 * the filename carries an explicit season/episode marker — a bare
 * trailing number (e.g. "Movie 2") is NOT treated as an episode, so
 * film sequels are never mis-grouped as a series.
 */
public final class SeriesParser {

    public static final class Result {
        public final String series;
        public final int season;
        public final int episode;
        public final String episodeTitle;
        public final boolean isSeries;

        Result(String series, int season, int episode, String episodeTitle, boolean isSeries) {
            this.series = series;
            this.season = season;
            this.episode = episode;
            this.episodeTitle = episodeTitle;
            this.isSeries = isSeries;
        }
    }

    private static final Pattern S_E = Pattern.compile("(?i)\\bS(\\d{1,2})E(\\d{1,3})\\b");
    private static final Pattern X_FORM = Pattern.compile("(?i)\\b(\\d{1,2})x(\\d{1,3})\\b");
    private static final Pattern SEASON_EPISODE = Pattern.compile("(?i)\\bSeason\\s+(\\d{1,2})\\s+Episode\\s+(\\d{1,3})\\b");
    private static final Pattern EPISODE_ONLY = Pattern.compile("(?i)\\bEpisode\\s+(\\d{1,3})\\b");
    private static final Pattern E_ONLY = Pattern.compile("(?i)[\\s\\-_.]E(\\d{1,3})\\b");

    public static Result parse(String rawName) {
        if (rawName == null || rawName.trim().length() == 0) {
            return new Result("", 0, 0, null, false);
        }
        String cleaned = rawName.replace('_', ' ').replace('.', ' ');
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        Matcher m = SEASON_EPISODE.matcher(cleaned);
        if (m.find()) return build(cleaned, m, 1, 2);
        m = S_E.matcher(cleaned);
        if (m.find()) return build(cleaned, m, 1, 2);
        m = X_FORM.matcher(cleaned);
        if (m.find()) return build(cleaned, m, 1, 2);
        m = EPISODE_ONLY.matcher(cleaned);
        if (m.find()) {
            Result r = build(cleaned, m, 0, 1);
            return new Result(r.series, 1, r.episode, r.episodeTitle, true);
        }
        m = E_ONLY.matcher(cleaned);
        if (m.find()) {
            Result r = build(cleaned, m, 0, 1);
            return new Result(r.series, 1, r.episode, r.episodeTitle, true);
        }
        return new Result(cleaned, 0, 0, null, false);
    }

    private static Result build(String cleaned, Matcher m, int seasonGroup, int episodeGroup) {
        int season = seasonGroup > 0 ? parseInt(m.group(seasonGroup)) : 1;
        int episode = parseInt(m.group(episodeGroup));
        String series = cleanTitle(cleaned.substring(0, m.start()));
        String after = cleanTitle(cleaned.substring(m.end()));
        String episodeTitle = after.length() == 0 ? null : after;
        if (series.length() == 0) series = cleaned;
        return new Result(series, season, episode, episodeTitle, true);
    }

    private static String cleanTitle(String s) {
        String t = s.trim();
        while (t.endsWith("-") || t.endsWith(":") || t.endsWith("(")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        while (t.startsWith("-") || t.startsWith(":")) {
            t = t.substring(1).trim();
        }
        // A trailing year in brackets, e.g. "Show (2020)", is not part of the title.
        t = t.replaceAll("\\(\\d{4}\\)\\s*$", "").trim();
        return t;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }
}
