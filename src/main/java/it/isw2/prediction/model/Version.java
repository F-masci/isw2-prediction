package it.isw2.prediction.model;

import it.isw2.prediction.VersionRole;

import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Version {

    private int id;
    private String name;
    private Date releaseDate;

    private HashMap<VersionRole, List<Ticket>> linkedTickets = new HashMap<>();

    private List<Commit> commits = new ArrayList<>();

    public Version(int id, String name, Date releaseDate) {
        this.id = id;
        this.name = name;
        this.releaseDate = releaseDate;

        this.linkedTickets.put(VersionRole.AFFECTED, new ArrayList<>());
        this.linkedTickets.put(VersionRole.OPENED, new ArrayList<>());
        this.linkedTickets.put(VersionRole.FIXED, new ArrayList<>());

    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Date getReleaseDate() {
        return releaseDate;
    }

    /* --- TICKETS --- */

    public boolean isLinkedToTicket(Ticket ticket) {
        for (VersionRole role : VersionRole.values())
            if(this.linkedTickets.get(role).contains(ticket)) return true;
        return false;
    }

    public boolean isLinkedToTicket(VersionRole versionRole, Ticket ticket) {
        return linkedTickets.get(versionRole).contains(ticket);
    }

    public boolean addTicket(VersionRole versionRole, Ticket ticket) {
        if (!linkedTickets.get(versionRole).contains(ticket)) {
            return linkedTickets.get(versionRole).add(ticket);
        }
        return false;
    }

    public boolean removeTicket(VersionRole versionRole, Ticket ticket) {
        return linkedTickets.get(versionRole).remove(ticket);
    }

    /* --- COMMITS --- */

    public List<Commit> getCommits() {
        return commits;
    }

    public boolean hasCommit(Commit commit) {
        return commits.contains(commit);
    }

    public void addCommit(Commit commit) {
        if (commits.contains(commit)) return;
        commits.add(commit);
        commit.setVersion(this);
    }

    /* --- FORMATTER --- */

    @Override
    public String toString() {
        return "Version (" + id + ") => " + name + ": " + releaseDate;
    }

    /* --- EQUALS & HASHCODE --- */

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Version version = (Version) obj;
        return id == version.id && name.equals(version.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

}
