package com.hanhuy.hdhr;

import com.hanhuy.hdhr.ui.RunnableAction;
import com.hanhuy.hdhr.treemodel.Tuner;
import com.hanhuy.hdhr.config.ChannelMap.Channel.Program;

public class HDHRAwareAction extends RunnableAction {
    private Tuner tuner;
    private Program program;

    public HDHRAwareAction(String name, Runnable action) {
        super(name, action);
    }
    public HDHRAwareAction(String name, int mnemonic_vk, Runnable action) {
        super(name, mnemonic_vk, action);
    }
    public HDHRAwareAction(String name,
            int mnemonic_vk, String accelerator, Runnable action) {
        super(name, mnemonic_vk, accelerator, action);
    }

    public void setTuner(Tuner t) {
        tuner = t;
        getRunnable().setTuner(t);
    }
    public void setProgram(Program p) {
        program = p;
        getRunnable().setProgram(p);
    }

    public Tuner getTuner() {
        return tuner;
    }
    public Program getProgram() {
        return program;
    }

    private HDHRAwareRunnable getRunnable() {
        return (HDHRAwareRunnable) r;
    }
}
