package com.hanhuy.hdhr;

import java.io.Serializable;
import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.awt.Dimension;
import java.awt.Point;
import javax.swing.tree.TreePath;

public class Preferences implements Serializable {

    public final static File PREFERENCES_FILE = new File(
            System.getProperty("user.home"), ".hdhrb.prefs.ser");
    private static Preferences INSTANCE;

    private final static long serialVersionUID = 1L;

    public TreePath lastViewedPath;
    public int fontDelta;
    public boolean programDebug;
    public String videoBackend;
    public String userUUID;
    public int volume;
    public boolean muting;
    public int splitLocation;
    public Dimension windowSize;
    public Point windowLocation;

    public synchronized static Preferences getInstance() {
        if (INSTANCE == null)
            load();
        if (INSTANCE == null)
            INSTANCE = new Preferences();

        return INSTANCE;
    }

    public static void save() {
        if (INSTANCE == null) return;

        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        try {
            fos = new FileOutputStream(PREFERENCES_FILE);
            oos = new ObjectOutputStream(fos);

            oos.writeObject(INSTANCE);
        }
        catch (IOException e) { } // ignore
        finally {
            try { if (fos != null) fos.close(); }
            catch (IOException ex) { }
            try { if (oos != null) oos.close(); }
            catch (IOException ex) { }
        }
    }

    private static void load() {
        if (!PREFERENCES_FILE.exists() && !PREFERENCES_FILE.isFile())
            return;
        FileInputStream fin = null;
        ObjectInputStream oin = null;
        try {
            fin = new FileInputStream(PREFERENCES_FILE);
            oin = new ObjectInputStream(fin);

            INSTANCE = (Preferences) oin.readObject();
        }
        catch (IOException e) { } // ignore
        catch (ClassNotFoundException e) { } // ignore
        finally {
            try { if (fin != null) fin.close(); }
            catch (IOException ex) { }
            try { if (oin != null) oin.close(); }
            catch (IOException ex) { }
        }
    }
}
