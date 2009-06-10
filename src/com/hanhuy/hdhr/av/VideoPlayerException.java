package com.hanhuy.hdhr.av;

public class VideoPlayerException extends RuntimeException {
    public VideoPlayerException(Throwable t) {
        super(t);
    }
    public VideoPlayerException(String msg) {
        super(msg);
    }
    public VideoPlayerException(String msg, Throwable t) {
        super(msg, t);
    }
}
