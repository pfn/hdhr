package com.hanhuy.hdhr.av;

import java.util.ArrayList;

import com.sun.jna.Platform;

public class VideoPlayerFactory {
    public final static String BACKEND_VLC           = "vlc";
    public final static String BACKEND_VLC_EXTERNAL_HTTP =
            "external vlc (http)";
    public final static String BACKEND_VLC_EXTERNAL_UDP =
            "external vlc (udp)";
    public final static String BACKEND_MPLAYER       = "mplayer";
    public final static String BACKEND_UDP_EXTERNAL  = "external udp test";
    public final static String BACKEND_HTTP_EXTERNAL = "external http test";
    public static VideoPlayer getVideoPlayer() {
        return getVideoPlayer(getVideoPlayerNames()[0]);
    }
    public static VideoPlayer getVideoPlayer(String backend) {
        if (BACKEND_VLC_EXTERNAL_HTTP.equals(backend))
            return new VLCExternalVideoPlayer(VLCExternalVideoPlayer.Mode.HTTP);
        if (BACKEND_VLC_EXTERNAL_UDP.equals(backend))
            return new VLCExternalVideoPlayer(VLCExternalVideoPlayer.Mode.UDP);
        else if (BACKEND_VLC.equals(backend))
            return new VLCVideoPlayer();
        else if (BACKEND_MPLAYER.equals(backend))
            return new MplayerVideoPlayer();
        else if (BACKEND_UDP_EXTERNAL.equals(backend))
            return new UDPExternalVideoPlayer();
        else if (BACKEND_HTTP_EXTERNAL.equals(backend))
            return new HTTPExternalVideoPlayer();
        return null;
    }

    public static String[] getVideoPlayerNames() {
        ArrayList<String> names = new ArrayList<String>();
        if (VLCVideoPlayer.isAvailable()) {
            if (!Platform.isMac()) {
                names.add(BACKEND_VLC_EXTERNAL_UDP);
                names.add(BACKEND_VLC_EXTERNAL_HTTP);
            }
            names.add(BACKEND_VLC);
        }

        if (MplayerVideoPlayer.isAvailable())
            names.add(BACKEND_MPLAYER);

        names.add(BACKEND_UDP_EXTERNAL);
        names.add(BACKEND_HTTP_EXTERNAL);
        return names.toArray(new String[names.size()]);
    }
}
