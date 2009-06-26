package com.hanhuy.hdhr.av;

import com.hanhuy.hdhr.config.RTPProxy;
import com.hanhuy.hdhr.config.UDPStream;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.IOException;

import org.videolan.jvlc.internal.LibVlc;
import org.videolan.jvlc.internal.LibVlc.libvlc_exception_t;
import org.videolan.jvlc.internal.LibVlc.LibVlcInstance;
import org.videolan.jvlc.internal.LibVlc.LibVlcMediaPlayer;
import org.videolan.jvlc.internal.LibVlc.LibVlcMedia;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;

public class VLCVideoPlayer implements VideoPlayer {
    private UDPStream us;

    private final static int VLC_VOLUME_MAX = 200;

    public final static int SLIDER_VOLUME_0DB = 160;
    public final static boolean isJWS;

    private boolean muting;

    private final static String LIBVLC_VERSION;

    private final static LibVlc _libvlc;
    private LibVlc libvlc;
    private LibVlcMediaPlayer player;
    private LibVlcInstance instance;
    private static String libraryPath;

    private final static String JNA_PATH = "jna.library.path";
    private boolean debug;

    private Component c;
    private long handle = 0xdeadbeef;

    private final static long DEADBEEF = 0xdeadbeef;

    private int volume;

    public void setSurface(Component c) {
        this.c = c;
    }

    public void setHandle(long handle) {
        this.handle = handle;
    }

    public static boolean isAvailable() {
        return _libvlc != null;
    }

    static {
        isJWS = System.getProperty("javawebstart.version") != null;

        if (isJWS) {
            libraryPath = Native.getWebStartLibraryPath("libvlc");
            if (libraryPath != null)
                System.setProperty(JNA_PATH, libraryPath);
        } else if (System.getProperty(JNA_PATH) != null) {
            libraryPath = System.getProperty(JNA_PATH);
            libraryPath = libraryPath.replaceAll("/", "\\\\");
        }

        if (Platform.isLinux()) {
            // disregard isJWS
            System.setProperty(JNA_PATH, "/usr/lib:/usr/local/lib");

        }
        LibVlc lv = null;
        try {
            lv = LibVlc.SYNC_INSTANCE;
            //lv = PrintWrapper.wrap(LibVlc.class, LibVlc.SYNC_INSTANCE);
        }
        catch (UnsatisfiedLinkError e) { }
        _libvlc = lv;
        if (_libvlc != null) {
            LIBVLC_VERSION = _libvlc.libvlc_get_version();
        } else
            LIBVLC_VERSION = null;
    }

    VLCVideoPlayer() {
        System.out.println("LIBVLC_VERSION="+LIBVLC_VERSION);
        setVolume(50);
        libvlc = _libvlc;
    }



