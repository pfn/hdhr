package com.hanhuy.hdhr.av;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;

public class ExternalVLC implements Runnable {
    VLCVideoPlayer player;
    private final long handle;
    volatile boolean quit;
    HashMap<String,Command> commands = new HashMap<String,Command>();

    ExternalVLC(long handle) {
        this.handle = handle;
    }

    public static void main(String[] args) {
        String wid = args[0];
        long handle = Long.parseLong(wid);
        ExternalVLC vlc = new ExternalVLC(handle);
        vlc.run();
    }

    public void run() {
        player = new VLCVideoPlayer();
        player.setHandle(handle);

        commands.put("play", new Command() {
            public void execute(String... args) {
                String uri = args[0];

                player.play(uri);
            }
        });
        commands.put("stop", new Command() {
            public void execute(String... args) {
                player.stop();
            }
        });
        commands.put("volume", new Command() {
            public void execute(String... args) {
                String s = args[0];
                int volume = Integer.parseInt(s);
                player.setVolume(volume);
            }
        });
        commands.put("mute", new Command() {
            public void execute(String... args) {
                String s = args[0];
                int volume = Integer.parseInt(s);
                player.mute(volume != 0);
            }
        });
        commands.put("debug", new Command() {
            public void execute(String... args) {
                String s = args[0];
                int debug = Integer.parseInt(s);
                player.setDebug(debug != 0);
            }
        });
        commands.put("deinterlace", new Command() {
            public void execute(String... args) {
                String s = args[0];
                player.setDeinterlacer("null".equals(s) ? null : s);
            }
        });
        commands.put("quit", new Command() {
            public void execute(String... args) {
                player.dispose();
                quit = true;
            }
        });

        new Thread(new CommandHandler(), "ExternalVLC CommandHandler").start();
    }

    class CommandHandler implements Runnable {
        public void run() {
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(System.in));
            String line;
            try {
                while ((line = r.readLine()) != null && !quit) {
                    ArrayList<String> parts = new ArrayList<String>(
                            Arrays.asList(line.split("\\s+")));
                    if (parts.size() < 1) continue;
                    String cmd = parts.get(0);
                    parts.remove(0);
                    Command c = commands.get(cmd);
                    if (c == null) {
                        System.err.println("[vlc cmd] Unknown: " + line);
                        continue;
                    }

                    c.execute(parts.toArray(new String[0]));
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            finally {
                try {
                    r.close();
                }
                catch (IOException ex) { }
            }
        }
    }
    interface Command {
        void execute(String... args);
    }

}
