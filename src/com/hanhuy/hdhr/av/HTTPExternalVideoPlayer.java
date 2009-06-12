package com.hanhuy.hdhr.av;

import com.hanhuy.hdhr.Main;
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
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    JOptionPane.showMessageDialog(Main.frame,
                            "Streaming at http://localhost:" + port);
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
}
