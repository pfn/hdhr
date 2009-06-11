package com.hanhuy.hdhr.treemodel;

import com.hanhuy.hdhr.config.ChannelMap.Channel.Program;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.Map;
import java.util.List;

public class DataFileDumper {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        File f = new File(args[0]);
        FileInputStream fin = null;
        ObjectInputStream oin = null;
        try {
            fin = new FileInputStream(f);
            oin = new ObjectInputStream(fin);
            Map<Tuner,List<Program>> programs = (Map) oin.readObject();
            for (Tuner t : programs.keySet()) {
                System.out.println("Tuner: " + t);
                List<Program> list = programs.get(t);
                for (int i = 0, j = list.size(); i < j; i++) {
                    Program p = list.get(i);
                    System.out.println("  " + i +
                            "  Program: " + list.indexOf(p) + " -- " + p);
                }
            }
        }
        finally {
            if (oin != null) oin.close();
            if (fin != null) fin.close();
        }
    }
}