    public void play(RTPProxy proxy) {
        try {
            us = new UDPStream();
            proxy.addPacketListener(us);
            int port = us.getRemotePort();
            play("udp://@:" + port);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void play(String uri) {
        final libvlc_exception_t ex = new libvlc_exception_t();

        //String defaultLibraryPath = "C:\\Program Files\\VideoLAN\\vlc";
        String defaultLibraryPath = "C:\\vlct";
        if (Platform.isLinux())
            defaultLibraryPath = "/usr/lib/vlc";

        libraryPath = libraryPath != null ?
                libraryPath : defaultLibraryPath;
        ArrayList<String> vlc_args = new ArrayList<String>(Arrays.asList(
                "-vvv",
                //"--ignore-config",
                "-I",            "dummy",
                "--no-video-title-show",
                "--no-osd",
                "--mouse-hide-timeout", "100",
                "--video-filter",       "deinterlace",
                "--deinterlace-mode",   "linear"
        ));
        if (debug) {
            vlc_args.add("-vvv");
            vlc_args.add("--no-overlay");
        } else {
            vlc_args.add("--quiet");
            vlc_args.add("1");
            vlc_args.add("--verbose");
            vlc_args.add("0");
        }
        if (Platform.isWindows()) {
            if (libraryPath != null) {
                vlc_args.add("--plugin-path");
                vlc_args.add(libraryPath);
            }
        }
        if (LIBVLC_VERSION.startsWith("0.9")) {
            vlc_args.add("--ffmpeg-skiploopfilter");
            vlc_args.add("4");
        } else if (LIBVLC_VERSION.startsWith("1.")) {
            // disable keyboard and mouse event handling
            vlc_args.add("--codec"); // avoid libmpeg2 crashes
            vlc_args.add("avcodec"); // prior to 1.0.0rc5 avcodec has buffer
                                     // alignment problems
            vlc_args.add("--vout-event");
            vlc_args.add("3");

            vlc_args.add("--ffmpeg-fast");
            vlc_args.add("--ffmpeg-skiploopfilter");
            vlc_args.add("all");
        } else {
            throw new VideoPlayerException(
                    "Unsupported VLC version: " + LIBVLC_VERSION);
        }
        instance = libvlc.libvlc_new(vlc_args.size(),
                vlc_args.toArray(new String[0]), ex);
        throwError(ex);

        setVolume(volume);

        if (muting)
            mute(muting);

        LibVlcMedia media = libvlc.libvlc_media_new(
                instance, uri, ex);
        throwError(ex);

        player = libvlc.libvlc_media_player_new_from_media(media, ex);
        throwError(ex);

        libvlc.libvlc_media_release(media);

        long drawable;
        Pointer drawableP;
        if (c == null && handle != DEADBEEF) {
            drawableP = Pointer.createConstant(handle);
            drawable = handle;
        } else if (c != null) {
            drawable = Native.getComponentID(c);
            drawableP = Native.getComponentPointer(c);
        } else {
            throw new VideoPlayerException("Drawing surface not set");
        }

        if (LIBVLC_VERSION.startsWith("0.9")) {
            // set_drawable only works properly on 32bit
            libvlc.libvlc_media_player_set_drawable(player, (int)drawable, ex);
        } else if (LIBVLC_VERSION.startsWith("1.")) {
            // vlc 1.0 will deprecate the above api
            if (Platform.isWindows()) {
                libvlc.libvlc_media_player_set_hwnd(player, drawableP, ex);
            } else if (Platform.isX11()) {
                libvlc.libvlc_media_player_set_xwindow(player, drawable, ex);
            } else if (Platform.isMac()) {
                libvlc.libvlc_media_player_set_nsobject(player, drawableP, ex);
            }
        }
        throwError(ex);

        libvlc.libvlc_media_player_play(player, ex);
        throwError(ex);
    }

    public void stop() {
        libvlc_exception_t ex = null;

        if (player != null) {
            ex = new libvlc_exception_t();
            libvlc.libvlc_media_player_stop(player, ex);

            libvlc.libvlc_media_player_release(player);
        }

        if (instance != null)
            libvlc.libvlc_release(instance);

        throwError(ex);
        player   = null;
        instance = null;
    }

    public void dispose() {
        stop();
        if (us != null)
            us.close();
    }

    private void throwError(LibVlc.libvlc_exception_t ex) {
        if (ex != null && ex.raised != 0)
            throw new VideoPlayerException(ex.code + ": " + ex.message);
    }

    public void mute(boolean m) {
        muting = m;
        if (instance != null) {
            libvlc_exception_t ex = new libvlc_exception_t();
            libvlc.libvlc_audio_set_mute(instance, muting ? 1 : 0, ex);
            throwError(ex);
        }
        if (!muting)
            setVolume(volume);
    }

    public void setDebug(boolean d) {
        debug = d;
        libvlc = debug ? PrintWrapper.wrap(LibVlc.class, _libvlc) : _libvlc;
    }
    public void setVolume(int volume) {
        this.volume = volume;
        double value = volume * 1.9;
        double dB = getDB((int) Math.round(value));

        int linear = (int) Math.round(100.0 * Math.pow(10, dB/10));

        volume = Math.min(linear, VLC_VOLUME_MAX);

        if (instance != null && !muting) {
            libvlc_exception_t ex = new libvlc_exception_t();
            libvlc.libvlc_audio_set_volume(instance, volume, ex);
            throwError(ex);
        }
    }
    public static double getDB(int value) {
        return (double) (value - SLIDER_VOLUME_0DB) / 10;
    }
}
