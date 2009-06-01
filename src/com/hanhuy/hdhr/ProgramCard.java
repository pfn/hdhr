package com.hanhuy.hdhr;

import com.hanhuy.common.ui.ResourceBundleForm;
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

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSlider;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeSelectionEvent;

import org.videolan.jvlc.internal.LibVlc;
import org.videolan.jvlc.internal.LibVlc.libvlc_exception_t;
import org.videolan.jvlc.internal.LibVlc.LibVlcInstance;
import org.videolan.jvlc.internal.LibVlc.LibVlcMediaPlayer;
import org.videolan.jvlc.internal.LibVlc.LibVlcMedia;

import com.sun.jna.Native;
import com.sun.jna.Platform;

public class ProgramCard extends ResourceBundleForm
implements TreeSelectionListener, ChangeListener {
    public final static ProgramCard INSTANCE = new ProgramCard();
    public final static String CARD_NAME = "ProgramCard";
    public final JPanel card;

    private final LibVlc libvlc;
    private LibVlcMediaPlayer player;
    private LibVlcInstance instance;
    private Control device;
    private final Canvas c;
    private String libraryPath;

    private int volume = 100;

    private ProgramCard() {
        card = new JPanel();
        card.setLayout(createLayoutManager());

        c = new Canvas();

        c.setBackground(Color.black);
        card.add(c, "canvas");


        boolean isJWS = System.getProperty("javawebstart.version") != null;

        if (isJWS) {
            libraryPath = Native.getWebStartLibraryPath("libvlc");
            if (libraryPath != null)
                System.setProperty("jna.library.path", libraryPath);
        }

        if (Platform.isLinux()) {
            System.setProperty("jna.library.path", "/usr/lib:/usr/local/lib");
        }
        libvlc = LibVlc.SYNC_INSTANCE;
    }

    private void setProgram(Tuner t, Program program) {
        final libvlc_exception_t ex = new libvlc_exception_t();

        String defaultLibraryPath = "C:\\Program Files\\VideoLAN\\vlc";
        if (Platform.isLinux())
            defaultLibraryPath = "/usr/lib/vlc";

        String pluginPath = libraryPath != null ?
                libraryPath : defaultLibraryPath;
        String[] vlc_args = {
                "-I", "dummy",
                //"--ignore-config",
                "--plugin-path", libraryPath,
                "--no-overlay",
                "--no-video-title-show",
                "--no-osd",
                "--quiet", "1",
                "--verbose", "0",
                "--ffmpeg-skiploopfilter=4",
                "--mouse-hide-timeout", "100",
                "--deinterlace-mode", "linear",
                "--video-filter=deinterlace"
                };
        instance = libvlc.libvlc_new(vlc_args.length, vlc_args, ex);
        throwError(ex);

        libvlc.libvlc_audio_set_volume(instance, volume, ex);
        throwError(ex);

        LibVlcMedia media;

        media = libvlc.libvlc_media_new(instance, "udp://@:5000", ex);
        throwError(ex);

        device = new Control();
        try {
            device.connect(t.device.id);
            Map<Integer,InetAddress[]> devices = Discover.discover(t.device.id);
            InetAddress[] endpoints = devices.get(t.device.id);
            String ip = endpoints[0].getHostAddress();
            device.lock(t.tuner);
            device.set("channel",
                    program.channel.getModulation() + ":" +
                    program.channel.number);
            device.set("program", Integer.toString(program.number));
            device.set("target", "udp://" + ip + ":5000");
        }
        catch (TunerException e) {
            JOptionPane.showMessageDialog(Main.frame, e.getMessage());
            device.close();
            stopPlayer();
            return;
        }

        player = libvlc.libvlc_media_player_new_from_media(media, ex);
        throwError(ex);

        libvlc.libvlc_media_release(media);

        Main.cards.show(Main.cardPane, CARD_NAME);

        { // set_drawable only works properly on 32bit
            long drawable = Native.getComponentID(c);
            libvlc.libvlc_media_player_set_drawable(player, (int)drawable, ex);
        }
        // vlc 1.0 will deprecate the above api
        if (Platform.isWindows()) {
        } else if (Platform.isX11()) {
        } else if (Platform.isMac()) {
        }
        throwError(ex);

        libvlc.libvlc_media_player_play(player, ex);
        throwError(ex);
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
    void stopPlayer() {
        libvlc_exception_t ex = null;

        if (player != null) {
            ex = new libvlc_exception_t();
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

        throwError(ex);
        device   = null;
        player   = null;
        instance = null;
    }

    private void throwError(LibVlc.libvlc_exception_t ex) {
        if (ex != null && ex.raised != 0)
            throw new RuntimeException(ex.code + ": " + ex.message);
    }

    /**
     * volume range of -18dB to 3.0dB
     *
     * 16 + 3 = 19 * 10 = 190
     */
    public void stateChanged(ChangeEvent e) {
        JSlider slider = (JSlider) e.getSource();
        int value = slider.getValue();
        double dB = (double) (value - 160) / 10;

        int linear = (int) Math.round(100.0 * Math.pow(10, dB/10));

        volume = Math.min(linear, 200);

        if (instance != null) {
            libvlc_exception_t ex = new libvlc_exception_t();
            libvlc.libvlc_audio_set_volume(instance, volume, ex);
            throwError(ex);
        }
    }
}
