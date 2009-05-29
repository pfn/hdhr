package com.hanhuy.hdhr;

import com.hanhuy.common.ui.ResourceBundleForm;
import com.hanhuy.hdhr.config.Control;
import com.hanhuy.hdhr.config.TunerException;
import com.hanhuy.hdhr.config.ChannelMap.Channel.Program;

import java.awt.Canvas;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeSelectionEvent;

public class ProgramCard extends ResourceBundleForm
implements TreeSelectionListener {
    public final static ProgramCard INSTANCE = new ProgramCard();
    public final static String CARD_NAME = "ProgramCard";
    public final JPanel card;

    private ProgramCard() {
        card = new JPanel();
        card.setLayout(createLayoutManager());

        Canvas c = new Canvas();
        card.add(c, "canvas");
    }

    public void valueChanged(TreeSelectionEvent e) {
        Object item = e.getPath().getLastPathComponent();
        if (item instanceof Program) {
            Main.cards.show(Main.cardPane, CARD_NAME);
        }
    }
}
