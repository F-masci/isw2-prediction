package it.isw2.prediction.model;

import it.isw2.prediction.VersionRole;
import it.isw2.prediction.factory.CommitRepositoryFactory;
import it.isw2.prediction.repository.CommitRepository;

import java.util.Date;
import java.util.ArrayList;
import java.util.List;

public class Ticket {

    private final int id;
    private final String key;

    private final Date updateDate;
    private final Date resolutionDate;
    private final Date creationDate;

    private Version affectedVersion;
    private Version openingVersion;
    private Version fixedVersion;
    private boolean isProportionalVersion;

    private List<Commit> commits = null;

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

    public void setAffectedVersion(Version version, boolean isProportionalVersion) {
        if (affectedVersion != null && affectedVersion.equals(version)) return;
        affectedVersion = version;
        this.isProportionalVersion = isProportionalVersion;
    }

    public Version getOpeningVersion() {
        return openingVersion;
    }

    public void setOpeningVersion(Version version) {
        if (openingVersion != null && openingVersion.equals(version)) return;
        openingVersion = version;
    }

    public Version getFixedVersions() {
        return fixedVersion;
    }

    public void setFixedVersion(Version version) {
        if (fixedVersion != null && fixedVersion.equals(version)) return;
        fixedVersion = version;
    }

    public boolean isProportionalVersion() {
        return isProportionalVersion;
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

    public void lazyLoadCommits() {
        if (commits != null) return;
        commits = new ArrayList<>();
        CommitRepository commitRepository = CommitRepositoryFactory.getInstance().getCommitRepository();
        List<Commit> allCommits = commitRepository.retrieveCommits();
        for (Commit commit : allCommits) {
            if (commit.getShortMessage().contains(getKey())) this.commits.add(commit);
        }
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
