package com.hanhuy.hdhr.stream;

import java.util.EventObject;

public class TimeShiftEvent extends EventObject {
    public enum EventType { PAUSE, RESUME, SHIFT };
    public final EventType type;
    public TimeShiftEvent(TimeShiftStream src, EventType type) {
        super(src);

        this.type = type;
    }
}
