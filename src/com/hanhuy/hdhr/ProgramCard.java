package com.hanhuy.hdhr;

import com.hanhuy.common.ui.ResourceBundleForm;
import com.hanhuy.hdhr.config.Control;
import com.hanhuy.hdhr.config.TunerException;
import com.hanhuy.hdhr.config.ChannelMap.Channel.Program;
import com.hanhuy.hdhr.treemodel.Tuner;

import java.awt.Canvas;
import java.awt.Color;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeSelectionEvent;

import org.videolan.jvlc.internal.LibVlc;
import org.videolan.jvlc.internal.LibVlc.libvlc_exception_t;
import org.videolan.jvlc.internal.LibVlc.LibVlcInstance;
import org.videolan.jvlc.internal.LibVlc.LibVlcMediaPlayer;
import org.videolan.jvlc.internal.LibVlc.LibVlcMedia;

import com.sun.jna.Native;

public class ProgramCard extends ResourceBundleForm
implements TreeSelectionListener {
    public final static ProgramCard INSTANCE = new ProgramCard();
    public final static String CARD_NAME = "ProgramCard";
    public final JPanel card;

    private final LibVlc libvlc;
    private LibVlcMediaPlayer player;
    private LibVlcInstance instance;
    private Control device;
    private final Canvas c;

    private ProgramCard() {
        card = new JPanel();
        card.setLayout(createLayoutManager());

        c = new Canvas();
        c.setBackground(Color.black);
        card.add(c, "canvas");

        libvlc = LibVlc.SYNC_INSTANCE;

    }

    private void setProgram(Tuner t, Program program) {
        final libvlc_exception_t ex = new libvlc_exception_t();

        String[] vlc_args = { "-I", "dummy", "--ignore-config",
                "--plugin-path", "c:\\program files\\videolan\\vlc",
                "--no-overlay",
                "--deinterlace-mode", "linear",
                "--video-filter=deinterlace" };
        instance = libvlc.libvlc_new(vlc_args.length, vlc_args, ex);
        raise(ex);

        LibVlcMedia media;

        media = libvlc.libvlc_media_new(instance, "udp://@:5000", ex);
        raise(ex);

        device = new Control();
        try {
            device.connect(t.device.id);
            device.lock(t.tuner);
            device.set("channel",
                    program.channel.getModulation() + ":" +
                    program.channel.number);
            device.set("program", Integer.toString(program.number));
            device.set("target", "udp://192.168.9.4:5000");
        }
        catch (TunerException e) {
            JOptionPane.showMessageDialog(Main.frame, e.getMessage());
            device.close();
            stopPlayer();
            return;
        }

        player = libvlc.libvlc_media_player_new_from_media(media, ex);
        raise(ex);

        libvlc.libvlc_media_release(media);

        Main.cards.show(Main.cardPane, CARD_NAME);

        long drawable = Native.getComponentID(c);
        libvlc.libvlc_media_player_set_drawable(player, (int)drawable, ex);
        raise(ex);

        libvlc.libvlc_media_player_play(player, ex);
        raise(ex);
    }

    public void valueChanged(TreeSelectionEvent e) {
        Object[] path = e.getPath().getPath();
        Object item = path[path.length - 1];
        if (item instanceof Program) {
            stopPlayer();
            setProgram((Tuner) path[path.length - 2], (Program) item);
        } else {
            stopPlayer();
        }
    }
    private void stopPlayer() {
        final libvlc_exception_t ex = new libvlc_exception_t();

        if (player != null) {
            libvlc.libvlc_media_player_stop(player, ex);
            libvlc.libvlc_media_player_release(player);
        }
        if (device != null) {
            try {
                device.set("target", "none");
                device.unlock();
            }
            catch (TunerException e) { }
            finally {
                device.close();
            }
        }

        if (instance != null)
            libvlc.libvlc_release(instance);

        raise(ex);
        device   = null;
        player   = null;
        instance = null;
    }

    private void raise(LibVlc.libvlc_exception_t ex) {
        if (ex.raised != 0) {
            System.out.println(ex.code);
            System.out.println(ex.message);
            System.exit(1);
        }
    }
}
