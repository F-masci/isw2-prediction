package it.isw2.prediction.builder;

import it.isw2.prediction.model.Ticket;
import it.isw2.prediction.model.Version;

import java.time.LocalDateTime;

public class TicketBuilder {

    private final Ticket ticket;

    private Version affectedVersion = null;
    private Version openingVersion = null;
    private Version fixedVersion = null;
    private boolean isProportionalVersion = false;

    public TicketBuilder (int id, String key, LocalDateTime resolutionDate, LocalDateTime creationDate) {
        this.ticket = new Ticket(id, key, resolutionDate, creationDate);
    }

    public TicketBuilder withAffectedVersion(Version affectedVersion) {
        this.affectedVersion = affectedVersion;
        this.isProportionalVersion = false;
        return this;
    }

    public TicketBuilder withOpeningVersion(Version openingVersion) {
        this.openingVersion = openingVersion;
        return this;
    }

    public TicketBuilder withFixedVersion(Version fixedVersion) {
        this.fixedVersion = fixedVersion;
        return this;
    }

    public Ticket build() {
        if(this.affectedVersion != null) ticket.setAffectedVersion(affectedVersion, isProportionalVersion);
        if(this.openingVersion != null) ticket.setOpeningVersion(openingVersion);
        if(this.fixedVersion != null) ticket.setFixedVersion(fixedVersion);
        return ticket;
    }

}
