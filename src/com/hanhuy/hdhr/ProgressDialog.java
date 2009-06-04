package com.hanhuy.hdhr;

import com.hanhuy.common.ui.Util;
import com.hanhuy.common.ui.ResourceBundleForm;

import javax.swing.JDialog;
import javax.swing.JProgressBar;
import javax.swing.JButton;

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
        d.width = 480;
        bar.setPreferredSize(d);

        dialog.setLayout(createLayoutManager());
        dialog.add(bar, "progressbar");
    }
    private void addCancelButton(Runnable r) {
        if (r == null) return;
        JButton button = new JButton(new RunnableAction("Cancel", r));
        dialog.add(button, "cancelbutton");
    }
    public void showProgress(Runnable r) {
        showProgress(r, null);
    }
    public void showProgress(final Runnable r, final Runnable cancel) {
        addCancelButton(cancel);
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
