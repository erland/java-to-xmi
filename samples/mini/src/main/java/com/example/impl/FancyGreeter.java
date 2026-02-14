package com.example.impl;

import com.example.Greeter;
import com.example.Hello;

import java.util.List;

public class FancyGreeter extends Hello implements Greeter {

    private final List<String> emojis;

    public FancyGreeter(String name, List<String> emojis) {
        super(name);
        this.emojis = emojis;
    }

    @Override
    public String greet() {
        String suffix = (emojis == null || emojis.isEmpty()) ? "" : (" " + emojis.get(0));
        return super.greet() + suffix;
    }
}
