package it.isw2.prediction.model;

import it.isw2.prediction.factory.CommitRepositoryFactory;
import it.isw2.prediction.factory.VersionRepositoryFactory;
import it.isw2.prediction.repository.CommitRepository;
import it.isw2.prediction.repository.VersionRepository;

import java.util.Comparator;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Ticket {

    private final int id;
    private final String key;

    private final Date updateDate;
    private final Date resolutionDate;
    private final Date creationDate;

    private final List<Version> affectedVersions = new ArrayList<>();
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

    public boolean isVersionAffected(Version version) {
        return affectedVersions.contains(version);
    }

    public List<Version> getAffectedVersions() {
        return affectedVersions;
    }

    public Version getBaseAffectedVersion() {
        if (affectedVersions.isEmpty()) return null;
        return affectedVersions.getFirst();
    }

    public void setBaseAffectedVersion(Version baseVersion, boolean isProportionalVersion) {
        if (fixedVersion == null || baseVersion == null) return;

        this.isProportionalVersion = isProportionalVersion;

        Date baseDate = baseVersion.getReleaseDate();
        Date fixedDate = fixedVersion.getReleaseDate();

        VersionRepository versionRepository = VersionRepositoryFactory.getInstance().getVersionRepository();
        List<Version> versions = versionRepository.retrieveVersionsBetweenDates(baseDate, fixedDate).stream()
                .sorted(Comparator.comparing(Version::getReleaseDate))
                .toList();

        for (Version v : versions) {
            // Controlla se la versione è già presente e se non è la versione di chiusura
            if(!isVersionAffected(v) && !v.equals(fixedVersion)) affectedVersions.add(v);
        }
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

    public Commit getLastCommit() {
        lazyLoadCommits();
        if (commits.isEmpty()) return null;
        return commits.stream().max(Comparator.comparing(Commit::getDate)).orElse(null);
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
