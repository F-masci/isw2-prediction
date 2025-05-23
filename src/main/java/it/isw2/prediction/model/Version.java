package it.isw2.prediction.model;

import it.isw2.prediction.VersionRole;
import it.isw2.prediction.exception.ticket.TicketRetrievalException;
import it.isw2.prediction.factory.CommitRepositoryFactory;
import it.isw2.prediction.factory.TicketRepositoryFactory;
import it.isw2.prediction.repository.CommitRepository;
import it.isw2.prediction.repository.TicketRepository;

import java.util.*;

public class Version {

    private int id;
    private String name;
    private Date releaseDate;

    private EnumMap<VersionRole, List<Ticket>> linkedTickets = null;

    private List<Commit> commits = new ArrayList<>();

    public Version(int id, String name, Date releaseDate) {
        this.id = id;
        this.name = name;
        this.releaseDate = releaseDate;
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

    public boolean isLinkedToTicket(Ticket ticket) throws TicketRetrievalException {
        lazyLoadTickets();
        for (VersionRole role : VersionRole.values())
            if(this.linkedTickets.get(role).contains(ticket)) return true;
        return false;
    }

    public boolean isLinkedToTicket(VersionRole versionRole, Ticket ticket) throws TicketRetrievalException {
        lazyLoadTickets();
        return linkedTickets.get(versionRole).contains(ticket);
    }

    private void addTicket(VersionRole versionRole, Ticket ticket) {
        if (linkedTickets.get(versionRole).contains(ticket)) return;
        linkedTickets.get(versionRole).add(ticket);
    }

    /* --- COMMITS --- */

    public List<Commit> getCommits() {
        lazyLoadCommits();
        return commits;
    }

    public boolean hasCommit(Commit commit) {
        lazyLoadCommits();
        return commits.contains(commit);
    }

    /* --- LAZY LOAD --- */

    private void lazyLoadTickets() throws TicketRetrievalException {
        if (linkedTickets != null) return;
        linkedTickets = new EnumMap<>(VersionRole.class);
        TicketRepository ticketRepository = TicketRepositoryFactory.getInstance().getTicketRepository();
        List<Ticket> tickets = ticketRepository.retrieveTickets();
        for(Ticket ticket : tickets) {
            if(this.equals(ticket.getFixedVersions())) this.addTicket(VersionRole.FIXED, ticket);
            if(this.equals(ticket.getAffectedVersion())) this.addTicket(VersionRole.AFFECTED, ticket);
            if(this.equals(ticket.getOpeningVersion())) this.addTicket(VersionRole.OPENING, ticket);
        }
    }

    public void lazyLoadCommits() {
        if (commits != null) return;
        this.commits = new ArrayList<>();
        CommitRepository commitRepository = CommitRepositoryFactory.getInstance().getCommitRepository();
        List<Commit> allCommits = commitRepository.retrieveCommits();
        for (Commit commit : allCommits) {
            if (this.equals(commit.getVersion())) this.commits.add(commit);
        }
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
