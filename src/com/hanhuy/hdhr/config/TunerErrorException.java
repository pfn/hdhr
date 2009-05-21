package com.hanhuy.hdhr.config;

public class TunerErrorException extends TunerException {
    public TunerErrorException(String msg) {
        super(msg);
    }
    public TunerErrorException(String msg, Throwable t) {
        super(msg, t);
    }
}
