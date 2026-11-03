package com.media.localflix;

import android.net.Uri;

public class Video {
    public long id;
    public String name;
    public String folder;
    public long size;
    public long duration;
    public long added;
    public int width;
    public int height;
    public Uri uri;
    // Iteration 2: series grouping, parsed from the filename in VideoRepo.
    public String series;
    public int season;
    public int episode;
    public String episodeTitle;
}
