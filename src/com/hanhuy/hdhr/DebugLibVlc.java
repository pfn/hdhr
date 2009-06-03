package com.hanhuy.hdhr;

import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationHandler;
import java.util.Arrays;

import org.videolan.jvlc.internal.LibVlc;
public class DebugLibVlc implements InvocationHandler {
    private LibVlc instance;
    public static LibVlc wrap(LibVlc instance) {
        DebugLibVlc d = new DebugLibVlc();
        d.instance = instance;
        return (LibVlc) Proxy.newProxyInstance(LibVlc.class.getClassLoader(),
                new Class[] { LibVlc.class }, d);
    }

    public Object invoke(Object proxy, Method method, Object[] args)
    throws Throwable {
        System.out.println(String.format("%s(%s)",
                method.getName(), Arrays.deepToString(args)));
        return method.invoke(instance, args);
    }
}
