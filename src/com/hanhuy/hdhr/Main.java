package com.hanhuy.hdhr;

import com.hanhuy.common.ui.ConsoleViewer;
import com.hanhuy.common.ui.ResourceBundleForm;
import com.hanhuy.common.ui.Util;
import com.hanhuy.common.ui.DimensionEditor;
import com.hanhuy.common.ui.FontEditor;
import com.hanhuy.hdhr.config.Lineup;
import com.hanhuy.hdhr.config.Control;
import com.hanhuy.hdhr.config.ChannelScan;
import com.hanhuy.hdhr.config.ChannelMap.Channel.Program;
import com.hanhuy.hdhr.config.TunerException;
import com.hanhuy.hdhr.treemodel.DeviceTreeModel;
import com.hanhuy.hdhr.treemodel.DeviceTreeCellRenderer;
import com.hanhuy.hdhr.treemodel.Tuner;
import com.hanhuy.hdhr.treemodel.Device;

import java.beans.PropertyEditorManager;
import java.io.IOException;
import java.awt.Point;
import java.awt.Font;
import java.awt.CardLayout;
import java.awt.EventQueue;
import java.awt.Dimension;
import java.awt.BorderLayout;
import java.awt.event.KeyEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowAdapter;
import java.awt.Color;
import java.awt.Window;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.HashMap;

import javax.swing.JList;
import javax.swing.ToolTipManager;
import javax.swing.JDialog;
import javax.swing.JProgressBar;
import javax.swing.JToolBar;
import javax.swing.JSlider;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JRadioButton;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.UIManager;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.JTextField;
import javax.swing.ImageIcon;
import javax.swing.JPopupMenu;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.swing.plaf.FontUIResource;

public class Main extends ResourceBundleForm implements Runnable {
    public static JFrame frame;
    public static CardLayout cards;
    public static JPanel cardPane;

    static DeviceTreeModel model = new DeviceTreeModel();

    static JProgressBar ssBar, seqBar, snqBar;

    public static void main(String[] args) throws Exception {
        if (System.getProperty("com.hanhuy.hdhr.noconsole") == null)
            setConsole();
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        UIManager.put("ProgressBar.selectionForeground", Color.black);
        PropertyEditorManager.registerEditor(Dimension.class,
                DimensionEditor.class);
        PropertyEditorManager.registerEditor(Font.class,
                FontEditor.class);

        EventQueue.invokeLater(new Main());
    }

