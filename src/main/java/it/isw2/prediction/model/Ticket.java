package it.isw2.prediction.model;

import java.time.LocalDateTime;

public class Ticket {

    private int id;
    private String key;

    private LocalDateTime resolutionDate;
    private LocalDateTime creationDate;

    public Ticket(int id, String key, LocalDateTime resolutionDate, LocalDateTime creationDate) {
        this.id = id;
        this.key = key;
        this.resolutionDate = resolutionDate;
        this.creationDate = creationDate;
    }

    @Override
    public String toString() {
        return "Ticket (" + id + ") => " + key + ": " + creationDate + " - " + resolutionDate;
    }

}
