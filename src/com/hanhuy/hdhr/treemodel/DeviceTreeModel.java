package com.hanhuy.hdhr.treemodel;

import com.hanhuy.hdhr.config.Discover;
import com.hanhuy.hdhr.config.TunerException;
import com.hanhuy.hdhr.config.Control;
import com.hanhuy.hdhr.config.ChannelMap.Channel.Program;
import com.hanhuy.hdhr.ui.ProgressDialog;
import com.hanhuy.hdhr.Main;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.net.InetAddress;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.awt.EventQueue;

import javax.swing.JOptionPane;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeModelEvent;

public class DeviceTreeModel implements TreeModel {
    public final static String ROOT_NODE = "Tuner Devices";
    public final static File PROGRAM_FILE = new File(
            System.getProperty("user.home"), ".hdhrb.ser");

    private Map<Integer,InetAddress[]> devices;
    private boolean discovering;
    private List<Device> deviceObjs;
    private Set<TreeModelListener> listeners =
            new HashSet<TreeModelListener>();
    public final Map<Tuner,List<Program>> programMap;

    @SuppressWarnings("unchecked")
    public DeviceTreeModel() {
        Map<Tuner,List<Program>> map = null;
        if (!PROGRAM_FILE.exists()) {
            map = new HashMap<Tuner,List<Program>>();
        } else {
            FileInputStream fin   = null;
            ObjectInputStream ois = null;
            try {
                fin = new FileInputStream(PROGRAM_FILE);
                ois = new ObjectInputStream(fin);
                map = (Map) ois.readObject();
            }
            catch (IOException e) {
                JOptionPane.showMessageDialog(Main.frame, e.getMessage());
                e.printStackTrace();
                map = new HashMap<Tuner,List<Program>>();
            }
            catch (ClassNotFoundException e) {
                JOptionPane.showMessageDialog(Main.frame, e.getMessage());
                e.printStackTrace();
                map = new HashMap<Tuner,List<Program>>();
            }
            finally {
                try {
                    if (ois != null)
                        ois.close();
                    if (fin != null)
                        fin.close();
                }
                catch (IOException e) { }
            }
        }
        programMap = map;
    }
    public Object getRoot() {
        return ROOT_NODE;
    }
    public Object getChild(Object node, int index) {
        if (ROOT_NODE == node)
            return deviceObjs.get(index);
        else if (node instanceof Device)
            return ((Device)node).tuners[index];
        else if (node instanceof Tuner) {
            if (programMap.containsKey(node))
                return programMap.get(node).get(index);
        }

        return null;
    }
    public int getChildCount(Object node) {
        if (ROOT_NODE == node) {
            discover();
            return devices != null ? devices.size() : 0;
        } else if (node instanceof Device) {
            return 2;
        } else if (node instanceof Tuner) {
            return programMap.containsKey(node) ?
                    programMap.get(node).size() : 0;
        }
        return 0;
    }
    public boolean isLeaf(Object node) {
        boolean hasChildren = false;
        hasChildren |= ROOT_NODE == node;
        hasChildren |= node instanceof Device;
        hasChildren |= node instanceof Tuner;
        return !hasChildren;
    }
    public void valueForPathChanged(TreePath path, Object value) {
        // unsupported;
    }
    public int getIndexOfChild(Object node, Object child) {
        if (child instanceof Device) {
            return deviceObjs.indexOf(((Device)child).id);
        } else if (child instanceof Tuner) {
            return ((Tuner)child).tuner.ordinal();
        } else if (child instanceof Program) {
            return programMap.get(node).indexOf(child);
        }
        return -1;
    }
    public void addTreeModelListener(TreeModelListener l) {
        listeners.add(l);
    }
    public void removeTreeModelListener(TreeModelListener l) {
        listeners.remove(l);
    }

    public TreePath getTunerPath(Tuner t) {
        return new TreePath(new Object[] { ROOT_NODE, t.device, t });
    }
    public void fireTreeNodesChanged(TreePath nodePath) {
        Object item = nodePath.getLastPathComponent();
        Object parent = nodePath.getParentPath().getLastPathComponent();

        int idx = getIndexOfChild(parent, item);
        TreeModelEvent e = new TreeModelEvent(this, nodePath.getParentPath(),
            new int[] { idx }, new Object[] { item });
        if (item instanceof Program)
            saveData();
    }
    void fireTreeNodesInserted(TreeModelEvent e) {
    }
    void fireTreeNodesRemoved(TreeModelEvent e) {
    }
    public void fireTreeStructureChanged(Object[] path) {
        TreeModelEvent e = new TreeModelEvent(this, path);
        for (TreeModelListener l : listeners)
            l.treeStructureChanged(e);

        if (path[path.length - 1] instanceof Tuner)
            saveData();
    }

    private void saveData() {
        FileOutputStream fout  = null;
        ObjectOutputStream oos = null;
        try {
            fout = new FileOutputStream(PROGRAM_FILE);
            oos = new ObjectOutputStream(fout);
            oos.writeObject(programMap);
        }
        catch (IOException ex) {
            JOptionPane.showMessageDialog(Main.frame, ex.getMessage());
            ex.printStackTrace();
        }
        finally {
            try {
                if (oos != null)
                    oos.close();
                if (fout != null)
                    fout.close();
            }
            catch (IOException exx) { }
        }
    }

    private synchronized void discover() {
        if (devices == null && !discovering) {
            discovering = true;
            try {
            ProgressDialog d = new ProgressDialog(Main.frame,
                "Discovering Devices");
            d.showProgress(new Runnable() {
                public void run() {
                    try {
                        devices = Discover.discover();
                        deviceObjs = new ArrayList<Device>();
                        for (Integer i : devices.keySet())
                            deviceObjs.add(new Device(i));
                        Collections.sort(deviceObjs);
                    }
                    catch (TunerException e) {
                        JOptionPane.showMessageDialog(
                                Main.frame, e.getMessage());
                    }
                }
            });
            }
            finally {
                discovering = false;
            }
        }
    }
    public synchronized void rediscover() {
        devices = null;
        discover();
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                fireTreeStructureChanged(new Object[] { ROOT_NODE });
            }
        });
    }
}
