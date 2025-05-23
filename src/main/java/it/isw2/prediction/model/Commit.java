package it.isw2.prediction.model;

import it.isw2.prediction.exception.ticket.TicketRetrievalException;
import it.isw2.prediction.factory.MethodRepositoryFactory;
import it.isw2.prediction.factory.TicketRepositoryFactory;
import it.isw2.prediction.repository.MethodRepository;
import it.isw2.prediction.repository.TicketRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Commit {

    private final RevCommit revCommit;

    private final Date commitDate;

    private Version version;
    private List<Ticket> linkedTickets = null;

    private List<Method> modifiedMethods = null;

    public Commit(RevCommit revCommit) {
        this.revCommit = revCommit;
        this.commitDate = new Date(revCommit.getCommitTime() * 1000L);
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
        return commitDate;
    }


    public RevCommit getRevCommit() {
        return revCommit;
    }

    public RevTree getTree() {
        return revCommit.getTree();
    }

    public RevCommit getParent() {
        return revCommit.getParentCount() > 0 ? revCommit.getParent(0) : null;
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

    /* --- MODIFIED METHODS --- */

    public List<Method> getModifiedMethods() {
        lazyLoadModifiedMethods();
        return modifiedMethods;
    }

    public boolean hasModifiedMethod(Method method) {
        lazyLoadModifiedMethods();
        return modifiedMethods.contains(method);
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

    private void lazyLoadModifiedMethods() {
        if (modifiedMethods != null) return;
        MethodRepository methodRepository = MethodRepositoryFactory.getInstance().getMethodRepository();
        modifiedMethods = methodRepository.retrieveModifiedMethods(this);
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

