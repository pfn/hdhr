package com.hanhuy.hdhr.stream;

import java.util.EventListener;
import java.io.Closeable;
import java.io.IOException;

public interface PacketListener extends EventListener, Closeable {
    public void packetArrived(PacketEvent e) throws IOException;
    public boolean isClosed();
}
