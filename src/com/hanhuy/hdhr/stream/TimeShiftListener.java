package com.hanhuy.hdhr.stream;

import java.util.EventListener;

public interface TimeShiftListener extends EventListener {
    public void timePaused(TimeShiftEvent e);
    public void timeShifted(TimeShiftEvent e);
    public void timeResumed(TimeShiftEvent e);
}
