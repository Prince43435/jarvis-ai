package com.jarvisai.app;

import android.app.Application;

import com.jarvisai.app.memory.MemoryDB;
import com.jarvisai.app.memory.MemoryEngine;

public class JarvisApp extends Application {

    private static JarvisApp instance;
    private MemoryDB db;
    private MemoryEngine memory;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        db = new MemoryDB(this);
        memory = new MemoryEngine(this, db);
    }

    public static JarvisApp get() {
        return instance;
    }

    public MemoryDB db() {
        if (db == null) db = new MemoryDB(this);
        return db;
    }

    public MemoryEngine memory() {
        if (memory == null) memory = new MemoryEngine(this, db());
        return memory;
    }
}
