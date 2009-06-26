package com.hanhuy.hdhr.av;

import com.hanhuy.hdhr.Main;
import com.hanhuy.hdhr.Actions;
import com.hanhuy.hdhr.config.RTPProxy;
import com.hanhuy.hdhr.config.HTTPStream;

import java.awt.Component;
import java.awt.EventQueue;
import java.io.IOException;

import javax.swing.JOptionPane;

public class HTTPExternalVideoPlayer implements VideoPlayer {
    private Component c;
    private HTTPStream hs;

    HTTPExternalVideoPlayer() {
    }

    public void mute(boolean m) {
    }

    public void setDebug(boolean d) {
    }

    public void setVolume(int volume) {
    }

    public void dispose() {
        stop();
        if (hs != null) hs.close();
    }


    public void play(RTPProxy proxy) {
        try {
            hs = new HTTPStream();
            proxy.addPacketListener(hs);
            final int port = hs.getLocalPort();
            System.out.println("Streaming at http://localhost:" + port);
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    Actions.getAction(
                            Actions.Name.STREAM_INFO).actionPerformed(null);
                }
            });
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {
    }

    public void setSurface(Component c) {
    }

    public String[] getDeinterlacers() {
        return new String[0];
    }

    public void setDeinterlacer(String d) {
    }
}
