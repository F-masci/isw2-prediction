package it.isw2.prediction.builder;

import it.isw2.prediction.factory.VersionRepositoryFactory;
import it.isw2.prediction.model.Ticket;
import it.isw2.prediction.model.Version;
import it.isw2.prediction.repository.VersionRepository;

import java.util.Date;
import java.util.ArrayList;
import java.util.List;

public class TicketBuilder {

    private final Ticket ticket;

    private Version affectedVersion = null;
    private Version openingVersion = null;
    private List<Version> fixedVersion = new ArrayList<>();
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

    public TicketBuilder addFixedVersion(int fixedVersionId) {
        this.fixedVersion.add(versionRepository.retrieveVersionById(fixedVersionId));
        return this;
    }

    public Ticket build() {
        if(this.affectedVersion != null) ticket.setAffectedVersion(affectedVersion, isProportionalVersion);
        if(this.openingVersion != null) ticket.setOpeningVersion(openingVersion);
        for (Version version: this.fixedVersion) ticket.addFixedVersion(version);
        return ticket;
    }

}
