package com.hanhuy.hdhr;

import com.hanhuy.common.ui.ConsoleViewer;
import com.hanhuy.common.ui.ResourceBundleForm;
import com.hanhuy.common.ui.Util;
import com.hanhuy.common.ui.DimensionEditor;
import com.hanhuy.common.ui.FontEditor;
import com.hanhuy.hdhr.config.LineupWeb;
import com.hanhuy.hdhr.config.LineupServer;
import com.hanhuy.hdhr.config.Control;
import com.hanhuy.hdhr.config.ChannelScan;
import com.hanhuy.hdhr.config.ChannelMap.Channel.Program;
import com.hanhuy.hdhr.config.TunerException;
import com.hanhuy.hdhr.config.TunerLineupException;
import com.hanhuy.hdhr.treemodel.DeviceTreeModel;
import com.hanhuy.hdhr.treemodel.DeviceTreeCellRenderer;
import com.hanhuy.hdhr.treemodel.Tuner;
import com.hanhuy.hdhr.treemodel.Device;
import com.hanhuy.hdhr.av.VideoPlayerFactory;
import com.hanhuy.hdhr.av.VideoPlayer;
import com.hanhuy.hdhr.ui.ProgressAwareRunnable;
import com.hanhuy.hdhr.ui.ProgressBar;
import com.hanhuy.hdhr.ui.ProgressDialog;
import com.hanhuy.hdhr.ui.RunnableAction;

import java.beans.PropertyEditorManager;
import java.io.IOException;
import java.awt.Component;
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
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.Color;
import java.awt.Window;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.UUID;
import java.util.Arrays;

import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.ToolTipManager;
import javax.swing.JDialog;
import javax.swing.JToolBar;
import javax.swing.JSlider;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
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
import javax.swing.JToggleButton;
import javax.swing.ImageIcon;
import javax.swing.JPopupMenu;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.swing.plaf.FontUIResource;

public class Main extends ResourceBundleForm implements Runnable {
    public static JFrame frame;
    public static CardLayout cards;
    public static JPanel cardPane;

    private JMenu deinterlacerMenu;

    static JTree tree;

    static DeviceTreeModel model = new DeviceTreeModel();

    private JSplitPane split;

    public static void main(String[] args) throws Exception {
        // prevent performance impact of javaws security manager
        System.setSecurityManager(new PermissiveSecurityManager());
        if (System.getProperty("com.hanhuy.hdhr.console") != null)
            setConsole();
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        UIManager.put("ProgressBar.selectionForeground", Color.black);
        PropertyEditorManager.registerEditor(Dimension.class,
                DimensionEditor.class);
        PropertyEditorManager.registerEditor(Font.class,
                FontEditor.class);

        Preferences p = Preferences.getInstance();
        if (p.userUUID == null) {
            p.userUUID = UUID.randomUUID().toString();
        }
        EventQueue.invokeLater(new Main());
    }

