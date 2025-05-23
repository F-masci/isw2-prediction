package it.isw2.prediction.builder;

import it.isw2.prediction.factory.VersionRepositoryFactory;
import it.isw2.prediction.model.Ticket;
import it.isw2.prediction.model.Version;
import it.isw2.prediction.repository.VersionRepository;

import java.util.Date;

public class TicketBuilder {

    private final Ticket ticket;

    private Version affectedVersion = null;
    private Version openingVersion = null;
    private boolean isProportionalVersion = false;

    private final VersionRepository versionRepository;

    public TicketBuilder (int id, String key, Date creationDate, Date resolutionDate, Date updateDate) {
        this.ticket = new Ticket(id, key, creationDate, resolutionDate, updateDate);
        this.versionRepository = VersionRepositoryFactory.getInstance().getVersionRepository();
        openingVersion = versionRepository.retrievePreviousVersionByDate(ticket.getCreationDate());
    }

    public TicketBuilder withAffectedVersion(int affectedVersionId) {
        this.affectedVersion = versionRepository.retrieveVersionById(affectedVersionId);
        this.isProportionalVersion = false;
        return this;
    }

    public TicketBuilder withOpeningVersion(int openingVersionId) {
        this.openingVersion = versionRepository.retrieveVersionById(openingVersionId);
        return this;
    }

    public Ticket build() {
        if(this.openingVersion != null) ticket.setOpeningVersion(openingVersion);
        if(this.affectedVersion != null) ticket.setAffectedVersion(affectedVersion, isProportionalVersion);
        return ticket;
    }

}
