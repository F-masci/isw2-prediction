package it.isw2.prediction.utils;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

public class Utils {

    private Utils () {}

    public static String readBlobAsString(Repository repo, ObjectId blobId) throws IOException {
        if (blobId == null || blobId.equals(ObjectId.zeroId())) return "";
        try (ObjectReader reader = repo.newObjectReader()) {
            ObjectLoader loader = reader.open(blobId);
            return new String(loader.getBytes(), "UTF-8");
        }
    }

}