    Main() {
        Actions.init(this);
        TimeShiftActions.init(this);
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

    private void initMenu(JFrame container) {
        JMenuBar menubar = new JMenuBar();
        container.setJMenuBar(menubar);
        // necessary due to canvas
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
        JMenu menu;

        menu = new JMenu(getString("fileMenuTitle"));
        menu.setMnemonic(getChar("fileMenuMnemonic"));
        menu.add(Actions.getAction(Actions.Name.DISCOVER));
        menu.add(Actions.getAction(Actions.Name.EXIT));
        menubar.add(menu);

        menu = new JMenu(getString("viewMenuTitle"));
        menu.setMnemonic(getChar("viewMenuMnemonic"));

        menu.add(Actions.getAction(Actions.Name.INCREASE_FONT_SIZE));
        menu.add(Actions.getAction(Actions.Name.RESET_FONT_SIZE));
        menu.add(Actions.getAction(Actions.Name.DECREASE_FONT_SIZE));
        menu.addSeparator();
        menu.add(Actions.getAction(Actions.Name.SHOW_CONSOLE));
        final JCheckBoxMenuItem debugItem = new JCheckBoxMenuItem();
        boolean pDebug = Preferences.getInstance().programDebug;
        if (pDebug) {
            debugItem.setState(pDebug);
            ProgramCard.INSTANCE.setDebug(pDebug);
        }
        RunnableAction debugAction = new RunnableAction(
                getString("debugName"), getInt("debugMnemonic"),
                new Runnable() {
            public void run() {
                boolean debug = debugItem.isSelected();
                Preferences.getInstance().programDebug = debug;
                ProgramCard.INSTANCE.setDebug(debug);
            }
        });
        debugItem.setAction(debugAction);
        menu.add(debugItem);

        menubar.add(menu);

        menu = new JMenu(getString("tunerMenuTitle"));
        menu.setMnemonic(getChar("tunerMenuMnemonic"));
        menu.add(Actions.getAction(Actions.Name.UNLOCK_TUNER));
        menu.add(Actions.getAction(Actions.Name.UNSET_TARGET));
        menu.add(Actions.getAction(Actions.Name.UNSET_CHANNEL));
        menu.addSeparator();
        menu.add(Actions.getAction(Actions.Name.CLEAR_LINEUP));
        menu.add(Actions.getAction(Actions.Name.SCAN));
        menu.add(Actions.getAction(Actions.Name.MATCH_LINEUP));
        menu.add(Actions.getAction(Actions.Name.EDIT_LINEUP));
        menu.add(Actions.getAction(Actions.Name.COPY_LINEUP));
        menubar.add(menu);

        menu = new JMenu(getString("programMenuTitle"));
        menu.setMnemonic(getChar("programMenuMnemonic"));
        menu.add(Actions.getAction(Actions.Name.STREAM_INFO));
        menu.add(Actions.getAction(Actions.Name.EDIT_PROGRAM));
        menu.add(Actions.getAction(Actions.Name.JUMP_TO_LAST_PROGRAM));

        menubar.add(menu);

        menu = new JMenu(getString("backendsMenuTitle"));
        menu.setMnemonic(getChar("backendsMenuMnemonic"));

        String[] backends = VideoPlayerFactory.getVideoPlayerNames();
        ButtonGroup g = new ButtonGroup();
        ActionListener l = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String c = e.getActionCommand();
                Preferences.getInstance().videoBackend = c;
                VideoPlayer player = VideoPlayerFactory.getVideoPlayer(c);
                ProgramCard.INSTANCE.setVideoPlayer(player);
                setDeinterlacers(player.getDeinterlacers());
            }
        };
        String defaultBackend = Preferences.getInstance().videoBackend;
        for (int i = 0, j = backends.length; i < j; i++) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(
                    backends[i], defaultBackend == null ?
                            i == 0 : backends[i].equals(defaultBackend));
            g.add(item);
            item.setActionCommand(backends[i]);
            item.addActionListener(l);
            menu.add(item);
        }

        menubar.add(menu);

        menu = new JMenu(getString("deinterlacersMenuTitle"));
        menu.setMnemonic(getChar("deinterlacersMenuMnemonic"));
        deinterlacerMenu = menu;
        setDeinterlacers(
                ProgramCard.INSTANCE.getVideoPlayer().getDeinterlacers());
        menubar.add(menu);

        menu = new JMenu(getString("helpMenuTitle"));
        menu.setMnemonic(getChar("helpMenuMnemonic"));

        menu.add(Actions.getAction(Actions.Name.ABOUT));
        menubar.add(menu);
    }

    private void setDeinterlacers(String[] deinterlacers) {
        deinterlacerMenu.removeAll();
        ButtonGroup g = new ButtonGroup();
        ActionListener l = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String c = e.getActionCommand();
                ProgramCard.INSTANCE.getVideoPlayer().setDeinterlacer(c);
                Preferences.getInstance().deinterlacer = c;
            }
        };
        String deint = Preferences.getInstance().deinterlacer;
        boolean hasDefault = false;
        for (int i = 0; i < deinterlacers.length; i++) {
            boolean isDefault = deinterlacers[i].equals(deint);
            hasDefault |= isDefault;
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(
                    deinterlacers[i], isDefault);
            if (isDefault)
                ProgramCard.INSTANCE.getVideoPlayer().setDeinterlacer(
                        deinterlacers[i]);
            g.add(item);
            item.setActionCommand(deinterlacers[i]);
            item.addActionListener(l);
            deinterlacerMenu.add(item);
        }
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(
                "none", deinterlacers.length == 0 || !hasDefault);
        g.add(item);
        item.setActionCommand(null);
        item.addActionListener(l);
        deinterlacerMenu.add(item);
    }

    public void run() {
        if (Preferences.getInstance().fontDelta != 0)
            changeFontSize(Preferences.getInstance().fontDelta);

        JFrame jframe = new JFrame(getString("title"));
        jframe.setIconImage(((ImageIcon)getIcon("icon")).getImage());
        initMenu(jframe);

        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        TreePopupListener l = new TreePopupListener();
        tree = new JTree(model);
        tree.setExpandsSelectedPaths(true);
        tree.getSelectionModel().setSelectionMode(
                TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addMouseListener(l);
        tree.addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                Object item = e.getPath().getLastPathComponent();
                if (item == DeviceTreeModel.ROOT_NODE)
                    cards.show(cardPane, "blank");
            }
        });
        tree.addTreeSelectionListener(Actions.getInstance());
        tree.addTreeSelectionListener(TimeShiftActions.getInstance());
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
        cardPane.setMinimumSize(new Dimension(320, 240));
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

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(TimeShiftActions.getAction(TimeShiftActions.Name.PAUSE_RESUME));
        bar.add(TimeShiftActions.getAction(TimeShiftActions.Name.SHIFT_START));
        bar.add(TimeShiftActions.getAction(TimeShiftActions.Name.REWIND_LONG));
        bar.add(TimeShiftActions.getAction(TimeShiftActions.Name.REWIND_SHORT));
        bar.add(TimeShiftActions.getAction(
                TimeShiftActions.Name.FORWARD_SHORT));
        bar.add(TimeShiftActions.getAction(TimeShiftActions.Name.FORWARD_LONG));
        bar.add(TimeShiftActions.getAction(TimeShiftActions.Name.SHIFT_END));

        final JSlider timeslider = new JSlider(new DefaultBoundedRangeModel(
                10000, 0, 0, 10000)) {
            {
                MouseListener[] listeners = getMouseListeners();
                for (MouseListener l : listeners)
                    removeMouseListener(l);
                final BasicSliderUI ui = (BasicSliderUI) getUI();
                BasicSliderUI.TrackListener tl =
                        ui.new TrackListener() {
                    @Override public void mouseClicked(MouseEvent e) {
                        if (!isEnabled()) return;
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
                return TimeShiftActions.getInstance().getToolTipText(e);
            }
            @Override
            public Point getToolTipLocation(MouseEvent e) {
                Point p = e.getPoint();
                p.x -= 25;
                p.y -= 25;
                return p;
            }
        };
        bar.add(timeslider);
        bar.add(Box.createHorizontalStrut(5));
        JButton b = bar.add(
                TimeShiftActions.getAction(TimeShiftActions.Name.STOP));
        topPane.add(bar);
        ToolTipManager.sharedInstance().registerComponent(timeslider);
        TimeShiftActions.getInstance().setTimeSlider(timeslider);
        timeslider.addChangeListener(TimeShiftActions.getInstance());


        //topPane.add(Box.createHorizontalGlue());
        topPane.add(Box.createHorizontalStrut(20));

        boolean mute = Preferences.getInstance().muting;
        ProgramCard.INSTANCE.setMute(mute);
        final JToggleButton muteButton = new JToggleButton(
                getIcon(mute ? "speakerMuteIcon" : "speakerIcon"), mute);
        muteButton.setBorderPainted(false);
        muteButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                boolean muting = muteButton.isSelected();
                muteButton.setIcon(getIcon(muting ?
                        "speakerMuteIcon" : "speakerIcon"));
                ProgramCard.INSTANCE.setMute(muting);
            }
        });
        topPane.add(muteButton);
        int v = Preferences.getInstance().volume;
        final JSlider slider = new JSlider(new DefaultBoundedRangeModel(
                v == 0 ? ProgramCard.DEFAULT_VOLUME : v,
                0, 0, ProgramCard.MAX_VOLUME) {
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
                return String.format("%d (%d)",
                        getValue(), ui.valueForXPosition(e.getPoint().x));
            }
            @Override
            public Point getToolTipLocation(MouseEvent e) {
                Point p = e.getPoint();
                p.x -= 25;
                p.y -= 25;
                return p;
            }
        };
        ProgramCard.INSTANCE.setVolume(slider.getValue());
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

        Dimension size = Preferences.getInstance().windowSize;
        Point winLoc = Preferences.getInstance().windowLocation;
        int location = Preferences.getInstance().splitLocation;

        if (winLoc != null)
            jframe.setLocation(winLoc);
        if (size != null)
            jframe.setSize(size);
        if (location > 0)
            split.setDividerLocation(location);

        jframe.setVisible(true);
        frame = jframe;
    }

    private Map<Object,Object> defaults = new HashMap<Object,Object>();
    void changeFontSize(boolean increase) {
        int increment = increase ? 1 : -1;
        Preferences.getInstance().fontDelta += increment;
        changeFontSize(increment);
    }
    void changeFontSize(int delta) {
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        for (Object key : Collections.list(keys)) {
            Object value = UIManager.get(key);
            if (value instanceof FontUIResource) {
                if (!defaults.containsKey(key))
                    defaults.put(key, value);
                FontUIResource f = (FontUIResource) value;
                FontUIResource nf = new FontUIResource(f.getName(),
                    f.getStyle(), f.getSize() + delta);
                UIManager.put(key, nf);
            }
        }
        if (Main.frame != null)
            SwingUtilities.updateComponentTreeUI(Main.frame);
    }
    void resetFontSize() {
        Preferences.getInstance().fontDelta = 0;
        for (Object key : defaults.keySet())
            UIManager.put(key, defaults.get(key));
        if (Main.frame != null)
            SwingUtilities.updateComponentTreeUI(Main.frame);
    }

    void exit() {

        ProgramCard.INSTANCE.stopPlayer(true);
        Preferences.getInstance().windowLocation = frame.getLocation();
        Preferences.getInstance().windowSize = frame.getSize();
        Preferences.getInstance().splitLocation =
                split.getDividerLocation();

        frame.setVisible(false);
        frame.dispose();

        Preferences.save();
        System.exit(0);
    }

}
class TreePopupListener implements MouseListener, TreeSelectionListener {
    private JPopupMenu tunerMenu;
    private JPopupMenu programMenu;
    private JPopupMenu rootMenu;
    private Object value;
    private LineupWeb l;

