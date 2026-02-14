package com.example;

public class Hello implements Greeter {
    private final String name;

    public Hello(String name) {
        this.name = name;
    }

    @Override
    public String greet() {
        return "Hello, " + name + "!";
    }
}
