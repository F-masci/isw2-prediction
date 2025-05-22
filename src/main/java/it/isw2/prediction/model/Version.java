package it.isw2.prediction.model;

import java.time.LocalDate;

public class Version {

    private int id;
    private String name;
    private LocalDate releaseDate;

    public Version(int id, String name, LocalDate releaseDate) {
        this.id = id;
        this.name = name;
        this.releaseDate = releaseDate;
    }

    @Override
    public String toString() {
        return "Version (" + id + ") => " + name + ": " + releaseDate;
    }

}
