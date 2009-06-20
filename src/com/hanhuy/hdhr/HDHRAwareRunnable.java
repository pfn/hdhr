package com.hanhuy.hdhr;

import com.hanhuy.hdhr.ui.RunnableAction;
import com.hanhuy.hdhr.treemodel.Tuner;
import com.hanhuy.hdhr.config.ChannelMap.Channel.Program;

public abstract class HDHRAwareRunnable implements Runnable {
    private Tuner tuner;
    private Program program;

    public void setTuner(Tuner t) {
        tuner = t;
    }
    public void setProgram(Program p) {
        program = p;
    }

    public Tuner getTuner() {
        return tuner;
    }
    public Program getProgram() {
        return program;
    }
}
