package com.hanhuy.hdhr;

import com.hanhuy.common.ui.DataBindingManager;
import com.hanhuy.common.ui.ResourceBundleForm;
import com.hanhuy.hdhr.treemodel.Tuner;
import com.hanhuy.hdhr.config.Control;
import com.hanhuy.hdhr.config.TunerException;
import com.hanhuy.hdhr.config.ChannelMap.Channel.Program;

import java.awt.EventQueue;
import java.awt.Color;
import java.util.Map;
import java.util.Arrays;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeSelectionEvent;

public class TunerInfoCard extends ResourceBundleForm
implements TreeSelectionListener {
    public final static TunerInfoCard INSTANCE = new TunerInfoCard();
    public final static String CARD_NAME = "TunerInfoCard";
    public final JPanel card;

    private JLabel name       = new JLabel();
    private JLabel channel    = new JLabel();
    private JLabel channelmap = new JLabel();
    private JLabel filter     = new JLabel();
    private JLabel program    = new JLabel();
    private JLabel target     = new JLabel();
    private JLabel debug      = new JLabel();
    private JLabel lockedBy   = new JLabel();

    private JProgressBar ssBar  = new JProgressBar();
    private JProgressBar snqBar = new JProgressBar();
    private JProgressBar seqBar = new JProgressBar();

    public final static Color red    = new Color(0xFF, 0, 0);
    public final static Color yellow = new Color(0xFF, 0xCC, 0);
    public final static Color green  = new Color(0, 0xAA, 0);

    private TunerInfoCard() {
        card = new JPanel();
        card.setLayout(createLayoutManager());

        card.add(name,       "name");
        card.add(lockedBy,   "lockedBy");
        card.add(target,     "target");
        card.add(channel,    "channel");
        card.add(channelmap, "channelmap");
        card.add(filter,     "filter");
        card.add(program,    "program");
        card.add(debug,      "debug");

        ssBar.setMaximum(100);
        ssBar.setStringPainted(true);
        ssBar.setString("0%");
        seqBar.setMaximum(100);
        seqBar.setStringPainted(true);
        seqBar.setString("0%");
        snqBar.setMaximum(100);
        snqBar.setStringPainted(true);
        snqBar.setString("0%");

        card.add(ssBar,  "ssBar");
        card.add(snqBar, "snqBar");
        card.add(seqBar, "seqBar");
    }

    public void setTuner(Tuner t) {
        name.setText(t.tuner.toString());
        Control device = new Control();
        String tuner = String.format("/tuner%d/", t.tuner.ordinal());
        try {
            device.connect(t.device.id);
            if (!task.isProgram) {
                lockedBy.setText(device.get(tuner + "lockkey"));
                target.setText(device.get(tuner + "target"));
                channel.setText(device.get(tuner + "channel"));
            }
            Map<String,String> status = Control.parseStatus(
                    device.get(tuner + "status"));
            int ss  = Integer.parseInt(status.get("ss"));
            int seq = Integer.parseInt(status.get("seq"));
            int snq = Integer.parseInt(status.get("snq"));
            if (task.isProgram) {
                setStatusBars(Main.ssBar, Main.snqBar, Main.seqBar,
                        ss, seq, snq, status.get("lock"), true);
                setNetBar();
            } else {
                setStatusBars(ssBar, snqBar, seqBar, ss, seq, snq,
                        status.get("lock"), false);
            }
            if (!task.isProgram) {
                channelmap.setText(device.get(tuner + "channelmap"));
                filter.setText(device.get(tuner + "filter"));
                program.setText(device.get(tuner + "program"));
                debug.setText("<html><pre>" + device.get(tuner + "debug"));
            }
        }
        catch (TunerException e) {
            lastTuner = null;
            e.printStackTrace();
            JOptionPane.showMessageDialog(Main.frame, e.getMessage());
        }
        finally {
            device.close();
        }
    }

    private int lastCount;
    private int lastErrorCount;
    private void setNetBar() {
        if (ProgramCard.INSTANCE.getProxy() == null) return;
        int count = ProgramCard.INSTANCE.getProxy().getPacketCount();
        int packets = count - lastCount;

        int errors = ProgramCard.INSTANCE.getProxy().getErrorsCount();
        int le = errors - lastErrorCount;
        int quality = (int) ((1.0 - ((double) le / packets)) * 100);

        Main.netBar.setValue(quality);
        String s = String.format(
                "q=%d%% pps=%d err=%d", quality, packets, errors);
        Main.netBar.setString(s);
        if (ProgramCard.INSTANCE.isDebug())
            System.out.println("[tuner status] " + s);

        Color barColor;
        if (quality > 95) 
            barColor = green;
        else if (quality > 90)
            barColor = yellow;
        else
            barColor = red;
        Main.netBar.setForeground(barColor);
        lastCount = count;
        lastErrorCount = errors;
    }

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
    public void valueChanged(TreeSelectionEvent e) {
        stopThread();
        Object[] path = e.getPath().getPath();
        Object item = path[path.length - 1];

        Main.netBar.setVisible(false);
        Main.ssBar.setVisible(false);
        Main.seqBar.setVisible(false);
        Main.snqBar.setVisible(false);
        if (item instanceof Tuner) {
            lastTuner = (Tuner) item;
            task = new RepeatingTask();
            new Thread(task, "TunerInfoCard").start();
            Main.cards.show(Main.cardPane, CARD_NAME);
        } else if (item instanceof Program && e.isAddedPath()) {
            lastTuner = (Tuner) path[path.length - 2];

            task = new RepeatingTask();
            task.isProgram = true;
            new Thread(task, "TunerInfo-ProgramCard").start();

            Main.netBar.setVisible(true);
            Main.ssBar.setVisible(true);
            Main.seqBar.setVisible(true);
            Main.snqBar.setVisible(true);
        }
    }
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
        public volatile boolean isProgram = false;
        public void run() {
            synchronized (this) {
                while (!shutdown && lastTuner != null) {
                    EventQueue.invokeLater(new Runnable() {
                        public void run() {
                            setTuner(lastTuner);
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
