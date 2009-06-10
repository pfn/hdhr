package com.hanhuy.hdhr.av;

import java.util.ArrayList;

import com.sun.jna.Platform;

public class VideoPlayerFactory {
    public final static String BACKEND_VLC          = "vlc";
    public final static String BACKEND_VLC_EXTERNAL = "external vlc";
    public final static String BACKEND_MPLAYER      = "mplayer";
    public static VideoPlayer getVideoPlayer() {
        return getVideoPlayer(getVideoPlayerNames()[0]);
    }
    public static VideoPlayer getVideoPlayer(String backend) {
        if (BACKEND_VLC_EXTERNAL.equals(backend))
            return new VLCExternalVideoPlayer();
        else if (BACKEND_VLC.equals(backend))
            return new VLCVideoPlayer();
        else if (BACKEND_MPLAYER.equals(backend))
            return new MplayerVideoPlayer();
        throw new IllegalArgumentException(backend);
    }

    public static String[] getVideoPlayerNames() {
        ArrayList<String> names = new ArrayList<String>();
        if (VLCVideoPlayer.isAvailable()) {
            if (!Platform.isMac())
                names.add(BACKEND_VLC_EXTERNAL);
            names.add(BACKEND_VLC);
        }

        if (MplayerVideoPlayer.isAvailable())
            names.add(BACKEND_MPLAYER);

        return names.toArray(new String[names.size()]);
    }
}
