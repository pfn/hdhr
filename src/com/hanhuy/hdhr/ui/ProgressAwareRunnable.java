package com.hanhuy.hdhr.ui;

public interface ProgressAwareRunnable extends Runnable {
    public void setProgressBar(ProgressBar bar);
}
