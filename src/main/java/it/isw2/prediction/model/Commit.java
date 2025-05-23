package it.isw2.prediction.model;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import java.time.Instant;
import java.util.Date;
import java.util.List;

public class Commit {

    private final RevCommit commit;

    private Version version;
    
    public Commit(RevCommit revCommit) {
        this.commit = revCommit;
    }

    public String getId() {
        return commit.getId().getName();
    }

    public String getMessage() {
        return commit.getFullMessage();
    }

    public String getShortMessage() {
        return commit.getShortMessage();
    }

    public String getAuthorName() {
        return commit.getAuthorIdent().getName();
    }

    public String getAuthorEmail() {
        return commit.getAuthorIdent().getEmailAddress();
    }

    public Date getDate() {
        return new Date(commit.getCommitTime());
    }
    
    
    public Version getVersion() {
        return version;
    }
    
    public void setVersion(Version version) {
        if(this.version != null && this.version.equals(version)) return;
        this.version = version;
        version.addCommit(this);
    }

}
