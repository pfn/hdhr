package com.hanhuy.hdhr;

import com.hanhuy.common.ui.ResourceBundleForm;
import com.hanhuy.common.ui.CollectionBackedListModel;
import com.hanhuy.common.ui.ConsoleViewer;
import com.hanhuy.hdhr.ui.ProgressAwareRunnable;
import com.hanhuy.hdhr.ui.ProgressDialog;
import com.hanhuy.hdhr.ui.ProgressBar;
import com.hanhuy.hdhr.ui.RunnableAction;
import com.hanhuy.hdhr.treemodel.DeviceTreeModel;
import com.hanhuy.hdhr.treemodel.Tuner;
import com.hanhuy.hdhr.treemodel.Device;
import com.hanhuy.hdhr.config.Control;
import com.hanhuy.hdhr.config.ChannelMap.Channel.Program;
import com.hanhuy.hdhr.config.TunerException;
import com.hanhuy.hdhr.config.TunerLineupException;
import com.hanhuy.hdhr.config.LineupServer;
import com.hanhuy.hdhr.config.LineupWeb;

import java.awt.EventQueue;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Properties;

import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JDialog;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.JPanel;
import javax.swing.tree.TreePath;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeSelectionEvent;

public class Actions extends ResourceBundleForm
implements TreeSelectionListener {

    private LineupWeb l;
    private TreePath previousProgram;

    private static Actions INSTANCE;

    private final Map<Name,Action> actions = new HashMap<Name,Action>();
    private Main main;
    public enum Name {
        SHOW_CONSOLE, EXIT,
        DISCOVER,
        INCREASE_FONT_SIZE, DECREASE_FONT_SIZE, RESET_FONT_SIZE,
        JUMP_TO_LAST_PROGRAM,
        ABOUT,
        // need tuner set
        SCAN, UNLOCK_TUNER, UNSET_TARGET, UNSET_CHANNEL,
        COPY_LINEUP, MATCH_LINEUP, EDIT_LINEUP, CLEAR_LINEUP,
        // need tuner and program set
        EDIT_PROGRAM, STREAM_INFO,
    }
    private Actions(Main m) {
        main = m;
        initActions();
    }

    public static Actions getInstance() {
        return INSTANCE;
    }

    static void init(Main m) {
        INSTANCE = new Actions(m);
        INSTANCE.disableTogglableActions();
    }

    public static Action getAction(Name action) {
        return INSTANCE.actions.get(action);
    }

    public static HDHRAwareAction getHDHRAction(Name action) {
        return (HDHRAwareAction) getAction(action);
    }
    private void initActions() {
        actions.put(Name.SHOW_CONSOLE, new RunnableAction(
                getString("showConsoleName"), getInt("showConsoleMnemonic"),
                getString("showConsoleAccelerator"), new Runnable() {
            public void run() {
                new ConsoleViewer(Main.frame);
            }
        }) {
            {
                setEnabled(
                        System.getProperty("com.hanhuy.hdhr.console") != null);
            }
        });
        actions.put(Name.EXIT, new RunnableAction(
                getString("exitName"), getInt("exitMnemonic"),
                getString("exitAccelerator"), new Runnable() {
            public void run() {
                main.exit();
            }
        }));
        actions.put(Name.INCREASE_FONT_SIZE, new RunnableAction(
                getString("increaseFontName"), getInt("increaseFontMnemonic"),
                        getString("increaseFontAccelerator"), new Runnable() {
            public void run() {
                main.changeFontSize(true);
            }
        }));
        actions.put(Name.RESET_FONT_SIZE, new RunnableAction(
                getString("resetFontName"), getInt("resetFontMnemonic"),
                getString("resetFontAccelerator"), new Runnable() {
            public void run() {
                main.resetFontSize();
            }
        }));
        actions.put(Name.DECREASE_FONT_SIZE, new RunnableAction(
                getString("decreaseFontName"), getInt("decreaseFontMnemonic"),
                getString("decreaseFontAccelerator"), new Runnable() {
            public void run() {
                main.changeFontSize(false);
            }
        }));
        actions.put(Name.JUMP_TO_LAST_PROGRAM, new RunnableAction(
                getString("jumpToLastName"), getInt("jumpToLastMnemonic"),
                getString("jumpToLastAccelerator"), new Runnable() {
            public void run() {
                TreePath last = previousProgram;
                if (last == null)
                    last = Preferences.getInstance().lastViewedPath;
                if (last == null) {
                    JOptionPane.showMessageDialog(Main.frame,
                            getString("jumpToLastError"));
                    return;
                }
                main.tree.expandPath(last.getParentPath());
                main.tree.scrollPathToVisible(last);
                main.tree.setSelectionPath(last);
            }
        }));
        actions.put(Name.ABOUT, new RunnableAction(getString("aboutName"),
                getInt("aboutMnemonic"), new Runnable() {
            public void run() {
                java.io.InputStream in = getClass().getResourceAsStream(
                        "version.properties");
                if (in == null) {
                    JOptionPane.showMessageDialog(
                            Main.frame, getString("aboutError"));
                    return;
                }
                try {
                    Properties p = new Properties();
                    p.load(in);

                    String version = p.getProperty("revision");
                    String timestamp = p.getProperty("time");
                    String message = format("aboutString", version, timestamp);
                    JOptionPane.showMessageDialog(Main.frame,
                            message, getString("aboutName"),
                            JOptionPane.PLAIN_MESSAGE);
                }
                catch (IOException e) {
                    JOptionPane.showMessageDialog(
                            Main.frame, getString("aboutError"));
                }
                finally {
                    try {
                        in.close();
                    }
                    catch (IOException e) { }
                }
            }
        }));
        actions.put(Name.SCAN, new HDHRAwareAction(getString("scanName"),
                getInt("scanMnemonic"), new HDHRAwareRunnable() {
            public void run() {
                Tuner t = getTuner();
                Control c = new Control();
                try {
                    c.connect(t.device.id);
                    String cmd = String.format("/tuner%d/target",
                            t.tuner.ordinal());
                    String target = c.get(cmd);
                    cmd = String.format("/tuner%d/lockkey",
                            t.tuner.ordinal());
                    String lock = c.get(cmd);
                    if (!"none".equals(lock)) {
                        JOptionPane.showMessageDialog(Main.frame,
                                format("scanTunerLocked", lock),
                                getString("scanTunerInUseTitle"),
                                JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    if (!"none".equals(target)) {
                        int r = JOptionPane.showConfirmDialog(
                                Main.frame, getString("scanTunerInUseConfirm"),
                                getString("scanTunerInUseTitle"),
                                JOptionPane.OK_CANCEL_OPTION);
                        if (r != JOptionPane.OK_OPTION)
                            return;
                    }
                }
                catch (TunerException e) {
                    JOptionPane.showMessageDialog(Main.frame,
                            getString("scanTunerInUseError"));
                    e.printStackTrace();
                }
                finally {
                    c.close();
                }
                List<Program> p = Main.model.programMap.get(t);
                boolean keep = false;
                if (p != null && p.size() > 0) {
                    int r = JOptionPane.showConfirmDialog(
                            Main.frame, getString("scanKeepConfirm"),
                            getString("scanName"), JOptionPane.YES_NO_OPTION);
                    keep = r == JOptionPane.YES_OPTION;
                }
                ProgressDialog d = new ProgressDialog(
                        Main.frame, getString("scanTitle"));
                final ScanningRunnable r = new ScanningRunnable(t, keep);
                d.showProgress(r, new ProgressAwareRunnable() {
                    ProgressBar b;
                    public void setProgressBar(ProgressBar b) {
                        this.b = b;
                    }
                    public void run() {
                        b.setIndeterminate(true);
                        b.setString(getString("scanCancelString"));
                        r.cancelled = true;
                    }
                });
                if (r.cancelled) return;
                JOptionPane.showMessageDialog(Main.frame,
                        format("scanFoundPrograms",
                                Main.model.programMap.get(t).size()));
            }
        }));
        actions.put(Name.UNLOCK_TUNER, new HDHRAwareAction(
                getString("unlockTunerName"), getInt("unlockTunerMnemonic"),
                new HDHRAwareRunnable() {
            public void run() {
                Control c = new Control();
                Tuner t = getTuner();
                try {
                    c.connect(t.device.id);
                    String lock = c.get(
                            "/tuner" + t.tuner.ordinal() + "/lockkey");
                    if ("none".equals(lock)) {
                        JOptionPane.showMessageDialog(Main.frame,
                            getString("unlockTunerNotLocked"));
                        return;
                    }
                    int r = JOptionPane.showConfirmDialog(
                            Main.frame, getString("unlockTunerForce"),
                            getString("unlockTunerName"),
                            JOptionPane.YES_NO_OPTION);
                    if (r != JOptionPane.YES_OPTION)
                        return;
                    ProgramCard.INSTANCE.stopPlayer();
                    c.force(t.tuner);
                }
                catch (TunerException e) {
                    JOptionPane.showMessageDialog(Main.frame, e.getMessage());
                }
                finally {
                    c.close();
                }
            }
        }));
        actions.put(Name.UNSET_TARGET, new HDHRAwareAction(
                getString("unsetTargetName"), getInt("unsetTargetMnemonic"),
                new HDHRAwareRunnable() {
            public void run() {
                Control c = new Control();
                Tuner t = getTuner();
                try {
                    c.connect(t.device.id);
                    String target = String.format("/tuner%d/target",
                            t.tuner.ordinal());
                    String lock = c.get(target);
                    if ("none".equals(lock)) {
                        JOptionPane.showMessageDialog(Main.frame,
                            getString("unsetTargetNotStreaming"));
                        return;
                    }
                    int r = JOptionPane.showConfirmDialog(
                            Main.frame, getString("unsetTargetConfirm"),
                            getString("unsetTargetName"),
                            JOptionPane.YES_NO_OPTION);
                    if (r != JOptionPane.YES_OPTION)
                        return;
                    ProgramCard.INSTANCE.stopPlayer();
                    c.set(target, "none");
                }
                catch (TunerException e) {
                    JOptionPane.showMessageDialog(Main.frame, e.getMessage());
                }
                finally {
                    c.close();
                }
            }
        }));
        actions.put(Name.UNSET_CHANNEL, new HDHRAwareAction(
                getString("unsetChannelName"), getInt("unsetChannelMnemonic"),
                new HDHRAwareRunnable() {
            public void run() {
                Control c = new Control();
                Tuner t = getTuner();
                try {
                    c.connect(t.device.id);
                    String channel = String.format("/tuner%d/channel",
                            t.tuner.ordinal());
                    String tune = c.get(channel);
                    if ("none".equals(tune)) {
                        JOptionPane.showMessageDialog(Main.frame,
                            getString("unsetChannelNotSet"));
                        return;
                    }
                    int r = JOptionPane.showConfirmDialog(
                            Main.frame, getString("unsetChannelConfirm"),
                            getString("unsetChannelName"),
                            JOptionPane.YES_NO_OPTION);
                    if (r != JOptionPane.YES_OPTION)
                        return;
                    ProgramCard.INSTANCE.stopPlayer();
                    c.set(channel, "none");
                }
                catch (TunerException e) {
                    JOptionPane.showMessageDialog(Main.frame, e.getMessage());
                }
                finally {
                    c.close();
                }
            }
        }));
        actions.put(Name.COPY_LINEUP, new HDHRAwareAction(
                getString("copyScanName"), getInt("copyScanMnemonic"),
                new HDHRAwareRunnable() {
            public void run() {
                Tuner t = getTuner();
                int count = Main.model.getChildCount(DeviceTreeModel.ROOT_NODE);
                ArrayList<Tuner> tuners = new ArrayList<Tuner>();
                for (int i = 0; i < count; i++) {
                    Device device = (Device) Main.model.getChild(
                            DeviceTreeModel.ROOT_NODE, i);
                    int dCount = Main.model.getChildCount(device);
                    for (int j = 0; j < dCount; j++) {
                        Tuner tuner = (Tuner) Main.model.getChild(device, j);
                        if (t == tuner) continue;
                        if (Main.model.getChildCount(tuner) > 0) {
                            tuners.add(tuner);
                        }
                    }
                }

                if (tuners.size() < 1) {
                    JOptionPane.showMessageDialog(Main.frame,
                            getString("copyScanNoChannels"));
                    return;
                }
                JList list = new JList(tuners.toArray());
                list.setSelectedIndex(0);
                list.setLayoutOrientation(JList.VERTICAL);

                JScrollPane pane = new JScrollPane(list,
                        JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                Object[] message = new Object[2];
                message[0] = getString("copyScanPrompt");
                message[1] = pane;
                int r = JOptionPane.showConfirmDialog(Main.frame, message,
                        getString("copyScanName"),
                        JOptionPane.OK_CANCEL_OPTION);
                if (r != JOptionPane.OK_OPTION)
                    return;
                Tuner value = (Tuner) list.getSelectedValue();
                if (value == null) {
                    JOptionPane.showMessageDialog(Main.frame,
                            getString("copyScanNotSelected"));
                    return;
                }
                Main.model.programMap.put(t, Main.model.programMap.get(value));
                Main.model.fireTreeStructureChanged(new Object[] {
                        DeviceTreeModel.ROOT_NODE, t.device, t });
            }
        }));
        actions.put(Name.EDIT_LINEUP, new HDHRAwareAction(
                getString("editLineupName"), getInt("editLineupMnemonic"),
                new HDHRAwareRunnable() {
            public void run() {
                Tuner t = getTuner();
                List<Program> programs = Main.model.programMap.get(t);
                new EditChannelLineupForm(t, programs);
            }
        }));
        actions.put(Name.CLEAR_LINEUP, new HDHRAwareAction(
                getString("clearLineupName"), getInt("clearLineupMnemonic"),
                new HDHRAwareRunnable() {
            public void run() {
                Tuner t = getTuner();
                int r = JOptionPane.showConfirmDialog(
                        Main.frame, getString("clearLineupConfirm"),
                        getString("clearLineupName"),
                        JOptionPane.OK_CANCEL_OPTION);
                if (r != JOptionPane.OK_OPTION)
                    return;
                Main.model.programMap.get(t).clear();
                Main.model.fireTreeStructureChanged(
                        DeviceTreeModel.ROOT_NODE, t.device, t);
            }
        }));
        actions.put(Name.MATCH_LINEUP, new HDHRAwareAction(
                getString("matchLineupName"), getInt("matchLineupMnemonic"),
                new HDHRAwareRunnable() {
            public void run() {
                final Tuner t = getTuner();
                final List<Program> programs = Main.model.programMap.get(t);
                if (programs == null || programs.size() == 0) {
                    JOptionPane.showMessageDialog(
                            Main.frame, getString("matchLineupNoScan"));
                    return;
                }
                final int deviceId = t.device.id;
                Control c = new Control();
                String location;
                try {
                    c.connect(deviceId);
                    location = c.get("/lineup/location");
                }
                catch (TunerException e) {
                    JOptionPane.showMessageDialog(Main.frame, e.getMessage());
                    return;
                }
                finally {
                    c.close();
                }


                boolean hasTSID = false;
                for (Program program : programs)
                    hasTSID |= program.channel.getTsID() != 0;

                if (hasTSID) {
                    ProgressDialog d = new ProgressDialog(
                            Main.frame, getString("matchLineupSiliconDust"));
                    final String loc = location;
                    d.showProgress(new Runnable() {
                        public void run() {
                            try {
                                LineupServer ls = new LineupServer(
                                        loc, deviceId,
                                        Preferences.getInstance().userUUID);
                                final int count = ls.identifyPrograms(programs);
                                EventQueue.invokeLater(new Runnable() {
                                    public void run() {
                                        JOptionPane.showMessageDialog(
                                                Main.frame,
                                                format("matchLineupPrograms",
                                                        count));

                                    }
                                });
                            }
                            catch (final TunerException e) {
                                EventQueue.invokeLater(new Runnable() {
                                    public void run() {
                                        JOptionPane.showMessageDialog(
                                                Main.frame, e.getMessage());
                                    }
                                });
                            }
                        }
                    });
                } else {
                    String[] databases;
                    if (l == null) {
                        l = new LineupWeb(location);
                        final String[][] p = new String[1][];
                        ProgressDialog d = new ProgressDialog(
                                Main.frame, getString("matchLineupFetch"));
                        d.showProgress(new Runnable() {
                            public void run() {
                                try {
                                    p[0] = l.getDatabaseIDs();
                                }
                                catch (final IOException e) {
                                    EventQueue.invokeLater(new Runnable() {
                                        public void run() {
                                            JOptionPane.showMessageDialog(
                                                    Main.frame, e.getMessage());
                                        }
                                    });
                                }
                            }
                        });
                        databases = p[0];
                    } else {
                        try {
                            databases = l.getDatabaseIDs();
                        }
                        catch (IOException e) {
                            JOptionPane.showMessageDialog(
                                    Main.frame, e.getMessage());
                            return;
                        }
                    }
                    JRadioButton[] buttons = new JRadioButton[databases.length];
                    ButtonGroup g = new ButtonGroup();
                    for (int i = 0; i < databases.length; i++) {
                        String label = format("matchLineupNames",
                                l.getProgramCount(databases[i]),
                                l.getDisplayName(databases[i]));
                        JRadioButton b = new JRadioButton(label);
                        g.add(b);
                        buttons[i] = b;
                    }
                    JOptionPane.showMessageDialog(Main.frame, buttons,
                            getString("matchLineupPrompt"),
                            JOptionPane.QUESTION_MESSAGE);
                    int selectedDB = -1;
                    for (int i = 0;
                            i < buttons.length && selectedDB == -1; i++) {
                        if (buttons[i].isSelected()) {
                            selectedDB = i;
                            break;
                        }
                    }
                    if (selectedDB != -1) {
                        final String selectedID = databases[selectedDB];
                        ProgressDialog d = new ProgressDialog(
                                Main.frame, getString("matchLineupMatching"));
                        final int[] count = new int[1];
                        d.showProgress(new Runnable() {
                            public void run() {
                                count[0] = 0;
                                for (Program program : programs) {
                                    if (l.applyProgramSettings(
                                            selectedID, program)) {
                                        count[0]++;
                                    } else {
                                        program.setName(getString(
                                                "matchLineupUnknown"));
                                        program.virtualMajor = 0;
                                        program.virtualMinor = 0;
                                    }
                                }
                            }
                        });
                        JOptionPane.showMessageDialog(Main.frame,
                                format("matchLineupPrograms", count[0]));
                        Main.model.fireTreeStructureChanged(new Object[] {
                                DeviceTreeModel.ROOT_NODE, t.device, t });
                    }
                }
            }
        }));
        actions.put(Name.DISCOVER, new RunnableAction(getString("discoverName"),
                getInt("discoverMnemonic"), new Runnable() {
            public void run() {
                Main.model.rediscover();
            }
        }));
        actions.put(Name.EDIT_PROGRAM, new HDHRAwareAction(
                getString("editProgramName"), getInt("editProgramMnemonic"),
                new HDHRAwareRunnable() {
            public void run() {
                Tuner t = getTuner();
                int deviceId = t.device.id;
                Program p = getProgram();

                Object[] message = new Object[2];

                JTextField nameField = new JTextField(p.getName(), 32);
                JPanel panel = new JPanel();
                panel.add(new JLabel(getString("editProgramNameLabel")));
                panel.add(nameField);
                message[0] = panel;

                JTextField numberField = new JTextField(p.getGuideNumber(), 8);
                panel = new JPanel();
                panel.add(new JLabel(getString("editProgramGuideLabel")));
                panel.add(numberField);
                message[1] = panel;

                JOptionPane pane = new JOptionPane(message,
                        JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION,
                        null, null, null);
                JDialog dialog = pane.createDialog(Main.frame,
                        getString("editProgramTitle"));
                pane.selectInitialValue();
                dialog.setVisible(true);
                dialog.dispose();

                Integer result = (Integer) pane.getValue();
                String name   = nameField.getText();
                String number = numberField.getText();
                if (result != null && result == JOptionPane.OK_OPTION) {
                    if (name == null || name.trim().equals("")) {
                        JOptionPane.showMessageDialog(Main.frame,
                                getString("editProgramNameError"));
                        return;
                    }
                    if (!isValidNumber(number)) {
                        JOptionPane.showMessageDialog(Main.frame,
                                getString("editProgramGuideError"));
                        return;
                    }
                }

                p.setName(name);
                int idx = number.indexOf(".");
                if (idx == -1) {
                    p.virtualMajor = Short.parseShort(number);
                } else {
                    p.virtualMajor = Short.parseShort(number.substring(0, idx));
                    p.virtualMinor =
                            Short.parseShort(number.substring(idx + 1));
                }

                List<Program> programs = Main.model.programMap.get(t);
                boolean hasTSID = false;
                for (Program program : programs)
                    hasTSID |= program.channel.getTsID() != 0;

                if (hasTSID) {
                    Control d = new Control();
                    try {
                        d.connect(deviceId);
                        LineupServer ls = new LineupServer(
                                d.get("/lineup/location"), deviceId,
                                Preferences.getInstance().userUUID);
                        ls.updatePrograms(Arrays.asList(p));
                    }
                    catch (TunerException e) {
                        JOptionPane.showMessageDialog(Main.frame,
                                e.getMessage());
                    }
                    finally {
                        d.close();
                    }
                }

                TreePath path = Main.model.getTunerPath(t);
                path = path.pathByAddingChild(p);
                Main.model.fireTreeNodesChanged(path);
            }

            private boolean isValidNumber(String number) {
                return number.matches("\\d+(\\.\\d+)?");
            }
        }));
        actions.put(Name.STREAM_INFO, new HDHRAwareAction(
                getString("streamInfoName"), getInt("streamInfoMnemonic"),
                new HDHRAwareRunnable() {
            public void run() {
                Tuner t = getTuner();
                Program p = getProgram();

                StreamInfoPanel.INSTANCE.show(t, p);
            }

            private boolean isValidNumber(String number) {
                return number.matches("\\d+(\\.\\d+)?");
            }
        }));
    }

    public void valueChanged(TreeSelectionEvent e) {
        TreePath path = e.getPath();
        Object value = path.getLastPathComponent();
        disableTogglableActions();
        if (value instanceof Program) {
            previousProgram = Preferences.getInstance().lastViewedPath;
            Preferences.getInstance().lastViewedPath = path;

            Program p = (Program) value;
            Tuner t = (Tuner) path.getParentPath().getLastPathComponent();
            enableTunerActions(t);
            getHDHRAction(Name.EDIT_PROGRAM).setProgram(p);
            getHDHRAction(Name.EDIT_PROGRAM).setTuner(t);
            getHDHRAction(Name.EDIT_PROGRAM).setEnabled(true);
            getHDHRAction(Name.STREAM_INFO).setProgram(p);
            getHDHRAction(Name.STREAM_INFO).setTuner(t);
            getHDHRAction(Name.STREAM_INFO).setEnabled(true);
        } else if (value instanceof Tuner) {
            Tuner t = (Tuner) value;
            enableTunerActions(t);
            getHDHRAction(Name.SCAN).setTuner(t);
            getHDHRAction(Name.SCAN).setEnabled(true);
            getHDHRAction(Name.CLEAR_LINEUP).setTuner(t);
            getHDHRAction(Name.CLEAR_LINEUP).setEnabled(true);
        }
    }

    private void disableTogglableActions() {
        for (Name n : Arrays.asList(
                    Name.SCAN, Name.UNLOCK_TUNER,
                    Name.UNSET_TARGET, Name.UNSET_CHANNEL,
                    Name.COPY_LINEUP,
                    Name.MATCH_LINEUP, Name.EDIT_LINEUP, Name.CLEAR_LINEUP,
                    Name.EDIT_PROGRAM, Name.STREAM_INFO)) {
            getAction(n).setEnabled(false);
        }
    }

    private void enableTunerActions(Tuner t) {
        for (Name n : Arrays.asList(
                Name.UNLOCK_TUNER, Name.UNSET_TARGET, Name.UNSET_CHANNEL,
                Name.COPY_LINEUP, Name.EDIT_LINEUP, Name.MATCH_LINEUP)) {
            
            getHDHRAction(n).setTuner(t);
            getHDHRAction(n).setEnabled(true);
        }
    }
}
