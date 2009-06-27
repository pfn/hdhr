package com.hanhuy.hdhr;

import com.hanhuy.common.ui.ResourceBundleForm;
import com.hanhuy.hdhr.treemodel.DeviceTreeModel;
import com.hanhuy.hdhr.treemodel.Tuner;
import com.hanhuy.hdhr.ui.ProgressAwareRunnable;
import com.hanhuy.hdhr.ui.ProgressBar;
import com.hanhuy.hdhr.config.ChannelScan;
import com.hanhuy.hdhr.config.ChannelMap.Channel.Program;
import com.hanhuy.hdhr.config.TunerException;
import com.hanhuy.hdhr.config.TunerLineupException;
import com.hanhuy.hdhr.config.Control;
import com.hanhuy.hdhr.config.LineupServer;

import java.awt.EventQueue;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.JOptionPane;

class ScanningRunnable extends ResourceBundleForm
implements ProgressAwareRunnable {
    volatile boolean cancelled = false;
    private final boolean keepOld;
    ScanningRunnable(Tuner t, boolean keepOld) {
        this.t = t;
        this.keepOld = keepOld;
    }
    ProgressBar bar;
    Tuner t;
    public void setProgressBar(ProgressBar b) {
        bar = b;
    }
    public void run() {
        Control device = new Control();
        try {
            bar.setStringPainted(true);
            bar.setString(format("connectingToDevice",
                    Integer.toHexString(t.device.id)));
            device.connect(t.device.id);
            bar.setString(format("lockingTuner", t.tuner));
            device.lock(t.tuner);
            bar.setString(getString("scanning"));
            final boolean[] hasTSID = new boolean[1];
            hasTSID[0] = false;
            final List<Program> programs = new ArrayList<Program>();
            ChannelScan.scan(device, new ChannelScan.ScanListener() {
                int progress = 0;
                boolean configured = false;
                int found = 0;
                public void scanningChannel(ChannelScan.ScanEvent e) {
                    if (cancelled) e.cancelScan();
                    if (!configured) {
                        bar.setIndeterminate(false);
                        bar.setMinimum(0);
                        bar.setMaximum(e.map.getChannels().size() * 2);
                        bar.setValue(0);
                        configured = true;
                    }
                    bar.setString(format("scanStatus", e.channel, found));
                    bar.setValue(bar.getValue() + 1);
                    System.out.println(String.format(
                            "Scanning %d/%d: %s",
                            e.index, e.map.getChannels().size(), e.channel));
                }
                public void foundChannel(ChannelScan.ScanEvent e) {
                    if (cancelled) e.cancelScan();
                    bar.setValue(bar.getValue() + 1);
                    System.out.println("Locked: " + e.getStatus());
                }
                public void skippedChannel(ChannelScan.ScanEvent e) {
                    if (cancelled) e.cancelScan();
                    bar.setValue(bar.getValue() + 1);
                    System.out.println("Skipped: " +
                            (e.getStatus() != null ?
                                    e.getStatus() : "impossible channel"));
                }
                public void programsFound(ChannelScan.ScanEvent e) {
                    if (cancelled) e.cancelScan();
                    found += e.channel.getPrograms().size();
                    programs.addAll(e.channel.getPrograms());

                    System.out.println("BEGIN STREAMINFO:\n" + e.streaminfo +
                            ":END STREAMINFO");
                    for (Program p : e.channel.getPrograms())
                        System.out.println(p);
                    hasTSID[0] |= e.channel.getTsID() != -1;
                }
                public void programsNotFound(ChannelScan.ScanEvent e) {
                    if (cancelled) e.cancelScan();
                    System.out.println("BEGIN STREAMINFO:" + e.streaminfo +
                            ":END STREAMINFO");
                    System.out.println("No available programs found");
                }
            });
            device.unlock();
            if (cancelled)
                return;

            List<Program> oldPrograms = Main.model.programMap.get(t);
            if (oldPrograms == null)
                oldPrograms = new ArrayList<Program>();
            int oldCount = oldPrograms.size();
            long now = System.currentTimeMillis();
            if (!keepOld) {
                oldPrograms.retainAll(programs);
                for (Program p : oldPrograms) {
                    int idx = programs.indexOf(p);
                    Program program = programs.get(idx);
                    p.channel.scanTime = now;
                    p.channel.setTsID(program.channel.getTsID());
                }
            } else {
                for (Program p : oldPrograms) {
                    if (programs.contains(p)) {
                        int idx = programs.indexOf(p);
                        Program program = programs.get(idx);
                        p.channel.scanTime = now;
                        p.channel.setTsID(program.channel.getTsID());
                    }
                }
            }
            int oldRemoved = oldCount - oldPrograms.size();
            programs.removeAll(oldPrograms);
            oldPrograms.addAll(programs);
            if (oldCount > 0 && !keepOld)
                System.out.println("Removed " + oldRemoved + " old programs");
            System.out.println("Found " + programs.size() + " programs");
            try {
                if (hasTSID[0]) {
                    bar.setIndeterminate(true);
                    bar.setString("Matching lineup with SiliconDust");
                    LineupServer ls = new LineupServer(
                            device.get("/lineup/location"), t.device.id,
                            Preferences.getInstance().userUUID);
                    ls.identifyPrograms(oldPrograms);
                }
            }
            catch (final TunerLineupException e) {
                e.printStackTrace();
                EventQueue.invokeLater(new Runnable() {
                    public void run() {
                        JOptionPane.showMessageDialog(
                                Main.frame, e.getMessage());
                    }
                });
            }
            bar.setIndeterminate(false);
            bar.setMaximum(100);
            bar.setValue(100);
            bar.setString(getString("scanComplete"));
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    Main.model.fireTreeStructureChanged(new Object[] {
                            DeviceTreeModel.ROOT_NODE, t.device, t });
                }
            });
            String message;
            if (oldCount > 0 && !keepOld) {
                message = format("scanUpdatedPrograms",
                        programs.size(), oldRemoved);
            } else if (oldCount > 0) {
                message = format("scanNewPrograms",
                        programs.size());
            } else {
                message = format("scanFoundPrograms",
                        Main.model.programMap.get(t).size());
            }
            JOptionPane.showMessageDialog(Main.frame, message);
        }
        catch (final TunerException e) {
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    JOptionPane.showMessageDialog(
                            Main.frame, e.getMessage());
                }
            });
            e.printStackTrace();
        }
        finally {
            device.close();
        }
    }
}
