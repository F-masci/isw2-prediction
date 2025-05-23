package it.isw2.prediction.model;

import it.isw2.prediction.VersionRole;

import java.util.Date;
import java.util.ArrayList;
import java.util.List;

public class Ticket {

    private int id;
    private String key;

    private Date updateDate;
    private Date resolutionDate;
    private Date creationDate;

    private Version affectedVersion;
    private Version openingVersion;
    private List<Version> fixedVersion = new ArrayList<>();
    private boolean isProportionalVersion;

    public Ticket(int id, String key, Date creationDate, Date resolutionDate, Date updateDate) {
        this.id = id;
        this.key = key;
        this.creationDate = creationDate;
        this.resolutionDate = resolutionDate;
        this.updateDate = updateDate;
    }

    public int getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public Date getResolutionDate() {
        return resolutionDate;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public Date getUpdateDate() {
        return updateDate;
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

    public List<Version> getFixedVersions() {
        return fixedVersion;
    }

    public boolean hasFixedVersion(Version version) {
        return fixedVersion.contains(version);
    }

    public void addFixedVersion(Version fixedVersion) {
        if (this.hasFixedVersion(fixedVersion)) return;
        this.fixedVersion.add(fixedVersion);
        fixedVersion.addTicket(VersionRole.FIXED, this);
    }

    public void removeFixedVersion(Version fixedVersion) {
        if(!this.hasFixedVersion(fixedVersion)) return;
        this.fixedVersion.remove(fixedVersion);
        fixedVersion.removeTicket(VersionRole.FIXED, this);
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
