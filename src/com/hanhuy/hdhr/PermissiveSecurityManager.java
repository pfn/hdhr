package com.hanhuy.hdhr;

import java.io.FileDescriptor;
import java.net.InetAddress;
import java.security.Permission;

public class PermissiveSecurityManager extends SecurityManager {
    public void checkPermission(Permission p) { }
    public void checkPermission(Permission p, Object o) { }
    public void checkCreateClassLoader() { }
    public void checkAccess(Thread t) { }
    public void checkAccess(ThreadGroup g) { }
    public void checkExit(int i) { }
    public void checkExec(String s) { }
    public void checkLink(String s) { }
    public void checkRead(FileDescriptor fd) { }
    public void checkRead(String s) { }
    public void checkRead(String s, Object o) { }
    public void checkWrite(FileDescriptor fd) { }
    public void checkWrite(String s) { }
    public void checkDelete(String s) { }
    public void checkConnect(String s, int i) { }
    public void checkConnect(String s, int i, Object o) { }
    public void checkListen(int i) { }
    public void checkAccept(String s, int i) { }
    public void checkMulticast(InetAddress a) { }
    @SuppressWarnings("deprecation")
    public void checkMulticast(InetAddress a, byte b) { }
    public void checkPropertiesAccess() { }
    public void checkPropertyAccess(String s) { }
    public void checkPrintJobAccess() { }
    public void checkSystemClipboardAccess() { }
    public void checkAwtEventQueueAccess() { }
    public void checkPackageAccess(String s) { }
    public void checkPackageDefinition(String s) { }
    public void checkSetFactory() { }
    public void checkMemberAccess(Class c, int i) { }
    public void checkSecurityAccess(String s) { }
}

