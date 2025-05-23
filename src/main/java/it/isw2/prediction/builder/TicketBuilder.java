package it.isw2.prediction.builder;

import it.isw2.prediction.model.Ticket;
import it.isw2.prediction.model.Version;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TicketBuilder {

    private final Ticket ticket;

    private Version affectedVersion = null;
    private Version openingVersion = null;
    private List<Version> fixedVersion = new ArrayList<>();
    private boolean isProportionalVersion = false;

    public TicketBuilder (int id, String key, LocalDateTime creationDate, LocalDateTime resolutionDate, LocalDateTime updateDate) {
        this.ticket = new Ticket(id, key, creationDate, resolutionDate, updateDate);
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

    public TicketBuilder addFixedVersion(Version fixedVersion) {
        this.fixedVersion.add(fixedVersion);
        return this;
    }

    public Ticket build() {
        if(this.affectedVersion != null) ticket.setAffectedVersion(affectedVersion, isProportionalVersion);
        if(this.openingVersion != null) ticket.setOpeningVersion(openingVersion);
        for (Version version: this.fixedVersion) ticket.addFixedVersion(version);
        return ticket;
    }

}
