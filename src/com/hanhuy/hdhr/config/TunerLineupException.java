package com.hanhuy.hdhr.config;

public class TunerLineupException extends TunerException {
    public TunerLineupException(Throwable t) {
        super(t);
    }
    public TunerLineupException(String msg) {
        super(msg);
    }
    public TunerLineupException(String msg, Throwable t) {
        super(msg, t);
    }
}
