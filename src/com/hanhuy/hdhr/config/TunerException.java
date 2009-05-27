package com.hanhuy.hdhr.config;

public class TunerException extends Exception {
    public TunerException(Throwable t) {
        super(t);
    }
    public TunerException(String msg) {
        super(msg);
    }
    public TunerException(String msg, Throwable t) {
        super(msg, t);
    }
}
