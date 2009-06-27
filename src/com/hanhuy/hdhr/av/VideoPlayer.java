package com.hanhuy.hdhr.av;

import com.hanhuy.hdhr.stream.PacketSource;

import java.awt.Component;

public interface VideoPlayer {
    void setSurface(Component surface);
    void play(PacketSource source);
    void stop();
    void dispose();
    void setVolume(int volume);
    void mute(boolean muting);
    void setDebug(boolean debug);
    String[] getDeinterlacers();
    void setDeinterlacer(String deinterlacer);
    //String getVolumeLabel(int level);
}
