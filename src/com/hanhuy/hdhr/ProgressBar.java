package com.hanhuy.hdhr;

import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JProgressBar;

public class ProgressBar {

    private enum Command {
        SET_STRING_PAINTED,
        SET_STRING,
        SET_INDETERMINATE,
        SET_MINIMUM,
        SET_MAXIMUM,
        SET_VALUE,
        GET_VALUE,
    };
    private final JProgressBar bar;
    public ProgressBar(JProgressBar b) {
        bar = b;
    }

    public void setStringPainted(boolean p) {
        invoke(Command.SET_STRING_PAINTED, p);
    }

    public void setString(String s) {
        invoke(Command.SET_STRING, s);
    }

    public void setMinimum(int m) {
        invoke(Command.SET_MINIMUM, m);
    }

    public void setMaximum(int m) {
        invoke(Command.SET_MAXIMUM, m);
    }

    public void setValue(int value) {
        invoke(Command.SET_VALUE, value);
    }

    public void setIndeterminate(boolean i) {
        invoke(Command.SET_INDETERMINATE, i);
    }

    public int getValue() {
        return (Integer) invokeAndWait(Command.GET_VALUE);
    }

    private void invoke(Command c, Object... args) {
        CommandInvoker i = new CommandInvoker();
        i.command = c;
        i.args = args;
        EventQueue.invokeLater(i);
    }

    private Object invokeAndWait(Command c, Object... args) {
        CommandInvoker i = new CommandInvoker();
        i.command = c;
        i.args = args;
        try {
            EventQueue.invokeAndWait(i);
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else if (t instanceof Error) {
                throw (Error) t;
            } else {
                throw new RuntimeException(t);
            }
        }
        return i.result;
    }

    private class CommandInvoker implements Runnable {
        volatile Command command;
        volatile Object[] args;
        volatile Object result;
        public void run() {
            switch (command) {
            case SET_STRING_PAINTED:
                bar.setStringPainted((Boolean) args[0]);
                break;
            case SET_STRING:
                bar.setString((String) args[0]);
                break;
            case SET_VALUE:
                bar.setValue((Integer) args[0]);
                break;
            case GET_VALUE:
                result = bar.getValue();
                break;
            case SET_MINIMUM:
                bar.setMinimum((Integer) args[0]);
                break;
            case SET_MAXIMUM:
                bar.setMaximum((Integer) args[0]);
                break;
            case SET_INDETERMINATE:
                bar.setIndeterminate((Boolean) args[0]);
                break;
            default:
                throw new IllegalArgumentException(command.toString());
            }
        }
    }
}
