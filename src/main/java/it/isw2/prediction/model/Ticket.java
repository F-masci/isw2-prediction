package it.isw2.prediction.model;

import it.isw2.prediction.VersionRole;

import java.time.LocalDateTime;

public class Ticket {

    private int id;
    private String key;

    private LocalDateTime resolutionDate;
    private LocalDateTime creationDate;

    private Version affectedVersion;
    private Version openingVersion;
    private Version fixedVersion;
    private boolean isProportionalVersion;

    public Ticket(int id, String key, LocalDateTime resolutionDate, LocalDateTime creationDate) {
        this.id = id;
        this.key = key;
        this.resolutionDate = resolutionDate;
        this.creationDate = creationDate;
    }

    public int getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public LocalDateTime getResolutionDate() {
        return resolutionDate;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    /* --- VERSIONS --- */

    public Version getAffectedVersion() {
        return affectedVersion;
    }

    public void setAffectedVersion(Version affectedVersion, boolean isProportionalVersion) {
        this.affectedVersion = affectedVersion;
        this.isProportionalVersion = isProportionalVersion;
        affectedVersion.addTicket(VersionRole.AFFECTED, this);
    }

    public Version getOpeningVersion() {
        return openingVersion;
    }

    public void setOpeningVersion(Version openingVersion) {
        this.openingVersion = openingVersion;
        openingVersion.addTicket(VersionRole.OPENED, this);
    }

    public Version getFixedVersion() {
        return fixedVersion;
    }

    public void setFixedVersion(Version fixedVersion) {
        this.fixedVersion = fixedVersion;
        fixedVersion.addTicket(VersionRole.FIXED, this);
    }

    public boolean isProportionalVersion() {
        return isProportionalVersion;
    }

    /* --- FORMATTER --- */

    @Override
    public String toString() {
        return "Ticket (" + id + ") => " + key + ": " + creationDate + " - " + resolutionDate;
    }

    /* --- EQUALS & HASHCODE --- */

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Ticket ticket = (Ticket) obj;
        return id == ticket.id && key.equals(ticket.key);
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(id);
        result = 31 * result + key.hashCode();
        return result;
    }

}
