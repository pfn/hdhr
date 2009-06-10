package com.hanhuy.hdhr;

import com.hanhuy.common.ui.ResourceBundleForm;
import com.hanhuy.hdhr.av.VideoPlayer;
import com.hanhuy.hdhr.av.VideoPlayerFactory;
import com.hanhuy.hdhr.config.Discover;
import com.hanhuy.hdhr.config.Control;
import com.hanhuy.hdhr.config.TunerException;
import com.hanhuy.hdhr.config.ChannelMap.Channel.Program;
import com.hanhuy.hdhr.treemodel.Tuner;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.event.MouseAdapter;
import java.util.Map;
import java.net.InetAddress;
import java.net.DatagramSocket;
import java.net.SocketException;
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
    private boolean mute;

    private Control device;
    private final Canvas c;

    private int volume = DEFAULT_VOLUME;

    private ProgramCard() {
        card = new JPanel();
        card.setLayout(createLayoutManager());

        c = new Canvas();

        c.setBackground(Color.black);
        card.add(c, "canvas");
        setVideoPlayer(VideoPlayerFactory.getVideoPlayer());
    }

    private void setProgram(Tuner t, Program program) {
        player.setSurface(c);

        device = new Control();
        int port = 0;
        try {
            DatagramSocket sock = new DatagramSocket();
            port = sock.getLocalPort();
            sock.close();

            device.connect(t.device.id);
            Map<Integer,InetAddress[]> devices = Discover.discover(t.device.id);
            InetAddress[] endpoints = devices.get(t.device.id);
            String ip = endpoints[0].getHostAddress();
            device.lock(t.tuner);
            device.set("channel",
                    program.channel.getModulation() + ":" +
                    program.channel.number);
            device.set("program", Integer.toString(program.number));
            device.set("target", "rtp://" + ip + ":" + port);
        }
        catch (SocketException e) {
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
        player.play("rtp://@:" + port);

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

        if (player != null) {
            player.stop();
            if (exiting)
                player.dispose();
        }

        device = null;
    }

    public void stateChanged(ChangeEvent e) {
        JSlider slider = (JSlider) e.getSource();
        volume = slider.getValue();
        if (player != null)
            player.setVolume(volume);
    }

    public VideoPlayer getVideoPlayer() {
        return player;
    }
    public void setVideoPlayer(VideoPlayer p) {
        if (player != null) {
            player.stop();
            player.dispose();
        }
        player = p;
        if (debug)
            player.setDebug(debug);
        if (mute)
            player.mute(mute);
    }

    public void setMute(boolean m) {
        mute = m;
        getVideoPlayer().mute(m);
    }

    public void setDebug(boolean d) {
        debug = d;
        getVideoPlayer().setDebug(debug);
    }
}
