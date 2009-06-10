package com.hanhuy.hdhr.av;

import java.awt.Component;
import java.io.PrintStream;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;
import java.util.ArrayList;

import com.sun.jna.Native;
import com.sun.jna.Platform;

public class MplayerVideoPlayer implements VideoPlayer {
    private MplayerIO io = null;
    private Component c;
    private boolean playing;
    private String lastUri;
    private boolean muting;

    private int volume;

    public static boolean isAvailable() {
        return MplayerIO.findMplayer() != null;
    }

    public void mute(boolean m) {
        muting = m;
        if (io != null && io.isRunning)
            io.cmd("mute " + (muting ? 1 : 0));
    }

    MplayerVideoPlayer() {
        setVolume(50);
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
    }

    public void play(String uri) {
        if (io != null && !io.isRunning)
            io = null;

        if (io == null)
            io = new MplayerIO(Native.getComponentID(c));

        mute(muting);
        io.cmd("loadfile \"" + uri + "\"");
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
        if (io == null)
            io = new MplayerIO(Native.getComponentID(c));
    }

    public static class MplayerIO {
        private Process p;
        private PrintStream ps;
        volatile boolean isRunning;
        volatile boolean stopped;
        MplayerIO(long wid) throws VideoPlayerException {
            ArrayList<String> args = new ArrayList<String>();
            args.add(findMplayer());
            args.addAll(Arrays.asList(
                "-lavdopts",
                  "fast:ec=1:threads=3:skipframe=nonref:skiploopfilter=all",
                "-vf", "pp=fd,spp,scale",
                "-noslices",
                "-sws", "0",
                //"-framedrop",
                "-hardframedrop",
                "-vc", "ffmpeg2", // for -hardframedrop
                "-wid", Long.toString(wid),
                //"-cache", "32",
                "-ni",
                "-slave",
                "-really-quiet",
                //"-quiet",
                "-nocache",
                "-idle"
            ));
            if (Platform.isWindows()) {
                args.add("-vo");
                args.add("direct3d");
            }
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
                             System.out.println("[mplayer io] " + line);
                         }
                         System.out.println("[mplayer io] thread exit");
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
            }, "MplayerIO stdout/stderr").start();
            new Thread(new Runnable() {
                public void run() {
                    try {
                        p.waitFor();
                        if (!stopped)
                            System.out.println("[mplayer exit] unexpected");
                        else
                            System.out.println("[mplayer exit] clean");
                    }
                    catch (InterruptedException e) {
                        System.out.println("[mplayer exit] interrupted");
                        throw new RuntimeException(e);
                    }
                    isRunning = false;

                }
            }, "MplayerIO waitFor(mplayer)").start();
        }
    
        private void cmd(String cmd) {
            System.out.println("[mplayer cmd] " + cmd);
            ps.print(cmd);
            ps.print("\n");
            ps.flush();
        }

        private void dispose() {
            stopped = true;
            cmd("quit");
            p.destroy();
        }
        private static String MPLAYER_PATH;
        static String findMplayer() {
            if (MPLAYER_PATH != null)
                return MPLAYER_PATH;
            String executableName = Platform.isWindows() ?
                    "mplayer.exe" : "mplayer";
            String PATH = System.getenv("PATH");
            String[] paths = PATH.split(File.pathSeparator);

            String path = null;
            for (String dir : paths) {
                File f = new File(dir, executableName);
                if (f.exists() && f.isFile()) {
                    path = f.getPath();
                    break;
                }
            }

            String resource = null;
            if (Platform.isWindows()) {
                resource = "win32/mplayer.exe";
            } else if (Platform.isLinux()) {
                resource = "linux/mplayer";
                if (Platform.is64Bit())
                    resource = "linux64/mplayer";
            } else if (Platform.isMac()) {
                resource = "osx/mplayer";
            }
            if (resource != null) {
                InputStream in = MplayerIO.class.getClassLoader(
                        ).getResourceAsStream(resource);
                if (in != null) {
                    FileChannel fc = null;
                    try {
                        File mplayer = File.createTempFile("mplayer", ".exe");

                        FileOutputStream fout = new FileOutputStream(mplayer);
                        ReadableByteChannel rc = Channels.newChannel(in);
                        fc = fout.getChannel();
                        long pos = 0;
                        long len;
                        while ((len = fc.transferFrom(rc, pos, 32768)) > 0) {
                            pos += len;
                        }
                        mplayer.setExecutable(true);
                        mplayer.deleteOnExit();
                        path = mplayer.getPath();
                    }
                    catch (IOException e) {
                        throw new VideoPlayerException(e);
                    }
                    finally {
                        try {
                            in.close();
                        }
                        catch (IOException ex) { }
                        try {
                            if (fc != null)
                                fc.close();
                        }
                        catch (IOException ex) { }
                    }
                }
            }
            MPLAYER_PATH = path;
            return path;
        }
        public static void main(String[] args) {
            String path = findMplayer();
            System.out.println("Found mplayer at: " + path);
        }
    }
}
