package com.hanhuy.hdhr;

import com.hanhuy.common.ui.Util;
import com.hanhuy.common.ui.CollectionBackedListModel;
import com.hanhuy.common.ui.ResourceBundleForm;
import com.hanhuy.hdhr.ui.RunnableAction;
import com.hanhuy.hdhr.config.ChannelMap.Channel.Program;
import com.hanhuy.hdhr.treemodel.DeviceTreeModel;
import com.hanhuy.hdhr.treemodel.Tuner;

import java.util.Arrays;
import java.util.List;

import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.JDialog;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;

public class EditChannelLineupForm extends ResourceBundleForm {
    private static JDialog d;
    private boolean updated;
    public EditChannelLineupForm(Tuner t, List<Program> programs) {
        if (d != null) {
            d.requestFocusInWindow();
            d.toFront();
            return;
        }
        d = new JDialog(Main.frame, getString("editLineupName"));
        d.setLayout(createLayoutManager());

        layout(t, programs);
        d.pack();
        d.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        Util.centerWindow(Main.frame, d);
        d.setVisible(true);
    }

    private void layout(final Tuner t, List<Program> programs) {
        final CollectionBackedListModel<Program> model =
                new CollectionBackedListModel<Program>(programs);
        final JList list = new JList(model);
        JScrollPane listPane = new JScrollPane(list,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        d.add(listPane, "list");

        final JButton top = new JButton(new RunnableAction(getString("topName"),
                getInt("topMnemonic"),
                new Runnable() {
            public void run() {
                updated = true;

                int[] indices = list.getSelectedIndices();
                Arrays.sort(indices);
                int offset = indices[0];
                for (int i = 0; i < indices.length; i++) {
                    int idx = indices[i];
                    Program p = model.remove(idx);
                    model.add(idx - offset, p);
                }
                for (int i = 0; i < indices.length; i++) {
                    indices[i] -= offset;
                }
                list.setSelectedIndices(indices);
                list.ensureIndexIsVisible(indices[0]);
            }
        }));
        final JButton bottom = new JButton(new RunnableAction(
                getString("bottomName"), getInt("bottomMnemonic"),
                new Runnable() {
            public void run() {
                updated = true;
                int[] indices = list.getSelectedIndices();
                Arrays.sort(indices);
                int offset = model.size() - 1 - indices[indices.length - 1];
                for (int i = indices.length; i > 0; i--) {
                    int idx = indices[i - 1];
                    Program p = model.remove(idx);
                    model.add(idx + offset, p);
                }
                for (int i = 0; i < indices.length; i++) {
                    indices[i] += offset;
                }
                list.setSelectedIndices(indices);
                list.ensureIndexIsVisible(indices[indices.length - 1]);
            }
        }));
        final JButton up = new JButton(new RunnableAction(getString("upName"),
                getInt("upMnemonic"),
                new Runnable() {
            public void run() {
                updated = true;

                int[] indices = list.getSelectedIndices();
                Arrays.sort(indices);
                for (int i = 0; i < indices.length; i++) {
                    int idx = indices[i];
                    Program p = model.remove(idx);
                    model.add(idx - 1, p);
                }
                for (int i = 0; i < indices.length; i++) {
                    indices[i]--;
                }
                list.setSelectedIndices(indices);
                list.ensureIndexIsVisible(indices[0]);
            }
        }));;
        final JButton down = new JButton(new RunnableAction(
                getString("downName"), getInt("downMnemonic"),
                new Runnable() {
            public void run() {
                updated = true;
                int[] indices = list.getSelectedIndices();
                Arrays.sort(indices);
                for (int i = indices.length; i > 0; i--) {
                    int idx = indices[i - 1];
                    Program p = model.remove(idx);
                    model.add(idx + 1, p);
                }
                for (int i = 0; i < indices.length; i++) {
                    indices[i]++;
                }
                list.setSelectedIndices(indices);
                list.ensureIndexIsVisible(indices[indices.length - 1]);
            }
        }));;
        final JButton close = new JButton(new RunnableAction(
                getString("closeName"), getInt("closeMnemonic"),
                new Runnable() {
            public void run() {
                d.setVisible(false);
                d.dispose();
                d = null;
                if (updated) {
                    Main.model.fireTreeStructureChanged(new Object[] {
                        DeviceTreeModel.ROOT_NODE, t.device, t
                    });
                    Actions.getAction(Actions.Name.JUMP_TO_LAST_PROGRAM).
                            actionPerformed(null);
                }
            }
        }));;
        final JButton add = new JButton(new RunnableAction(
                getString("addName"), getInt("addMnemonic"),
                new Runnable() {
            public void run() {
                javax.swing.JOptionPane.showMessageDialog(Main.frame, "Not yet implemented");
            }
        }));;
        final JButton remove = new JButton(new RunnableAction(
                getString("removeName"), getInt("removeMnemonic"),
                new Runnable() {
            public void run() {
                updated = true;
                int[] indices = list.getSelectedIndices();
                Arrays.sort(indices);
                for (int i = indices.length; i > 0; i--) {
                    int idx = indices[i - 1];
                    model.remove(idx);
                }
            }
        }));;

        d.add(top,    "top");
        d.add(up,     "up");
        d.add(down,   "down");
        d.add(bottom, "bottom");
        d.add(add,    "add");
        d.add(remove, "remove");
        d.add(close,  "close");

        top.getAction().setEnabled(false);
        up.getAction().setEnabled(false);
        down.getAction().setEnabled(false);
        bottom.getAction().setEnabled(false);
        remove.getAction().setEnabled(false);

        list.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                int idx = list.getSelectedIndex();
                int[] indices = list.getSelectedIndices();
                Arrays.sort(indices);
                remove.getAction().setEnabled(indices.length > 0);
                top.getAction().setEnabled(
                        indices.length > 0 && indices[0] > 0);
                up.getAction().setEnabled(indices.length > 0 && indices[0] > 0);
                bottom.getAction().setEnabled(
                        indices.length > 0 &&
                        indices[indices.length -1] < model.size() - 1);
                down.getAction().setEnabled(
                        indices.length > 0 &&
                        indices[indices.length -1] < model.size() - 1);
            }
        });
    }
}
