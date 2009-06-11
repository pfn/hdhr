package com.hanhuy.hdhr.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HTTPStream implements RTPProxy.PacketListener {

    private HttpServer server;
    private volatile boolean shutdown;

    private final ArrayList<OutputStream> streams =
            new ArrayList<OutputStream>();

    public HTTPStream() throws SocketException, IOException {
        
        InetAddress localhost;
        try {
            localhost = InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 });
        }
        catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }

        server = HttpServer.create(
                new InetSocketAddress(localhost, 0), 0);
        setupServer();
    }

    private void setupServer() {
        server.createContext("/", new HttpHandler() {
            public void handle(HttpExchange e) throws IOException {
                byte[] b = new byte[32768];
                int read;
                InputStream in = e.getRequestBody();
                while ((read = in.read(b, 0, 32768)) != -1)
                    ; // do nothing

                in.close();
                e.getResponseHeaders().add("Content-type", "video/x-mpeg-ts");
                if (!"GET".equals(e.getRequestMethod())) {
                    e.sendResponseHeaders(200, -1);
                    return;
                }
                e.sendResponseHeaders(200, 0);
                streams.add(e.getResponseBody());
            }
        });
        server.start();
    }

    public void close() {
        shutdown = true;
        for (OutputStream out : streams) {
            try {
                out.close();
            }
            catch (IOException e) { }
        }
        server.stop(1);
    }

    public void packetArrived(RTPProxy.PacketEvent e) throws IOException {
        byte[] packet = e.packet;

        if (shutdown) return;
        for (Iterator<OutputStream> i = streams.iterator(); i.hasNext();) {
            OutputStream out = i.next();
            try {
                out.write(packet);
            }
            catch (IOException ex) {
                try {
                    out.close();
                }
                catch (IOException ex2) { }
                i.remove();
            }
        }
    }

    public int getLocalPort() {
        return server.getAddress().getPort();
    }
}
