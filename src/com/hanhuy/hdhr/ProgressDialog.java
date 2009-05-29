package com.hanhuy.hdhr;

import com.hanhuy.common.ui.Util;
import com.hanhuy.common.ui.ResourceBundleForm;

import javax.swing.JDialog;
import javax.swing.JProgressBar;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;

public class ProgressDialog extends ResourceBundleForm {
    private JDialog dialog;
    private Frame parent;
    private JProgressBar bar;

    public ProgressDialog(String title) {
        this(null, title);
    }
    public ProgressDialog() {
        this(null, null);
    }
    public ProgressDialog(Frame parent) {
        this(parent, null);
    }
    public ProgressDialog(Frame parent, String title) {
        this.parent = parent;
        dialog = new JDialog(parent, title, true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        bar = new JProgressBar();
        Dimension d = bar.getPreferredSize();
        d.width = 320;
        bar.setPreferredSize(d);

        dialog.setLayout(createLayoutManager());
        dialog.add(bar, "progressbar");
    }
    public void showProgress(final Runnable r) {
        bar.setIndeterminate(true);
        Thread t = new Thread(new Runnable() {
            public void run() {
                if (r instanceof ProgressAwareRunnable)
                    ((ProgressAwareRunnable)r).setJProgressBar(bar);
                try {
                    r.run();
                }
                finally {
                    EventQueue.invokeLater(new Runnable() {
                        public void run() {
                            dialog.setVisible(false);
                            dialog.dispose();
                        }
                    });
                }
            }
        }, "ProgressDialog");
        t.start();
        dialog.pack();
        Util.centerWindow(parent, dialog);
        dialog.setVisible(true);
    }
}
