package com.hanhuy.hdhr.treemodel;

import com.hanhuy.hdhr.config.Control;

import java.io.Serializable;

public class Device implements Serializable {

    private final static long serialVersionUID = 200905251536L;
    public final int id;
    public final Tuner[] tuners;
    public Device(int id) {
        this.id = id;

        tuners = new Tuner[2];
        tuners[0] = new Tuner(this, Control.Tuner.TUNER0);
        tuners[1] = new Tuner(this, Control.Tuner.TUNER1);
    }
    @Override
    public String toString() {
        return Integer.toHexString(id);
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Device)
            return ((Device)other).id == id;
        return false;
    }
}
