package com.hanhuy.hdhr;

import com.hanhuy.common.ui.DataBindingManager;
import com.hanhuy.common.ui.ResourceBundleForm;
import com.hanhuy.hdhr.treemodel.Tuner;
import com.hanhuy.hdhr.config.Control;
import com.hanhuy.hdhr.config.TunerException;

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
            lockedBy.setText(device.get(tuner + "lockkey"));
            target.setText(device.get(tuner + "target"));
            channel.setText(device.get(tuner + "channel"));
            Map<String,String> status = Control.parseStatus(
                    device.get(tuner + "status"));
            int ss  = Integer.parseInt(status.get("ss"));
            int seq = Integer.parseInt(status.get("seq"));
            int snq = Integer.parseInt(status.get("snq"));
            Color ssColor;
            Color red    = new Color(0xFF, 0, 0);
            Color yellow = new Color(0xFF, 0xCC, 0);
            Color green  = new Color(0, 0xAA, 0);
            if (Arrays.asList("8vsb", "t8", "t7", "t6").contains(
                    status.get("lock"))) {
                if (ss >= 75) // -30dBmV
                    ssColor = green;
                else if (ss >= 50) // -15dBmV
                    ssColor = yellow;
                else
                    ssColor = red;
            } else if ("none".equals(status.get("lock"))) {
                ssColor = Color.gray;
            } else {
                if (ss >= 90) // -6dBmV
                    ssColor = green;
                else if (ss >= 80) // -12dBmV
                    ssColor = yellow;
                else
                    ssColor = red;
            }
            ssBar.setValue(ss);
            ssBar.setForeground(ssColor);
            ssBar.setString(ss + "%");
            snqBar.setValue(snq);
            snqBar.setString(snq + "%");
            snqBar.setForeground(snq >= 70 ?
                    green : snq >= 50 ? yellow : red);
            seqBar.setValue(seq);
            seqBar.setString(seq + "%");
            seqBar.setForeground(seq >= 100 ? green: red);
            channelmap.setText(device.get(tuner + "channelmap"));
            filter.setText(device.get(tuner + "filter"));
            program.setText(device.get(tuner + "program"));
            debug.setText("<html><pre>" + device.get(tuner + "debug"));
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

    private RepeatingTask task = null;
    private Tuner lastTuner = null;
    public void valueChanged(TreeSelectionEvent e) {
        Object item = e.getPath().getLastPathComponent();
        if (item instanceof Tuner) {
            lastTuner = (Tuner) item;
            stopThread();
            task = new RepeatingTask();
            new Thread(task, "TunerInfoCard").start();
            Main.cards.show(Main.cardPane, CARD_NAME);
        } else {
            stopThread();
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
