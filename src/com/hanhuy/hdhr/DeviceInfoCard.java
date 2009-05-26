package com.hanhuy.hdhr;

import com.hanhuy.common.ui.DataBindingManager;
import com.hanhuy.common.ui.ResourceBundleForm;
import com.hanhuy.hdhr.treemodel.Device;
import com.hanhuy.hdhr.config.Control;
import com.hanhuy.hdhr.config.TunerException;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeSelectionEvent;

public class DeviceInfoCard extends ResourceBundleForm
implements TreeSelectionListener {
    public final static DeviceInfoCard INSTANCE = new DeviceInfoCard();
    public final static String CARD_NAME = "DeviceInfoCard";
    public final JPanel card;

    private JLabel id        = new JLabel();
    private JLabel location  = new JLabel();
    private JLabel irTarget  = new JLabel();
    private JLabel model     = new JLabel();
    private JLabel features  = new JLabel();
    private JLabel version   = new JLabel();
    private JLabel copyright = new JLabel();
    private JLabel debug     = new JLabel();

    private DeviceInfoCard() {
        card = new JPanel();
        card.setLayout(createLayoutManager());

        card.add(id,        "id");
        card.add(location,  "location");
        card.add(irTarget,  "irTarget");
        card.add(model,     "model");
        card.add(features,  "features");
        card.add(version,   "version");
        card.add(copyright, "copyright");
        card.add(debug,     "debug");
    }

    public void setDevice(Device d) {
        Control device = new Control();

        try {
            device.connect(d.id);
            id.setText(Integer.toHexString(d.id));
            location.setText(device.get("/lineup/location"));
            irTarget.setText(device.get("/ir/target"));
            model.setText(device.get("/sys/model"));
            features.setText("<html><pre>" + device.get("/sys/features"));
            version.setText(device.get("/sys/version"));
            copyright.setText(device.get("/sys/copyright"));
            debug.setText("<html><pre>" + device.get("/sys/debug"));
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
        if (item instanceof Device) {
            setDevice((Device) item);
            Main.cards.show(Main.cardPane, CARD_NAME);
        }
    }
}
