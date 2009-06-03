package com.hanhuy.hdhr.treemodel;

import com.hanhuy.hdhr.config.Control;
import com.hanhuy.hdhr.config.ChannelMap.Channel.Program;

import java.util.List;
import java.util.ArrayList;

import java.io.Serializable;

public class Tuner implements Serializable {
    private final static long serialVersionUID = 200905251535L;
    public final Control.Tuner tuner;
    public final Device device;

    public Tuner(Device device, Control.Tuner tuner) {
        this.device = device;
        this.tuner  = tuner;
    }

    public String toString() {
        return Integer.toHexString(device.id).toUpperCase() +
                "-" + tuner.ordinal();
    }

    @Override
    public int hashCode() {
        return device.id + tuner.ordinal();
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Tuner) {
             Tuner t = (Tuner) other;
             return t.device.id == device.id && t.tuner == tuner;
        }
        return false;
    }
}
