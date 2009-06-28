package com.hanhuy.hdhr;

import com.hanhuy.common.ui.ResourceBundleForm;
import com.hanhuy.hdhr.av.VideoPlayer;
import com.hanhuy.hdhr.av.VideoPlayerFactory;
import com.hanhuy.hdhr.stream.RTPProxy;
import com.hanhuy.hdhr.config.Discover;
import com.hanhuy.hdhr.config.Control;
import com.hanhuy.hdhr.config.TunerException;
import com.hanhuy.hdhr.config.ChannelMap.Channel.Program;
import com.hanhuy.hdhr.treemodel.Tuner;

import java.awt.Canvas;
import java.awt.Color;
import java.io.IOException;
import java.util.Map;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSlider;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeSelectionEvent;

public class ProgramCard extends ResourceBundleForm
implements TreeSelectionListener, ChangeListener {

    private VideoPlayer player;
    public final static int DEFAULT_VOLUME = 50;
    public final static int MAX_VOLUME     = 100;
    public final static ProgramCard INSTANCE = new ProgramCard();
    public final static String CARD_NAME = "ProgramCard";
    public final JPanel card;

    private boolean debug;
    private boolean playing;
    private boolean mute;

    private Control device;
    private RTPProxy proxy;
    private final Canvas c;
    private Program program;

    private int volume = DEFAULT_VOLUME;

    private ProgramCard() {
        card = new JPanel();
        card.setLayout(createLayoutManager());

        c = new Canvas();

        c.setBackground(Color.black);
        card.add(c, "canvas");
        String defaultBackend = Preferences.getInstance().videoBackend;
        setVideoPlayer(defaultBackend == null ?
                VideoPlayerFactory.getVideoPlayer() :
                VideoPlayerFactory.getVideoPlayer(defaultBackend));
    }

    public Program getProgram() {
        return program;
    }

    private void setProgram(Tuner t, Program program) {
        this.program = program;
        player.setSurface(c);

        device = new Control();
        int port;
        try {

            device.connect(t.device.id);
            Map<Integer,InetAddress[]> devices = Discover.discover(t.device.id);
            InetAddress[] endpoints = devices.get(t.device.id);
            String ip = endpoints[0].getHostAddress();

            proxy = new RTPProxy(endpoints[0]);
            port = proxy.getLocalPort();
            device.lock(t.tuner);
            device.set("channel",
                    program.channel.getModulation() + ":" +
                    program.channel.number);
            device.set("program", Integer.toString(program.number));
            String target = "rtp://" + ip + ":" + port;
            device.set("target", target);
        }
        catch (IOException e) {
            JOptionPane.showMessageDialog(Main.frame, e.getMessage());
            device.close();
            device = null;
            stopPlayer();
            return;
        }
        catch (TunerException e) {
            JOptionPane.showMessageDialog(Main.frame, e.getMessage());
            device.close();
            device = null;
            stopPlayer();
            return;
        }
        player.setVolume(volume);
        player.play(proxy);

        playing = true;
        Main.cards.show(Main.cardPane, CARD_NAME);
    }

    public void valueChanged(TreeSelectionEvent e) {
        Object[] path = e.getPath().getPath();
        Object item = path[path.length - 1];
        if (item instanceof Program && e.isAddedPath()) {
            stopPlayer();
            setProgram((Tuner) path[path.length - 2], (Program) item);
        } else {
            stopPlayer();
        }
    }
    void stopPlayer() {
        stopPlayer(false);
    }
    void stopPlayer(boolean exiting) {
        program = null;
        playing = false;

        if (device != null) {
            try {
                device.set("target", "none");
                if (exiting)
                    device.set("channel", "none");
                device.unlock();
            }
            catch (TunerException e) { }
            finally {
                device.close();
            }
        }
        if (proxy != null) {
            proxy.close();
            proxy = null;
        }

        if (player != null) {
            player.stop();
            if (exiting)
                player.dispose();
        }

        device = null;
    }

    public void stateChanged(ChangeEvent e) {
        JSlider slider = (JSlider) e.getSource();
        setVolume(slider.getValue());
    }

    public void setVolume(int volume) {
        this.volume = volume;
        Preferences.getInstance().volume = volume;
        if (player != null)
            player.setVolume(volume);
    }

    public VideoPlayer getVideoPlayer() {
        return player;
    }
    public void setVideoPlayer(VideoPlayer p) {
        if (p == null) return;
        if (proxy != null)
            proxy.clearPacketListeners();
        if (player != null) {
            player.stop();
            player.dispose();
        }

        player = p;
        if (debug)
            player.setDebug(debug);
        if (mute)
            player.mute(mute);

        if (proxy != null) {
            player.setSurface(c);
            player.setVolume(volume);
            player.play(proxy);
        }
    }

    public void setMute(boolean m) {
        mute = m;
        Preferences.getInstance().muting = m;
        getVideoPlayer().mute(m);
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean d) {
        if (player == null) return;
        debug = d;
        player.setDebug(debug);
    }
    
    public RTPProxy getProxy() {
        return proxy;
    }

    public boolean isPlaying() {
        return playing;
    }
}
