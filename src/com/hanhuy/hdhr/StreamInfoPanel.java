package com.hanhuy.hdhr;

import com.hanhuy.common.ui.ResourceBundleForm;
import com.hanhuy.hdhr.treemodel.Tuner;
import com.hanhuy.hdhr.config.RTPProxy;
import com.hanhuy.hdhr.config.Control;
import com.hanhuy.hdhr.config.TunerException;
import com.hanhuy.hdhr.config.ChannelMap.Channel.Program;

import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Color;
import java.util.Map;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;

public class StreamInfoPanel extends ResourceBundleForm {

    private JLabel name        = new JLabel();
    private JLabel channel     = new JLabel();
    private JLabel program     = new JLabel();
    private JLabel target      = new JLabel();
    private JLabel bitrate     = new JLabel();
    private JLabel packetrate  = new JLabel();
    private JLabel dropped     = new JLabel();
    private JLabel transport   = new JLabel();
    private JLabel sequence    = new JLabel();

    private JProgressBar ssBar  = new JProgressBar();
    private JProgressBar snqBar = new JProgressBar();
    private JProgressBar seqBar = new JProgressBar();

    public final static Color red    = new Color(0xFF, 0, 0);
    public final static Color yellow = new Color(0xFF, 0xCC, 0);
    public final static Color green  = new Color(0, 0xAA, 0);
    private JPanel panel = new JPanel();
    public final static StreamInfoPanel INSTANCE = new StreamInfoPanel();

    private boolean showing = false;
    private JDialog d;

    private StreamInfoPanel() {
        panel.setLayout(createLayoutManager());

        panel.add(name,    "name");
        panel.add(target,  "target");
        panel.add(channel, "channel");
        panel.add(program, "program");

        ssBar.setMaximum(100);
        ssBar.setStringPainted(true);
        ssBar.setString("0%");
        seqBar.setMaximum(100);
        seqBar.setStringPainted(true);
        seqBar.setString("0%");
        snqBar.setMaximum(100);
        snqBar.setStringPainted(true);
        snqBar.setString("0%");

        panel.add(ssBar,  "ssBar");
        panel.add(snqBar, "snqBar");
        panel.add(seqBar, "seqBar");

        panel.add(bitrate,    "bitrate");
        panel.add(packetrate, "packets");
        panel.add(dropped,    "dropped");
        panel.add(transport,  "transport");
        panel.add(sequence,   "sequence");
    }

    public void show(Tuner t, Program p) {
        update(t, p);
        if (showing) {
            d.requestFocusInWindow();
            d.toFront();
            return;
        }
        showing = true;
        d = new JDialog(Main.frame, "Stream Info");
        d.add(panel);

        stopThread();
        task = new RepeatingTask();
        new Thread(task, "StreamInfoPanel").start();

        d.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                hide();
            }
        });
        show(d, Main.frame);
    }

    public void hide() {
        stopThread();
        showing = false;
        if (d != null) {
            d.setVisible(false);
            d.dispose();
        }
    }

    public void update(Tuner t, Program p) {
        RTPProxy proxy = ProgramCard.INSTANCE.getProxy();
        if (proxy == null) {
            lastPacketCount = 0;
            lastByteCount   = 0;
            hide();
            return;
        }
        lastTuner = t;
        lastProgram = p;
        name.setText(t.tuner.toString());
        Control device = new Control();
        String tuner = String.format("/tuner%d/", t.tuner.ordinal());
        try {
            device.connect(t.device.id);
            target.setText(device.get(tuner + "target"));
            channel.setText(device.get(tuner + "channel"));
            Map<String,String> status = Control.parseStatus(
                    device.get(tuner + "status"));
            int ss  = Integer.parseInt(status.get("ss"));
            int seq = Integer.parseInt(status.get("seq"));
            int snq = Integer.parseInt(status.get("snq"));
            setStatusBars(ssBar, snqBar, seqBar, ss, seq, snq,
                    status.get("lock"), false);
            program.setText(device.get(tuner + "program"));
        }
        catch (TunerException e) {
            lastTuner = null;
            lastProgram = null;
            e.printStackTrace();
            JOptionPane.showMessageDialog(Main.frame, e.getMessage());
        }
        finally {
            device.close();
        }

        Iterator<RTPProxy.PacketListener> listeners =
                proxy.getPacketListeners().iterator();
        String destinations = "";
        if (listeners.hasNext())
            destinations = listeners.next().toString();
        while (listeners.hasNext())
            destinations += "<br>" + listeners.next().toString();
        target.setText("<html>" + destinations);

        int packetCount = proxy.getPacketCount();
        long byteCount = proxy.getByteCount();
        if (lastPacketCount != 0) {
            int packets = packetCount - lastPacketCount;
            packetrate.setText(packets + " pps");
        }
        if (lastByteCount != 0) {
            long bytes = byteCount - lastByteCount;
            int kbps = (int) bytes * 8 / 1024;
            bitrate.setText(kbps + " kbps");
        }
        lastPacketCount = packetCount;
        lastByteCount = byteCount;

        dropped.setText(Integer.toString(proxy.getNetworkErrorCount()));
        transport.setText(Integer.toString(proxy.getTransportErrorCount()));
        sequence.setText(Integer.toString(proxy.getSequenceErrorCount()));
    }

    private int lastPacketCount;
    private long lastByteCount;

    private void setStatusBars(
            JProgressBar ssBar, JProgressBar snqBar, JProgressBar seqBar,
            int ss, int seq, int snq, String lock, boolean showLabels) {
        Color ssColor;
        if ("8vsb".equals(lock) ||
                Arrays.asList("t8", "t7", "t6").contains(lock.substring(0, 2)))
        {
            if (ss >= 75) // -30dBmV
                ssColor = green;
            else if (ss >= 50) // -15dBmV
                ssColor = yellow;
            else
                ssColor = red;
        } else if ("none".equals(lock)) {
            ssColor = Color.gray;
        } else {
            if (ss >= 90) // -6dBmV
                ssColor = green;
            else if (ss >= 80) // -12dBmV
                ssColor = yellow;
            else
                ssColor = red;
        }
        String label;
        ssBar.setValue(ss);
        ssBar.setForeground(ssColor);
        label = showLabels ? "SS: " + ss + "%" : ss + "%";
        ssBar.setString(label);
        snqBar.setValue(snq);
        label = showLabels ? "SNQ: " + snq + "%" : snq + "%";
        snqBar.setString(label);
        snqBar.setForeground(snq >= 70 ?
                green : snq >= 50 ? yellow : red);
        seqBar.setValue(seq);
        label = showLabels ? "SEQ: " + seq + "%" : seq + "%";
        seqBar.setString(label);
        seqBar.setForeground(seq >= 100 ? green: red);
    }

    private RepeatingTask task = null;
    private Tuner lastTuner = null;
    private Program lastProgram = null;
    private void stopThread() {
        if (task != null) {
            synchronized (task) {
                task.shutdown = true;
                task.notify();
            }
        }
    }
    class RepeatingTask implements Runnable {
        public boolean shutdown = false;
        public void run() {
            synchronized (this) {
                while (!shutdown && lastTuner != null) {
                    EventQueue.invokeLater(new Runnable() {
                        public void run() {
                            update(lastTuner, lastProgram);
                        }
                    });
                    try {
                        wait(1 * 1000);
                    }
                    catch (InterruptedException e) {
                        shutdown = true;
                    }
                }
            }
        }
    }
}
