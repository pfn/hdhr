package com.hanhuy.hdhr;

public interface ProgressAwareRunnable extends Runnable {
    public void setProgressBar(ProgressBar bar);
}