    private static void setConsole() {
        System.setOut(ConsoleViewer.OUT);
        System.setErr(ConsoleViewer.OUT);

        Thread.setDefaultUncaughtExceptionHandler(
                new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                if (Main.frame == null) {
                    ConsoleViewer c = new ConsoleViewer(null);
                    Window w = c.getWindow();
                    // javawebstart workaround, processes keep running otherwise
                    w.addWindowListener(new WindowAdapter() {
                        @Override
                        public void windowClosed(WindowEvent e) {
                            System.exit(1);
                        }
                    });
                }
                System.err.println("Uncaught exception in thread " + t);
                e.printStackTrace();
            }
        });
    }

    public void run() {
        JFrame jframe = new JFrame(getString("title"));
        jframe.setIconImage(((ImageIcon)getIcon("icon")).getImage());
        JMenuBar menubar = new JMenuBar();
        jframe.setJMenuBar(menubar);
        JMenu menu;

        menu = new JMenu(getString("fileMenuTitle"));
        menu.setMnemonic(getChar("fileMenuMnemonic"));
        menu.add(new RunnableAction("Show Console", KeyEvent.VK_C, "control C",
                new Runnable() {
            public void run() {
                new ConsoleViewer(Main.frame);
            }
        }));
        menu.add(new RunnableAction("Exit", KeyEvent.VK_X, "control X",
                new Runnable() {
            public void run() {
                exit();
            }
        }));
        menubar.add(menu);

        menu = new JMenu(getString("viewMenuTitle"));
        menu.setMnemonic(getChar("viewMenuMnemonic"));

        menu.add(new RunnableAction("Increase font size", KeyEvent.VK_I,
                "control I", new Runnable() {
            public void run() {
                changeFontSize(true);
            }
        }));
        menu.add(new RunnableAction("Reset font size", KeyEvent.VK_E,
                "control E", new Runnable() {
            public void run() {
                resetFontSize();
            }
        }));
        menu.add(new RunnableAction("Decrease font size", KeyEvent.VK_D,
                "control D", new Runnable() {
            public void run() {
                changeFontSize(false);
            }
        }));
        menubar.add(menu);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        TreePopupListener l = new TreePopupListener();
        final JTree tree = new JTree(model);
        tree.addMouseListener(l);
        tree.addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                Object item = e.getPath().getLastPathComponent();
                if (item == DeviceTreeModel.ROOT_NODE)
                    cards.show(cardPane, "blank");
            }
        });
        tree.addTreeSelectionListener(l);
        tree.addTreeSelectionListener(TunerInfoCard.INSTANCE);
        tree.addTreeSelectionListener(DeviceInfoCard.INSTANCE);
        tree.addTreeSelectionListener(ProgramCard.INSTANCE);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new DeviceTreeCellRenderer());
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                int count = model.getChildCount(DeviceTreeModel.ROOT_NODE);
                for (int i = 0; i < count; i++) {
                    Object node = model.getChild(DeviceTreeModel.ROOT_NODE, i);
                    tree.expandPath(new TreePath(
                            new Object[] { DeviceTreeModel.ROOT_NODE, node }));
                }
            }
        });

        JScrollPane pane = new JScrollPane(tree,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        pane.setPreferredSize(new Dimension(320, 540));
        split.setTopComponent(pane);

        cards    = new CardLayout();
        cardPane = new JPanel();
        cardPane.setLayout(cards);
        cardPane.add(new JPanel(), "blank");
        cardPane.add(DeviceInfoCard.INSTANCE.card, DeviceInfoCard.CARD_NAME);
        cardPane.add(TunerInfoCard.INSTANCE.card,  TunerInfoCard.CARD_NAME);
        cardPane.add(ProgramCard.INSTANCE.card,    ProgramCard.CARD_NAME);
        cards.show(cardPane, "blank");
        cardPane.setPreferredSize(new Dimension(960, 540));
        split.setBottomComponent(cardPane);

        jframe.add(split);

        jframe.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        jframe.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }
        });

        JPanel topPane = new JPanel();
        topPane.setLayout(new BoxLayout(topPane, BoxLayout.X_AXIS));

        /*
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(new RunnableAction("Test", new Runnable() {
            int i = 0;
            public void run() {
                System.out.println("OOH! " + i++);
            }
        }));
        topPane.add(bar);
        */

        topPane.add(Box.createHorizontalGlue());

        ssBar = new JProgressBar();
        ssBar.setStringPainted(true);
        ssBar.setString("SS:");
        ssBar.setMaximumSize(new Dimension(120, 20));
        topPane.add(ssBar);
        ssBar.setVisible(false);
        topPane.add(Box.createHorizontalStrut(5));

        snqBar = new JProgressBar();
        snqBar.setStringPainted(true);
        snqBar.setString("SNQ:");
        snqBar.setMaximumSize(new Dimension(120, 20));
        topPane.add(snqBar);
        snqBar.setVisible(false);
        topPane.add(Box.createHorizontalStrut(5));

        seqBar = new JProgressBar();
        seqBar.setStringPainted(true);
        seqBar.setString("SEQ:");
        seqBar.setMaximumSize(new Dimension(120, 20));
        topPane.add(seqBar);
        seqBar.setVisible(false);
        topPane.add(Box.createHorizontalStrut(5));
        final JSlider slider = new JSlider(new DefaultBoundedRangeModel(
                ProgramCard.SLIDER_VOLUME_0DB, 0, 0,
                ProgramCard.SLIDER_VOLUME_MAX) {
            @Override
            public boolean getValueIsAdjusting() {
                return false;
            }
        }) {
            {
                MouseListener[] listeners = getMouseListeners();
                for (MouseListener l : listeners)
                    removeMouseListener(l);
                final BasicSliderUI ui = (BasicSliderUI) getUI();
                BasicSliderUI.TrackListener tl =
                        ui.new TrackListener() {
                    @Override public void mouseClicked(MouseEvent e) {
                        Point p = e.getPoint();
                        final int value = ui.valueForXPosition(p.x);

                        setValue(value);
                    }
                    @Override public boolean shouldScroll(int dir) {
                        return false;
                    }
                };
                addMouseListener(tl);
            }
            @Override
            public String getToolTipText(MouseEvent e) {
                BasicSliderUI ui = (BasicSliderUI) getUI();
                return String.format("%.1fdB (%.1fdB)",
                        ProgramCard.getDB(getValue()),
                        ProgramCard.getDB(
                                ui.valueForXPosition(e.getPoint().x)));
            }
            @Override
            public Point getToolTipLocation(MouseEvent e) {
                Point p = e.getPoint();
                p.x -= 25;
                p.y -= 25;
                return p;
            }
        };
        ToolTipManager.sharedInstance().registerComponent(slider);
        ToolTipManager.sharedInstance().setInitialDelay(50);
        ToolTipManager.sharedInstance().setReshowDelay(0);
        slider.addChangeListener(ProgramCard.INSTANCE);
        Dimension d = slider.getPreferredSize();
        d.width = 240;
        slider.setMaximumSize(d);
        topPane.add(slider);
        topPane.add(Box.createHorizontalStrut(5));
        jframe.add(topPane, BorderLayout.NORTH);

        jframe.pack();
        Util.centerWindow(jframe);
        jframe.setVisible(true);
        frame = jframe;
    }

    private Map<Object,Object> defaults = new HashMap<Object,Object>();
    private void changeFontSize(boolean increase) {
        int increment = increase ? 1 : -1;
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        for (Object key : Collections.list(keys)) {
            Object value = UIManager.get(key);
            if (value instanceof FontUIResource) {
                if (!defaults.containsKey(key))
                    defaults.put(key, value);
                FontUIResource f = (FontUIResource) value;
                FontUIResource nf = new FontUIResource(f.getName(),
                    f.getStyle(), f.getSize() + increment);
                UIManager.put(key, nf);
            }
        }
        SwingUtilities.updateComponentTreeUI(Main.frame);
    }
    private void resetFontSize() {
        for (Object key : defaults.keySet())
            UIManager.put(key, defaults.get(key));
        SwingUtilities.updateComponentTreeUI(Main.frame);
    }

    void exit() {
        frame.setVisible(false);
        frame.dispose();

        ProgramCard.INSTANCE.stopPlayer(true);
        System.exit(0);
    }

}
class TreePopupListener implements MouseListener, TreeSelectionListener {
    private JPopupMenu tunerMenu;
    private JPopupMenu programMenu;
    private JPopupMenu rootMenu;
    private Object value;
    private TreePath path;
    private Lineup l;

