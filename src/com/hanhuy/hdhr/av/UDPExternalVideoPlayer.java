package com.hanhuy.hdhr.av;

import com.hanhuy.hdhr.Main;
import com.hanhuy.hdhr.config.RTPProxy;
import com.hanhuy.hdhr.config.UDPStream;

import java.awt.Component;
import java.awt.EventQueue;
import java.io.IOException;

import javax.swing.JOptionPane;

public class UDPExternalVideoPlayer implements VideoPlayer {
    private Component c;
    private UDPStream us;

    UDPExternalVideoPlayer() {
    }

    public void mute(boolean m) {
    }

    public void setDebug(boolean d) {
    }

    public void setVolume(int volume) {
    }

    public void dispose() {
        stop();
        if (us != null)
            us.close();
    }


    public void play(RTPProxy proxy) {
        try {
            us = new UDPStream();
            proxy.addPacketListener(us);
            final int port = us.getRemotePort();
            System.out.println("Streaming to udp://@localhost:" + port);
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    JOptionPane.showMessageDialog(Main.frame,
                            "Streaming to udp://@localhost:" + port);
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
