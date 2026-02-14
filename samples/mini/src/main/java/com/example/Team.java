package com.example;

import java.util.List;

public class Team {
    private final List<Greeter> greeters;

    public Team(List<Greeter> greeters) {
        this.greeters = greeters;
    }

    public List<Greeter> getGreeters() {
        return greeters;
    }
}
