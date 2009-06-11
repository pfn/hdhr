package com.hanhuy.hdhr.av;

import com.hanhuy.hdhr.config.RTPProxy;
import com.hanhuy.hdhr.config.HTTPStream;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import com.sun.media.jmc.MediaProvider;
import com.sun.media.jmc.control.AudioControl;
import com.sun.media.jmc.control.VideoControl;
import com.sun.media.jmc.control.VideoRenderControl;
import com.sun.media.jmc.event.VideoRendererListener;
import com.sun.media.jmc.event.VideoRendererEvent;

// this doesn't work  :-(  there doesn't seem to be support for mpeg2-ts yet
public class JMCVideoPlayer implements VideoPlayer {
    private MediaProvider mp;
    private AudioControl ac;
    private VideoRenderControl vc;
    private Dimension vSize;
    private Graphics2D g;
    private int volume = 50;
    private boolean muting;

    private void initMediaProvider(URI u) {
        mp = new MediaProvider(u);
        ac = mp.getControl(AudioControl.class);
        vc = mp.getControl(VideoRenderControl.class);
        VideoControl v = mp.getControl(VideoControl.class);
        v.setResizeBehavior(VideoControl.ResizeBehavior.Preserve);

        vc.addVideoRendererListener(new VideoRendererListener() {
            public void videoFrameUpdated(VideoRendererEvent e) {
                if (vSize == null) {
                    vSize = vc.getFrameSize();
                }

                vc.paintVideoFrame(g, new Rectangle(0, 0, vSize.width, vSize.height));
            }
        });
    }

    public void setSurface(Component surface) {
        g = (Graphics2D) surface.getGraphics();
    }

    public void play(RTPProxy proxy) {
        try {
            HTTPStream hs = new HTTPStream();
            proxy.addPacketListener(hs);
            int port = hs.getLocalPort();
            play("http://localhost:" + port);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void play(String uri) {
        URI u;
        try {
            u = new URI(uri);
        }
        catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
        if (mp == null)
            initMediaProvider(u);
        else
            mp.setSource(u);
        setVolume(volume);
        if (muting) mute(muting);
        mp.play();
    }
    public void stop() {
        if (mp != null)
            mp.pause();
    }
    public void dispose() {
        if (mp != null)
            mp.setSource(null);
    }
    public void setVolume(int volume) {
        this.volume = volume;
        if (ac != null)
            ac.setVolume((float) volume / 100);
    }
    public void mute(boolean muting) {
        this.muting = muting;
        if (ac != null)
            ac.setMute(muting);
    }
    public void setDebug(boolean debug) { }
}