    TreePopupListener() {
        tunerMenu = new JPopupMenu();
        tunerMenu.add(new RunnableAction("Scan channels", new Runnable() {
            public void run() {
                Tuner t = (Tuner) value;
                Control c = new Control();
                try {
                    c.connect(t.device.id);
                    String cmd = String.format("/tuner%d/target",
                            t.tuner.ordinal());
                    String target = c.get(cmd);
                    if (!"none".equals(target)) {
                        int r = JOptionPane.showConfirmDialog(
                                Main.frame, "<html><p>" +
                                "Another application is currently using but " +
                                "has not locked this tuner.  Continuing may " +
                                "cause unexpected results or behavior.",
                                "Tuner in use", JOptionPane.OK_CANCEL_OPTION);
                        if (r != JOptionPane.OK_OPTION)
                            return;
                    }
                }
                catch (TunerException e) {
                    JOptionPane.showMessageDialog(Main.frame,
                            "Unable to verify tuner status");
                    e.printStackTrace();
                }
                finally {
                    c.close();
                }
                List<Program> p = Main.model.programMap.get(t);
                if (p != null && p.size() > 0) {
                    int r = JOptionPane.showConfirmDialog(
                            Main.frame, "<html><p>" +
                            "Performing a channel scan will delete the " +
                            "previously configured channels, continue?",
                            "Re-scan channels", JOptionPane.YES_NO_OPTION);
                    if (r != JOptionPane.YES_OPTION)
                        return;
                }
                ProgressDialog d = new ProgressDialog(
                        Main.frame, "Scanning");
                try {
                    d.showProgress(new ScanningRunnable(t));
                }
                catch (IndexOutOfBoundsException e) {
/* seem to hit some sort of bug here:
Exception in thread "AWT-EventQueue-0" java.lang.IndexOutOfBoundsException: Index: 1, Size: 1
        at java.util.ArrayList.RangeCheck(Unknown Source)
        at java.util.ArrayList.get(Unknown Source)
        at java.awt.Container.createHierarchyEvents(Unknown Source)
        at java.awt.Container.createHierarchyEvents(Unknown Source)
        at java.awt.Container.createHierarchyEvents(Unknown Source)
        at java.awt.Container.createHierarchyEvents(Unknown Source)
        at java.awt.Dialog.conditionalShow(Unknown Source)
        at java.awt.Dialog.show(Unknown Source)
        at java.awt.Component.show(Unknown Source)
        at java.awt.Component.setVisible(Unknown Source)
        at java.awt.Window.setVisible(Unknown Source)
        at java.awt.Dialog.setVisible(Unknown Source)                    
        at com.hanhuy.hdhr.ProgressDialog.showProgress(ProgressDialog.java:61)
*/
                    System.out.println(
                            "Strange IndexOutOfBoundsException occurred");
                    e.printStackTrace();
                    return;
                }
                JOptionPane.showMessageDialog(Main.frame,
                        "Found " + Main.model.programMap.get(t).size() +
                        " programs");
            }
        }));
        tunerMenu.add(new RunnableAction("Unlock tuner", new Runnable() {
            public void run() {
                Control c = new Control();
                Tuner t = (Tuner) value;
                try {
                    c.connect(t.device.id);
                    String lock = c.get(
                            "/tuner" + t.tuner.ordinal() + "/lockkey");
                    if ("none".equals(lock)) {
                        JOptionPane.showMessageDialog(Main.frame,
                            "This tuner is not locked");
                        return;
                    }
                    int r = JOptionPane.showConfirmDialog(
                            Main.frame, "<html><p>" +
                            "Force unlock this tuner?  Doing so might " +
                            " interrupt another program that may be using " +
                            " this tuner.",
                            "Unlock Tuner", JOptionPane.YES_NO_OPTION);
                    if (r != JOptionPane.YES_OPTION)
                        return;
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
        tunerMenu.add(new RunnableAction("Unset target", new Runnable() {
            public void run() {
                Control c = new Control();
                Tuner t = (Tuner) value;
                try {
                    c.connect(t.device.id);
                    String target = String.format("/tuner%d/target",
                            t.tuner.ordinal());
                    String lock = c.get(target);
                    if ("none".equals(lock)) {
                        JOptionPane.showMessageDialog(Main.frame,
                            "This tuner is not streaming");
                        return;
                    }
                    int r = JOptionPane.showConfirmDialog(
                            Main.frame, "<html><p>" +
                            "Set the target for this tuner to none?  Doing " +
                            "so <b>will interrupt</b> any other program " +
                            "that is using this tuner.",
                            "Unset target", JOptionPane.YES_NO_OPTION);
                    if (r != JOptionPane.YES_OPTION)
                        return;
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
        tunerMenu.add(new RunnableAction("Copy scan results...",
                new Runnable() {
            public void run() {
                Tuner t = (Tuner) value;
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
                            "No scan results are available to copy");
                    return;
                }
                JList list = new JList(tuners.toArray());
                list.setSelectedIndex(0);
                list.setLayoutOrientation(JList.VERTICAL);

                JScrollPane pane = new JScrollPane(list,
                        JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                Object[] message = new Object[2];
                message[0] = "Select the tuner with the " +
                        "scan results you wish to copy";
                message[1] = pane;
                int r = JOptionPane.showConfirmDialog(Main.frame, message,
                        "Copy scan results", JOptionPane.OK_CANCEL_OPTION);
                if (r != JOptionPane.OK_OPTION)
                    return;
                Tuner value = (Tuner) list.getSelectedValue();
                if (value == null) {
                    JOptionPane.showMessageDialog(Main.frame,
                            "No results selected");
                    return;
                }
                Main.model.programMap.put(t, Main.model.programMap.get(value));
                Main.model.fireTreeStructureChanged(new Object[] {
                        DeviceTreeModel.ROOT_NODE, t.device, t });
            }
        }));
        tunerMenu.add(new RunnableAction("Match channel lineup...",
                new Runnable() {
            public void run() {
                final Tuner t = (Tuner) value;
                final List<Program> programs = Main.model.programMap.get(t);
                if (programs == null || programs.size() == 0) {
                    JOptionPane.showMessageDialog(
                            Main.frame, "No programs to match, scan first");
                    return;
                }
                int deviceId = t.device.id;
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
                String[] databases;
                if (l == null) {
                    l = new Lineup(location);
                    final String[][] p = new String[1][];
                    ProgressDialog d = new ProgressDialog(
                            Main.frame, "Fetching lineup");
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
                    String label = l.getProgramCount(databases[i]) +
                            " programs: " + l.getDisplayName(databases[i]);
                    JRadioButton b = new JRadioButton(label);
                    g.add(b);
                    buttons[i] = b;
                }
                JOptionPane.showMessageDialog(Main.frame, buttons,
                        "Select a lineup to apply",
                        JOptionPane.QUESTION_MESSAGE);
                int selectedDB = -1;
                for (int i = 0; i < buttons.length && selectedDB == -1; i++) {
                    if (buttons[i].isSelected()) {
                        selectedDB = i;
                        break;
                    }
                }
                if (selectedDB != -1) {
                    final String selectedID = databases[selectedDB];
                    ProgressDialog d = new ProgressDialog(
                            Main.frame, "Matching lineup");
                    final int[] count = new int[1];
                    d.showProgress(new Runnable() {
                        public void run() {
                            count[0] = 0;
                            for (Program program : programs) {
                                if (l.applyProgramSettings(
                                        selectedID, program)) {
                                    count[0]++;
                                } else {
                                    program.setName("UNKNOWN");
                                    program.virtualMajor = 0;
                                    program.virtualMinor = 0;
                                }
                            }
                        }
                    });
                    JOptionPane.showMessageDialog(Main.frame,
                            "Matched " + count[0] + " programs");
                    Main.model.fireTreeStructureChanged(new Object[] {
                            DeviceTreeModel.ROOT_NODE, t.device, t });
                }
            }
        }));

        rootMenu = new JPopupMenu();
        rootMenu.add(new RunnableAction("Re-discover devices",
                new Runnable() {
            public void run() {
                Main.model.rediscover();
            }
        }));

        programMenu = new JPopupMenu();
        programMenu.add(new RunnableAction("Edit...", new Runnable() {
            public void run() {
                Program p = (Program) value;

                Object[] message = new Object[2];

                JTextField nameField = new JTextField(p.getName(), 32);
                JPanel panel = new JPanel();
                panel.add(new JLabel("Name"));
                panel.add(nameField);
                message[0] = panel;

                JTextField numberField = new JTextField(p.getGuideNumber(), 8);
                panel = new JPanel();
                panel.add(new JLabel("Guide Number"));
                panel.add(numberField);
                message[1] = panel;

                JOptionPane pane = new JOptionPane(message,
                        JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION,
                        null, null, null);
                JDialog dialog = pane.createDialog(Main.frame,
                        "Edit Program Details");
                pane.selectInitialValue();
                dialog.setVisible(true);
                dialog.dispose();

                Integer result = (Integer) pane.getValue();
                String name   = nameField.getText();
                String number = numberField.getText();
                if (result != null && result == JOptionPane.OK_OPTION) {
                    if (name == null || name.trim().equals("")) {
                        JOptionPane.showMessageDialog(Main.frame,
                                "Name can't be blank");
                        return;
                    }
                    if (!isValidNumber(number)) {
                        JOptionPane.showMessageDialog(Main.frame,
                                "Invalid guide number, format is #.#");
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

                Main.model.fireTreeNodesChanged(path);
            }

            private boolean isValidNumber(String number) {
                return number.matches("\\d+(\\.\\d+)?");
            }
        }));
    }
    public void valueChanged(TreeSelectionEvent e) {
        path = e.getPath();
        value = e.getPath().getLastPathComponent();
    }

    public void mousePressed(MouseEvent e)  { maybeShowPopup(e); }
    public void mouseReleased(MouseEvent e) { maybeShowPopup(e); }

    public void mouseClicked(MouseEvent e) { }
    public void mouseEntered(MouseEvent e) { }
    public void mouseExited(MouseEvent e) { }
    private void maybeShowPopup(MouseEvent e) {
        if (e.isPopupTrigger()) {
            JPopupMenu popup = null;

            if (value == DeviceTreeModel.ROOT_NODE)
                popup = rootMenu;
            if (value instanceof Tuner)
                popup = tunerMenu;
            if (value instanceof Program)
                popup = programMenu;
            if (popup != null)
                popup.show(e.getComponent(), e.getX(), e.getY());
        }
    }
}
class ScanningRunnable implements ProgressAwareRunnable {
    ScanningRunnable(Tuner t) {
        this.t = t;
    }
    JProgressBar bar;
    Tuner t;
    public void setJProgressBar(JProgressBar b) {
        bar = b;
    }
    public void run() {
        Control device = new Control();
        try {
            bar.setStringPainted(true);
            bar.setString("Connecting to device: " +
                    Integer.toHexString(t.device.id));
            device.connect(t.device.id);
            bar.setString("Locking tuner: " + t.tuner);
            device.lock(t.tuner);
            bar.setString("Scanning");
            final List<Program> programs = new ArrayList<Program>();
            ChannelScan.scan(device, new ChannelScan.ScanListener() {
                int progress = 0;
                boolean configured = false;
                int found = 0;
                public void scanningChannel(ChannelScan.ScanEvent e) {
                    if (!configured) {
                        bar.setIndeterminate(false);
                        bar.setMinimum(0);
                        bar.setMaximum(e.map.getChannels().size() * 2);
                        bar.setValue(0);
                        configured = true;
                    }
                    bar.setString(
                            "Scanning: " + e.channel + " Found: " + found);
                    bar.setValue(bar.getValue() + 1);
                    System.out.println(String.format(
                            "Scanning %d/%d: %s",
                            e.index, e.map.getChannels().size(), e.channel));
                }
                public void foundChannel(ChannelScan.ScanEvent e) {
                    bar.setValue(bar.getValue() + 1);
                    System.out.println("Locked: " + e.getStatus());
                }
                public void skippedChannel(ChannelScan.ScanEvent e) {
                    bar.setValue(bar.getValue() + 1);
                    System.out.println("Skipped: " +
                            (e.getStatus() != null ?
                                    e.getStatus() : "impossible channel"));
                }
                public void programsFound(ChannelScan.ScanEvent e) {
                    found += e.channel.getPrograms().size();
                    programs.addAll(e.channel.getPrograms());

                    System.out.println("BEGIN STREAMINFO:\n" + e.streaminfo +
                            ":END STREAMINFO");
                    for (Program p : e.channel.getPrograms())
                        System.out.println(p);
                }
                public void programsNotFound(ChannelScan.ScanEvent e) {
                    System.out.println("BEGIN STREAMINFO:" + e.streaminfo +
                            ":END STREAMINFO");
                    System.out.println("No available programs found");
                }
            });
            device.unlock();

            Main.model.programMap.put(t, programs);
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    Main.model.fireTreeStructureChanged(new Object[] {
                            DeviceTreeModel.ROOT_NODE, t.device, t });
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
        finally {
            device.close();
        }
    }
}
