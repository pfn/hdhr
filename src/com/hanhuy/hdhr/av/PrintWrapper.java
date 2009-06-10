package com.hanhuy.hdhr.av;

import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationHandler;
import java.util.Arrays;

public class PrintWrapper implements InvocationHandler {
    private Object instance;
    @SuppressWarnings("unchecked")
    public static <T> T wrap(Class<T> clazz, Object instance) {
        PrintWrapper wrapper = new PrintWrapper();
        wrapper.instance = instance;
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(),
                new Class[] { clazz }, wrapper);
    }

    public Object invoke(Object proxy, Method method, Object[] args)
    throws Throwable {
        System.out.println(String.format("%s(%s)",
                method.getName(), Arrays.deepToString(args)));
        return method.invoke(instance, args);
    }
}
