package it.isw2.prediction.builder;

import it.isw2.prediction.factory.VersionRepositoryFactory;
import it.isw2.prediction.model.Commit;
import it.isw2.prediction.model.Ticket;
import it.isw2.prediction.model.Version;
import it.isw2.prediction.repository.VersionRepository;

import java.util.Date;
import java.util.List;

public class TicketBuilder {

    private final Ticket ticket;

    private Version affectedVersion = null;
    private Version openingVersion = null;
    private Version fixedVersion = null;
    private boolean isProportionalVersion = false;

    private final VersionRepository versionRepository;

    public TicketBuilder (int id, String key, Date creationDate, Date resolutionDate, Date updateDate) {
        this.ticket = new Ticket(id, key, creationDate, resolutionDate, updateDate);
        this.versionRepository = VersionRepositoryFactory.getInstance().getVersionRepository();

        // Calcola la versione di apertura in base alla data di creazione del ticket
        openingVersion = versionRepository.retrievePreviousVersionByDate(ticket.getCreationDate());

        // Calcola la versione di chiusura in base alla data di chiusura dell'ultimo commit
        List<Commit> commits = ticket.getCommits();
        if (commits != null && !commits.isEmpty()) {
            Commit lastCommit = commits.stream()
                .max((c1, c2) -> c1.getDate().compareTo(c2.getDate()))
                .orElse(null);
            if (lastCommit != null)
                fixedVersion = versionRepository.retrieveNextVersionByDate(lastCommit.getDate());
        }

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

    public TicketBuilder withFixedVersion(int fixedVersionId) {
        this.fixedVersion = versionRepository.retrieveVersionById(fixedVersionId);
        return this;
    }

    public Ticket build() {
        if(this.openingVersion != null) ticket.setOpeningVersion(openingVersion);
        if(this.fixedVersion != null) ticket.setFixedVersion(fixedVersion);
        if(this.affectedVersion != null) ticket.setBaseAffectedVersion(affectedVersion, isProportionalVersion);
        return ticket;
    }

}
