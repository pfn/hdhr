package com.hanhuy.hdhr.ui;

import java.awt.EventQueue;
import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

public class EventQueueWrapper implements InvocationHandler {
    private Object instance;
    private HashMap<Method,Method> methodMap = new HashMap<Method,Method>();
    @SuppressWarnings("unchecked")
    public static <T> T wrap(Class<T> clazz, Object instance) {
        EventQueueWrapper wrapper = new EventQueueWrapper();
        wrapper.instance = instance;
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(),
                new Class[] { clazz }, wrapper);
    }

    private Method lookupMethod(Method m) throws Throwable {
        if (!methodMap.containsKey(m)) {
            Method method = instance.getClass().getDeclaredMethod(
                    m.getName(), m.getParameterTypes());
            methodMap.put(m, method);
        }

        return methodMap.get(m);
    }
    public Object invoke(Object proxy, Method m, final Object[] args)
    throws Throwable {
        final Object[] r = new Object[1];
        final Method method = lookupMethod(m);
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