    TreePopupListener() {
        tunerMenu = new JPopupMenu();
        tunerMenu.add(Actions.getAction(Actions.Name.UNLOCK_TUNER));
        tunerMenu.add(Actions.getAction(Actions.Name.UNSET_TARGET));
        tunerMenu.add(Actions.getAction(Actions.Name.UNSET_CHANNEL));
        tunerMenu.addSeparator();
        tunerMenu.add(Actions.getAction(Actions.Name.CLEAR_LINEUP));
        tunerMenu.add(Actions.getAction(Actions.Name.SCAN));
        tunerMenu.add(Actions.getAction(Actions.Name.MATCH_LINEUP));
        tunerMenu.add(Actions.getAction(Actions.Name.EDIT_LINEUP));
        tunerMenu.add(Actions.getAction(Actions.Name.COPY_LINEUP));

        rootMenu = new JPopupMenu();
        rootMenu.add(Actions.getAction(Actions.Name.DISCOVER));

        programMenu = new JPopupMenu();
        programMenu.add(Actions.getAction(Actions.Name.STREAM_INFO));
        programMenu.add(Actions.getAction(Actions.Name.EDIT_PROGRAM));
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

            TreePath p = Main.tree.getPathForLocation(
                    e.getPoint().x, e.getPoint().y);
            Main.tree.setSelectionPath(p);
            if (p != null && p.getPathCount() > 0) {
                JPopupMenu popup = null;

                Object value = p.getLastPathComponent();

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
}
