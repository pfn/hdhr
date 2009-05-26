package com.hanhuy.hdhr;

import javax.swing.JProgressBar;

public interface ProgressAwareRunnable extends Runnable {
    public void setJProgressBar(JProgressBar bar);
}
