package com.hanhuy.hdhr;

import com.hanhuy.common.ui.DataBindingManager;
import com.hanhuy.common.ui.ResourceBundleForm;
import com.hanhuy.hdhr.treemodel.Tuner;
import com.hanhuy.hdhr.config.Control;
import com.hanhuy.hdhr.config.TunerException;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeSelectionEvent;

public class TunerInfoCard extends ResourceBundleForm
implements TreeSelectionListener {
    public final static TunerInfoCard INSTANCE = new TunerInfoCard();
    public final static String CARD_NAME = "TunerInfoCard";
    public final JPanel card;

    private JLabel name       = new JLabel();
    private JLabel channel    = new JLabel();
    private JLabel channelmap = new JLabel();
    private JLabel filter     = new JLabel();
    private JLabel program    = new JLabel();
    private JLabel target     = new JLabel();
    private JLabel status     = new JLabel();
    private JLabel debug      = new JLabel();
    private JLabel lockedBy   = new JLabel();

    private TunerInfoCard() {
        card = new JPanel();
        card.setLayout(createLayoutManager());

        card.add(name,       "name");
        card.add(lockedBy,   "lockedBy");
        card.add(target,     "target");
        card.add(channel,    "channel");
        card.add(status,     "status");
        card.add(channelmap, "channelmap");
        card.add(filter,     "filter");
        card.add(program,    "program");
        card.add(debug,      "debug");
    }

    public void setTuner(Tuner t) {
        name.setText(t.tuner.toString());
        Control device = new Control();
        String tuner = String.format("/tuner%d/", t.tuner.ordinal());
        try {
            device.connect(t.device.id);
            lockedBy.setText(device.get(tuner + "lockkey"));
            target.setText(device.get(tuner + "target"));
            channel.setText(device.get(tuner + "channel"));
            status.setText(device.get(tuner + "status"));
            channelmap.setText(device.get(tuner + "channelmap"));
            filter.setText(device.get(tuner + "filter"));
            program.setText(device.get(tuner + "program"));
            debug.setText("<html><pre>" + device.get(tuner + "debug"));
        }
        catch (TunerException e) {
            JOptionPane.showMessageDialog(Main.frame, e.getMessage());
        }
        finally {
            device.close();
        }
    }

    public void valueChanged(TreeSelectionEvent e) {
        Object item = e.getPath().getLastPathComponent();
        if (item instanceof Tuner) {
            setTuner((Tuner) item);
            Main.cards.show(Main.cardPane, CARD_NAME);
        }
    }
}
