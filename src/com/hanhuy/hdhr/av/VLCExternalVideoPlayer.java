package com.hanhuy.hdhr.av;

import com.hanhuy.hdhr.config.RTPProxy;
import com.hanhuy.hdhr.config.UDPStream;
import com.hanhuy.hdhr.config.HTTPStream;

import java.awt.Component;
import java.awt.EventQueue;

import java.io.PrintStream;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.MalformedURLException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;
import java.util.ArrayList;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.ScriptEngineManager;

import com.sun.jna.Native;
import com.sun.jna.Platform;

public class VLCExternalVideoPlayer implements VideoPlayer {
    public static enum Mode { UDP, HTTP };

    private RTPProxy.PacketListener l;
    private IO io = null;
    private Component c;
    volatile boolean playing;
    private String lastUri;

    private int volume;
    private boolean muting;
    private boolean debug;
    private String deinterlacer;

    private Mode mode;

    VLCExternalVideoPlayer(Mode m) {
        mode = m;
        setVolume(50);
    }

    public void mute(boolean m) {
        muting = m;
        if (io != null)
            io.cmd("mute " + (muting ? 1 : 0));
    }

    public void setDebug(boolean d) {
        debug = d;
        if (io != null)
            io.cmd("debug " + (d ? 1 : 0));
    }

    public void setVolume(int volume) {
        this.volume = volume;
        if (io != null && io.isRunning)
            io.cmd("volume " + volume);
    }

    public void dispose() {
        stop();
        if (io != null) {
            io.dispose();
            io = null;
        }
        try {
            if (l != null)
                l.close();
        }
        catch (IOException e) { }
    }


    public void play(RTPProxy proxy) {
        try {
            int port;
            String url;
            switch (mode) {
            case HTTP:
                HTTPStream hs = new HTTPStream();
                l = hs;
                port = hs.getLocalPort();
                url = "http://localhost:" + port;
                break;
            case UDP:
                UDPStream us = new UDPStream();
                l = us;
                port = us.getRemotePort();
                url = "udp://@:" + port;
                break;
            default:
                throw new IllegalArgumentException(mode.toString());
            }
            proxy.addPacketListener(l);
            play(url);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String[] getDeinterlacers() {
        return VLCVideoPlayer.deinterlacers.toArray(
                new String[VLCVideoPlayer.deinterlacers.size()]);
    }

    public void setDeinterlacer(String deinterlacer) {
        if (deinterlacer != null &&
                !VLCVideoPlayer.deinterlacers.contains(deinterlacer))
            deinterlacer = null;
        if (io != null)
            io.cmd("deinterlace " + deinterlacer);
        this.deinterlacer = deinterlacer;
    }
    public void play(String uri) {
        if (io != null && !io.isRunning)
            io = null;

        if (io == null)
            io = new IO(Native.getComponentID(c));

        setDeinterlacer(deinterlacer);
        setDebug(debug);
        mute(muting);
        io.cmd("play " + uri);
        setVolume(volume);
        lastUri = uri;
        playing = true;
    }

    public void stop() {
        if (!playing) return;

        if (io != null)
            io.cmd("stop");
        playing = false;
    }

    public void setSurface(Component c) {
        if (this.c != c) {
            if (io != null)
                dispose();
        }
        this.c = c;
        long id = Native.getComponentID(c);
        System.out.println("[vlc] drawing to window id " + id);
        if (io == null)
            io = new IO(id);
    }

    static URL getJarURL(String path) {
        ClassLoader cl = VLCExternalVideoPlayer.class.getClassLoader();
        path = cl.getResource(path).toString();
        path = path.substring(path.indexOf(":") + 1);
        path = path.substring(0, path.indexOf("!"));

        try {
            return new URL(path);
        }
        catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    static String getFileLocation(ScriptEngine js, URL u)
    throws ScriptException {
        js.put("url", u);
        js.eval("var entry = com.sun.deploy.cache.Cache.getCacheEntry(" +
                "url, null, null)");
        js.eval("var file = entry.getDataFile()");
        return js.get("file").toString();
    }
    public class IO {
        private Process p;
        private PrintStream ps;
        volatile boolean isRunning;
        volatile boolean stopped;
        IO(long wid) throws VideoPlayerException {
            ArrayList<String> args = new ArrayList<String>();
            args.add(findJVM());

            String classpath = System.getProperty("java.class.path");
            if (VLCVideoPlayer.isJWS) {
    
                URL hdhr = getJarURL("com/hanhuy/hdhr/av/VLCVideoPlayer.class");
                URL jna  = getJarURL("com/sun/jna/Native.class");
                URL vlc  = getJarURL("org/videolan/jvlc/internal/LibVlc.class");

                try {
                    ScriptEngineManager m = new ScriptEngineManager();
                    ScriptEngine js = m.getEngineByName("JavaScript");

                    classpath = getFileLocation(js, hdhr) +
                            File.pathSeparator + getFileLocation(js, jna) +
                            File.pathSeparator + getFileLocation(js, vlc);
                }
                catch (ScriptException e) {
                    throw new RuntimeException(e);
                }
            }
            String jna_path = System.getProperty("jna.library.path");
            args.addAll(Arrays.asList(
                "-Djna.library.path=" + System.getProperty("jna.library.path"),
                "-cp",                  classpath,
                "com.hanhuy.hdhr.av.ExternalVLC",
                Long.toString(wid)
            ));
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.redirectErrorStream(true);
            try {
                p = pb.start();
            }
            catch (IOException e) {
                throw new VideoPlayerException(e);
            }
            isRunning = true;
    
            ps = new PrintStream(p.getOutputStream());
    
            new Thread(new Runnable() {
                public void run() {
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(p.getInputStream()));
                    try {
                         String line;
                         while ((line = in.readLine()) != null) {
                             // do nothing
                             System.out.println("[vlc io] " + line);
                         }
                         System.out.println("[vlc io] thread exit");
                    }
                    catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    finally {
                        try {
                            in.close();
                        }
                        catch (IOException e) { }
                    }
                }
            }, "IO stdout/stderr (vlc)").start();
            new Thread(new Runnable() {
                public void run() {
                    try {
                        p.waitFor();
                        isRunning = false;
                        if (!stopped) {
                            System.out.println("[vlc exit] unexpected");
                            if (playing) {
                                play(lastUri);
                                System.out.println("[vlc exit] " +
                                        "Backend process died while playing, " +
                                        "restarting process");
                            } else {
                                System.out.println("[vlc exit] " +
                                        "Backend process died.");
                            }
                        } else
                            System.out.println("[vlc exit] clean");
                    }
                    catch (InterruptedException e) {
                        System.out.println("[vlc exit] interrupted");
                        throw new RuntimeException(e);
                    }

                }
            }, "IO waitFor(vlc)").start();
        }
    
        private void cmd(String cmd) {
            System.out.println("[vlc cmd] " + cmd);
            ps.print(cmd);
            ps.print("\n");
            ps.flush();
        }

        private void dispose() {
            stopped = true;
            cmd("quit");
            p.destroy();
        }
        private String JVM_PATH;
        String findJVM() {
            if (JVM_PATH != null)
                return JVM_PATH;
            String executableName = Platform.isWindows() ?
                    "java.exe" : "java";
            String java_home = System.getProperty("java.home");

            if (java_home == null)
                throw new IllegalStateException("java.home is null");

            String java = String.format("%s%sbin%s%s",
                    java_home, File.separator, File.separator, executableName);

            JVM_PATH = java;
            return java;
        }
    }
}
