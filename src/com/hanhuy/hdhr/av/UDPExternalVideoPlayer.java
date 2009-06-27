package com.hanhuy.hdhr.av;

import com.hanhuy.hdhr.Actions;
import com.hanhuy.hdhr.stream.PacketSource;
import com.hanhuy.hdhr.stream.UDPStream;

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

    public void play(PacketSource source) {
        try {
            us = new UDPStream();
            source.addPacketListener(us);
            final int port = us.getRemotePort();
            System.out.println("Streaming to udp://@localhost:" + port);
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
