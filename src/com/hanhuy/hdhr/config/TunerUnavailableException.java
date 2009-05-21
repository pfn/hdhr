package com.hanhuy.hdhr.config;

public class TunerUnavailableException extends TunerException {
    public TunerUnavailableException(String msg) {
        super(msg);
    }
    public TunerUnavailableException(String msg, Throwable t) {
        super(msg, t);
    }
}
