package com.hanhuy.hdhr;

import com.hanhuy.common.ui.ResourceBundleForm;
import com.hanhuy.common.ui.Util;
import com.hanhuy.common.ui.DimensionEditor;
import com.hanhuy.hdhr.config.Lineup;
import com.hanhuy.hdhr.config.Control;
import com.hanhuy.hdhr.config.ChannelScan;
import com.hanhuy.hdhr.config.ChannelMap.Channel.Program;
import com.hanhuy.hdhr.config.TunerException;
import com.hanhuy.hdhr.treemodel.DeviceTreeModel;
import com.hanhuy.hdhr.treemodel.DeviceTreeCellRenderer;
import com.hanhuy.hdhr.treemodel.Tuner;

import java.beans.PropertyEditorManager;
import java.io.IOException;
import java.awt.CardLayout;
import java.awt.EventQueue;
import java.awt.Dimension;
import java.awt.BorderLayout;
import java.awt.event.KeyEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseEvent;
import java.awt.Color;
import java.util.List;
import java.util.ArrayList;

import javax.swing.JRadioButton;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JMenuBar;
import javax.swing.UIManager;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.ImageIcon;
import javax.swing.JPopupMenu;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;

public class Main extends ResourceBundleForm implements Runnable {
    public static JFrame frame;
    public static CardLayout cards;
    public static JPanel cardPane;

    static DeviceTreeModel model = new DeviceTreeModel();

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        UIManager.put("ProgressBar.selectionForeground", Color.black);
        PropertyEditorManager.registerEditor(Dimension.class,
                DimensionEditor.class);
        EventQueue.invokeLater(new Main());
    }

    public void run() {
        frame = new JFrame(getString("title"));
        frame.setIconImage(((ImageIcon)getIcon("icon")).getImage());
        JMenuBar menubar = new JMenuBar();
        frame.setJMenuBar(menubar);
        JMenu menu;

        menu = new JMenu(getString("fileMenuTitle"));
        menu.setMnemonic(getChar("fileMenuMnemonic"));
        menu.add(new RunnableAction("Exit", KeyEvent.VK_X, "control X",
                new Runnable() {
            public void run() {
                frame.setVisible(false);
                frame.dispose();
                System.exit(0);
            }
        }));
        menubar.add(menu);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        TreePopupListener l = new TreePopupListener();
        JTree tree = new JTree(model);
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

        JScrollPane pane = new JScrollPane(tree,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        pane.setPreferredSize(new Dimension(240, 540));
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

        frame.add(split);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        Util.centerWindow(frame);
        frame.setVisible(true);
    }

}
class TreePopupListener implements MouseListener, TreeSelectionListener {
    private JPopupMenu tunerMenu;
    private JPopupMenu rootMenu;
    private Object value;
    private Lineup l;

    TreePopupListener() {
        tunerMenu = new JPopupMenu();
        tunerMenu.add(new RunnableAction("Scan channels", new Runnable() {
            public void run() {
                Tuner t = (Tuner) value;
                List<Program> p = Main.model.programMap.get(t);
                if (p != null && p.size() > 0) {
                    int r = JOptionPane.showConfirmDialog(
                            Main.frame, "<html><p>" +
                            "Performing a channel scan will reset the " +
                            "previously configured channels, continue?",
                            "Re-scan channels", JOptionPane.YES_NO_OPTION);
                    if (r != JOptionPane.YES_OPTION)
                        return;
                }
                ProgressDialog d = new ProgressDialog(
                        Main.frame, "Scanning");
                d.showProgress(new ScanningRunnable(t));
                JOptionPane.showMessageDialog(Main.frame,
                        "Found " + t.programs.size() + " programs");
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
        rootMenu.add(new JMenuItem("Re-discover devices"));
    }
    public void valueChanged(TreeSelectionEvent e) {
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
                }
                public void foundChannel(ChannelScan.ScanEvent e) {
                    bar.setValue(bar.getValue() + 1);
                }
                public void skippedChannel(ChannelScan.ScanEvent e) {
                    bar.setValue(bar.getValue() + 1);
                }
                public void programsFound(ChannelScan.ScanEvent e) {
                    found += e.channel.getPrograms().size();
                    programs.addAll(e.channel.getPrograms());
                }
                public void programsNotFound(ChannelScan.ScanEvent e) {
                }
            });
            device.unlock();

            t.programs.clear();
            t.programs.addAll(programs);
            Main.model.programMap.put(t, programs);
            Main.model.fireTreeStructureChanged(new Object[] {
                    DeviceTreeModel.ROOT_NODE, t.device, t });
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
