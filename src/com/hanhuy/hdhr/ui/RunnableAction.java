package com.hanhuy.hdhr.ui;

import java.awt.event.ActionEvent;

import javax.swing.Action;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.KeyStroke;

public class RunnableAction extends AbstractAction implements Action {

    protected Runnable r;
    public RunnableAction(String name, Runnable action) {
        this(name, null, null, -1, null, null, action);
    }
    public RunnableAction(String name,
            int mnemonic_vk, Runnable action) {
        this(name, null, null, mnemonic_vk, null, null, action);
    }
    public RunnableAction(String name,
            int mnemonic_vk, String accelerator, Runnable action) {
        this(name, null, null, mnemonic_vk, accelerator, null, action);
    }
    public RunnableAction(String name, String shortDesc, String description,
            int mnemonic_vk, String accelerator, Icon icon, Runnable action) {
        putValue(NAME, name);
        if (mnemonic_vk != -1)
            putValue(MNEMONIC_KEY, mnemonic_vk);
        if (accelerator != null)
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(accelerator));
        if (icon != null)
            putValue(SMALL_ICON, icon);
        if (shortDesc != null)
            putValue(SHORT_DESCRIPTION, shortDesc);
        if (description != null)
            putValue(LONG_DESCRIPTION, description);
        if (action == null)
            throw null;
        r = action;
    }

    public void actionPerformed(ActionEvent e) {
        r.run();
    }
}
