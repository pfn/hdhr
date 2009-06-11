package com.hanhuy.hdhr.av;

import com.hanhuy.hdhr.config.RTPProxy;

import java.awt.Component;

public interface VideoPlayer {
    void setSurface(Component surface);
    void play(RTPProxy proxy);
    void stop();
    void dispose();
    void setVolume(int volume);
    void mute(boolean muting);
    void setDebug(boolean debug);
    //String getVolumeLabel(int level);
}
