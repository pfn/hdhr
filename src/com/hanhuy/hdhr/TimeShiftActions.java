package com.hanhuy.hdhr;

import com.hanhuy.common.ui.ResourceBundleForm;
import com.hanhuy.hdhr.ui.RunnableAction;
import com.hanhuy.hdhr.stream.TimeShiftStream;
import com.hanhuy.hdhr.treemodel.DeviceTreeModel;
import com.hanhuy.hdhr.treemodel.Tuner;
import com.hanhuy.hdhr.treemodel.Device;
import com.hanhuy.hdhr.config.ChannelMap.Channel.Program;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.Map;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.text.SimpleDateFormat;

import javax.swing.Action;
import javax.swing.JSlider;
import javax.swing.tree.TreePath;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import javax.swing.plaf.basic.BasicSliderUI;

public class TimeShiftActions extends ResourceBundleForm
implements TreeSelectionListener, ChangeListener {

    private JSlider timeslider;
    private static TimeShiftActions INSTANCE;

    private SimpleDateFormat sdf = new SimpleDateFormat("h:mm:ss");

    private RepeatingTask task;

    private int lastupdate;
    private int lastchange;

    private boolean ignoreStateChanged;

    private final Map<Name,Action> actions = new HashMap<Name,Action>();
    private Main main;
    public enum Name {
        PAUSE_RESUME, REWIND_LONG, REWIND_SHORT, FORWARD_SHORT, FORWARD_LONG,
        SHIFT_START, SHIFT_END, STOP
    }

    private TimeShiftActions(Main m) {
        main = m;
        initActions();
    }

    public static TimeShiftActions getInstance() {
        return INSTANCE;
    }

    static void init(Main m) {
        INSTANCE = new TimeShiftActions(m);
        INSTANCE.disableActions();
    }

    void setTimeSlider(JSlider slider) {
        timeslider = slider;
        disableActions();
    }

    public static Action getAction(Name action) {
        return INSTANCE.actions.get(action);
    }

    private void initActions() {
        actions.put(Name.PAUSE_RESUME, new RunnableAction(
                getString("playPauseName"), getInt("playPauseMnemonic"),
                getString("playPauseAccelerator"), getIcon("playIcon"),
                new Runnable() {
            public void run() {
                // noop, handled by actionPerformed below
            }
        }) {
            boolean paused;
            @Override public void setEnabled(boolean b) {
                super.setEnabled(b);
                paused = false;
                setPauseIcon(!b);
            }
            void setPauseIcon(boolean p) {
                putValue(SMALL_ICON,
                        getIcon(p ? "playIcon" : "pauseIcon"));
            }

            @Override public void actionPerformed(ActionEvent e) {
                paused = !paused;
                if (paused) {
                    ProgramCard.INSTANCE.getTimeShiftStream().pause();
                } else {
                    ProgramCard.INSTANCE.getTimeShiftStream().resume();
                }
                setPauseIcon(paused);

                getAction(Name.SHIFT_END).setEnabled(true);
                getAction(Name.FORWARD_LONG).setEnabled(true);
                getAction(Name.FORWARD_SHORT).setEnabled(true);
            }
        });
        actions.put(Name.STOP, new RunnableAction(
                getString("stopName"), getInt("stopMnemonic"),
                getString("stopAccelerator"), getIcon("stopIcon"),
                new Runnable() {
            public void run() {
                Main.tree.setSelectionPath(null);
            }
        }));
        actions.put(Name.SHIFT_START, new RunnableAction(
                getString("shiftStartName"), getInt("shiftStartMnemonic"),
                getString("shiftStartAccelerator"), getIcon("shiftStartIcon"),
                new Runnable() {
            public void run() {
                ProgramCard.INSTANCE.getTimeShiftStream().base();
                getAction(Name.SHIFT_END).setEnabled(true);
                getAction(Name.FORWARD_LONG).setEnabled(true);
                getAction(Name.FORWARD_SHORT).setEnabled(true);

                getAction(Name.PAUSE_RESUME).setEnabled(true);
            }
        }));
        actions.put(Name.SHIFT_END, new RunnableAction(
                getString("shiftEndName"), getInt("shiftEndMnemonic"),
                getString("shiftEndAccelerator"), getIcon("shiftEndIcon"),
                new Runnable() {
            public void run() {
                ProgramCard.INSTANCE.getTimeShiftStream().now();
                getAction(Name.SHIFT_END).setEnabled(false);
                getAction(Name.FORWARD_LONG).setEnabled(false);
                getAction(Name.FORWARD_SHORT).setEnabled(false);

                getAction(Name.PAUSE_RESUME).setEnabled(true);
            }
        }));
        actions.put(Name.REWIND_LONG, new RunnableAction(
                getString("rewindLongName"), getInt("rewindLongMnemonic"),
                getString("rewindLongAccelerator"), getIcon("rewindLongIcon"),
                new Runnable() {
            public void run() {
                ProgramCard.INSTANCE.getTimeShiftStream().shift(-30);
                getAction(Name.SHIFT_END).setEnabled(true);
                getAction(Name.FORWARD_LONG).setEnabled(true);
                getAction(Name.FORWARD_SHORT).setEnabled(true);

                getAction(Name.PAUSE_RESUME).setEnabled(true);
            }
        }));
        actions.put(Name.REWIND_SHORT, new RunnableAction(
                getString("rewindShortName"), getInt("rewindShortMnemonic"),
                getString("rewindShortAccelerator"), getIcon("rewindShortIcon"),
                new Runnable() {
            public void run() {
                ProgramCard.INSTANCE.getTimeShiftStream().shift(-5);
                getAction(Name.SHIFT_END).setEnabled(true);
                getAction(Name.FORWARD_LONG).setEnabled(true);
                getAction(Name.FORWARD_SHORT).setEnabled(true);

                getAction(Name.PAUSE_RESUME).setEnabled(true);
            }
        }));
        actions.put(Name.FORWARD_SHORT, new RunnableAction(
                getString("forwardShortName"), getInt("forwardShortMnemonic"),
                getString("forwardShortAccelerator"),
                getIcon("forwardShortIcon"),
                new Runnable() {
            public void run() {
                ProgramCard.INSTANCE.getTimeShiftStream().shift(5);
                if (ProgramCard.INSTANCE.getTimeShiftStream().isRealTime()) {
                    getAction(Name.SHIFT_END).setEnabled(false);
                    getAction(Name.FORWARD_LONG).setEnabled(false);
                    getAction(Name.FORWARD_SHORT).setEnabled(false);
                }

                getAction(Name.PAUSE_RESUME).setEnabled(true);
            }
        }));
        actions.put(Name.FORWARD_LONG, new RunnableAction(
                getString("forwardLongName"), getInt("forwardLongMnemonic"),
                getString("forwardLongAccelerator"),
                getIcon("forwardLongIcon"),
                new Runnable() {
            public void run() {
                ProgramCard.INSTANCE.getTimeShiftStream().shift(30);
                if (ProgramCard.INSTANCE.getTimeShiftStream().isRealTime()) {
                    getAction(Name.SHIFT_END).setEnabled(false);
                    getAction(Name.FORWARD_LONG).setEnabled(false);
                    getAction(Name.FORWARD_SHORT).setEnabled(false);
                }

                getAction(Name.PAUSE_RESUME).setEnabled(true);
            }
        }));
    }

    public void valueChanged(TreeSelectionEvent e) {
        TreePath path = e.getPath();
        Object value = path.getLastPathComponent();
        stopTask();
        if (value instanceof Program) {
            enableActions();
            task = new RepeatingTask();
            Thread t = new Thread(task, "TimeShiftActions.timeslider");
            t.start();
        } else {
            disableActions();
        }
    }

    private void enableActions() {
        if (!ProgramCard.INSTANCE.isTimeShiftEnabled())
            return;

        for (Name n : Arrays.asList(Name.PAUSE_RESUME, Name.SHIFT_START,
                Name.REWIND_LONG, Name.REWIND_SHORT, Name.STOP)) {
            getAction(n).setEnabled(true);
        }
        timeslider.setEnabled(true);
    }
    private void disableActions() {
        if (timeslider != null)
            timeslider.setEnabled(false);
        for (Name n : Name.values()) {
            getAction(n).setEnabled(false);
        }
    }

    private void stopTask() {
        if (task != null) {
            synchronized (task) {
                task.shutdown = true;
                task.notify();
            }
        }
        task = null;
    }

    public void stateChanged(ChangeEvent e) {
        synchronized (timeslider) {
            if (ignoreStateChanged) return;
            if (timeslider.getValueIsAdjusting()) return;

            TimeShiftStream ts = ProgramCard.INSTANCE.getTimeShiftStream();
            if (ts == null) return;

            int value = timeslider.getValue();
            if (value == lastupdate) return;
            if (value == lastchange) return;
            lastchange = value;

            double r = (double) value / 10000;

            long startMS = ts.getStartPCR()   / 27000;
            long nowMS   = ts.getInputPCR()   / 27000;

            long pos = ((long) (r * (nowMS - startMS))) + startMS;


            if (value != 10000) {
                ts.seek(pos * 27000);
                getAction(Name.SHIFT_END).setEnabled(true);
                getAction(Name.FORWARD_LONG).setEnabled(true);
                getAction(Name.FORWARD_SHORT).setEnabled(true);
            } else {
                ts.now();
                getAction(Name.SHIFT_END).setEnabled(false);
                getAction(Name.FORWARD_LONG).setEnabled(false);
                getAction(Name.FORWARD_SHORT).setEnabled(false);
            }
        }
    }

    /**
     * @return text appropriate for user in the timeslider
     */
    public String getToolTipText(MouseEvent e) {
        TimeShiftStream ts = ProgramCard.INSTANCE.getTimeShiftStream();
        if (!timeslider.isEnabled() || ts == null) {
            return getString("timeShiftingDisabled");
        }

        long now = System.currentTimeMillis();
        long startMS   = ts.getStartPCR()   / 27000;
        long currentMS = ts.getCurrentPCR() / 27000;
        long nowMS     = ts.getInputPCR()   / 27000;

        Date current = new Date(now - (nowMS - currentMS));

        BasicSliderUI ui = (BasicSliderUI) timeslider.getUI();

        int pos = ui.valueForXPosition(e.getPoint().x);

        double r = (double) pos / 10000;
        long targetMS = (nowMS - startMS) - ((long) (r * (nowMS - startMS)));

        Date target = new Date(now - targetMS);

        return String.format("%s (%s)", sdf.format(current),
                sdf.format(target));
    }

    private class RepeatingTask implements Runnable {
        volatile boolean shutdown;
        private TimeShiftStream ts = ProgramCard.INSTANCE.getTimeShiftStream();
        public synchronized void run() {
            while (!shutdown) {
                EventQueue.invokeLater(new Runnable() {
                    public void run() {

                        if (ts == null) return;

                        long startMS   = ts.getStartPCR()   / 27000;
                        long currentMS = ts.getCurrentPCR() / 27000;
                        long nowMS     = ts.getInputPCR()   / 27000;

                        double r = (double) (currentMS - startMS) /
                                (nowMS - startMS);
                        // set position of the slider
                        synchronized (timeslider) {
                            ignoreStateChanged = true;
                            lastupdate = (int) (r * 10000);
                            timeslider.setValue(lastupdate);
                            ignoreStateChanged = false;
                        }
                    }
                });
                try {
                    wait(500);
                }
                catch (InterruptedException e) {
                }
            }
        }
    }
}
