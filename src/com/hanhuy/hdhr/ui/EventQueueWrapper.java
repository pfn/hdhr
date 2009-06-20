package com.hanhuy.hdhr.ui;

import java.awt.EventQueue;
import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;

public class EventQueueWrapper implements InvocationHandler {
    private Object instance;
    @SuppressWarnings("unchecked")
    public static <T> T wrap(Class<T> clazz, Object instance) {
        EventQueueWrapper wrapper = new EventQueueWrapper();
        wrapper.instance = instance;
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(),
                new Class[] { clazz }, wrapper);
    }

    public Object invoke(Object proxy, final Method method, final Object[] args)
    throws Throwable {
        final Object[] r = new Object[1];
        Class<?> type = method.getReturnType();
        Runnable invoker = new Runnable() {
            public void run() {
                try {
                    r[0] = method.invoke(instance, args);
                }
                catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                catch (InvocationTargetException e) {
                    Throwable t = e.getTargetException();
                    if (t instanceof Error) {
                        throw (Error) t;
                    } else if (t instanceof RuntimeException) {
                        throw (RuntimeException) t;
                    } else {
                        throw new RuntimeException(t);
                    }
                }
            }
        };
        if (!Void.class.equals(type)) {
            if (EventQueue.isDispatchThread()) {
                invoker.run();
            } else {
                EventQueue.invokeAndWait(invoker);
            }
        } else {
            EventQueue.invokeLater(invoker);
        }
        return r[0];
    }
}
