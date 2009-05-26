package com.hanhuy.hdhr.treemodel;

import com.hanhuy.hdhr.config.ChannelMap.Channel.Program;
import com.hanhuy.common.ui.ResourceBundleForm;

import java.awt.Component;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;

public class DeviceTreeCellRenderer extends ResourceBundleForm
implements TreeCellRenderer {
    private DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
    private Icon devicesIcon = getIcon("devicesIcon");
    private Icon deviceIcon  = getIcon("deviceIcon");
    private Icon tunerIcon   = getIcon("tunerIcon");
    private Icon programIcon = getIcon("programIcon");

    public Component getTreeCellRendererComponent(JTree tree,
            Object item, boolean selected, boolean expanded, boolean leaf,
            int row, boolean focused) {
        JLabel l = (JLabel) renderer.getTreeCellRendererComponent(
                tree, item, selected, expanded, leaf, row, focused);
        if (item == DeviceTreeModel.ROOT_NODE)
            l.setIcon(devicesIcon);
        else if (item instanceof Device)
            l.setIcon(deviceIcon);
        else if (item instanceof Tuner)
            l.setIcon(tunerIcon);
        else if (item instanceof Program)
            l.setIcon(programIcon);
        return l;
    }
}
