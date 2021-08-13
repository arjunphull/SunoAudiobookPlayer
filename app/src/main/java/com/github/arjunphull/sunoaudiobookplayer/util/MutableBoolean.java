package com.github.arjunphull.sunoaudiobookplayer.util;

public class MutableBoolean {
    private boolean val;

    public MutableBoolean(boolean value) {
        val = value;
    }

    public boolean get() {
        return val;
    }

    public void setTrue() {
        val = true;
    }

    public void setFalse() {
        val = false;
    }
}
