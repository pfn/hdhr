package com.hanhuy.hdhr.ui;

public interface ProgressBar {
    public void setStringPainted(boolean p);
    public void setString(String s);
    public void setMinimum(int m);
    public void setMaximum(int m);
    public void setValue(int value);
    public void setIndeterminate(boolean i);
    public int getValue();
}
