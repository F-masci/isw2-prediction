package it.isw2.prediction.model;

import it.isw2.prediction.exception.ticket.TicketRetrievalException;
import it.isw2.prediction.factory.TicketRepositoryFactory;
import it.isw2.prediction.repository.TicketRepository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.Date;
import java.util.List;

public class Commit {

    private final RevCommit revCommit;

    private Version version;
    private List<Ticket> linkedTickets = null;
    
    public Commit(RevCommit revCommit) {
        this.revCommit = revCommit;
    }

    public String getId() {
        return revCommit.getId().getName();
    }

    public String getMessage() {
        return revCommit.getFullMessage();
    }

    public String getShortMessage() {
        return revCommit.getShortMessage();
    }

    public String getAuthorName() {
        return revCommit.getAuthorIdent().getName();
    }

    public String getAuthorEmail() {
        return revCommit.getAuthorIdent().getEmailAddress();
    }

    public Date getDate() {
        return new Date(revCommit.getCommitTime());
    }
    
    /* --- VERSION --- */

    public Version getVersion() {
        return version;
    }
    
    public void setVersion(Version version) {
        if(this.version != null && this.version.equals(version)) return;
        this.version = version;
    }

    /* --- TICKETS --- */

    public List<Ticket> getLinkedTickets() throws TicketRetrievalException {
        lazyLoadTickets();
        return linkedTickets;
    }

    public boolean isLinkedToTicket(Ticket ticket) throws TicketRetrievalException {
        lazyLoadTickets();
        return linkedTickets.contains(ticket);
    }

    /* --- LAZY LOADING --- */

    private void lazyLoadTickets() throws TicketRetrievalException {
        if (linkedTickets != null) return;
        TicketRepository ticketRepository = TicketRepositoryFactory.getInstance().getTicketRepository();
        List<Ticket> tickets = ticketRepository.retrieveTickets();
        for (Ticket ticket : tickets) {
            // Collego il commit al ticket se il messaggio contiene il ticket
            if (getShortMessage().contains(ticket.getKey())) linkedTickets.add(ticket);
        }
    }

    /* --- FORMATTER --- */

    @Override
    public String toString() {
        return "Commit (" + getId() + ") => " + getAuthorName() + ": " + getDate() + " - " + getShortMessage();
    }

    /* --- EQUALS & HASHCODE --- */

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Commit commit = (Commit) obj;
        return getId().equals(commit.getId());
    }

    @Override
    public int hashCode() {
        return revCommit.hashCode();
    }

}
