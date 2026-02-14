package com.example;

public class App implements Greeter {
    @Override
    public String greet() {
        return new Hello("world").greet();
    }

    public static void main(String[] args) {
        System.out.println(new App().greet());
    }
}
