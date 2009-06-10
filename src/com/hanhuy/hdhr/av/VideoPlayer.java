package com.hanhuy.hdhr.av;

import java.awt.Component;

public interface VideoPlayer {
    void setSurface(Component surface);
    void play(String uri);
    void stop();
    void dispose();
    void setVolume(int volume);
    void mute(boolean muting);
    void setDebug(boolean debug);
    //String getVolumeLabel(int level);
}
