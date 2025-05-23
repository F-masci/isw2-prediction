package it.isw2.prediction.factory;

import it.isw2.prediction.model.Commit;
import it.isw2.prediction.model.Version;
import it.isw2.prediction.repository.VersionRepository;
import org.eclipse.jgit.revwalk.RevCommit;

public class CommitFactory {

    private static CommitFactory instance = null;

    public static CommitFactory getInstance() {
        if (instance == null) instance = new CommitFactory();
        return instance;
    }

    private CommitFactory() {}

    public Commit createCommit(RevCommit revCommit) {

        Commit commit = new Commit(revCommit);

        VersionRepository versionRepository = VersionRepositoryFactory.getInstance().getVersionRepository();
        Version version = versionRepository.retrieveNextVersionByDate(commit.getDate());
        if (version != null) commit.setVersion(version);

        return commit;

    }

}
