package com.fongmi.android.tv.bean;

public class TextHeader {

    private final String text;

    public TextHeader(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return text;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TextHeader other)) return false;
        return text != null ? text.equals(other.text) : other.text == null;
    }

    @Override
    public int hashCode() {
        return text != null ? text.hashCode() : 0;
    }
}
