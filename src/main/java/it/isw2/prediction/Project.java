package it.isw2.prediction;

public enum Project {

    BOOKKEPER("BOOKKEEPER", 12311293, "bookkeeper"),
    OPENJPA("OPENJPA", 12310351, "openjpa"),;

    private final String key;
    private final int id;
    private final String folder;

    Project(String key, int id, String folder) {
        this.key = key;
        this.id = id;
        this.folder = folder;
    }

    public static Project getByKey(String key) {
        for (Project p : Project.values()) if (p.key.equals(key)) return p;
        return null;
    }

    public static Project getById(int id) {
        for (Project p : Project.values()) if (p.id == id) return p;
        return null;
    }

    public String getKey() {
        return key;
    }

    public int getId() {
        return id;
    }

    public String getFolder() {
        return folder;
    }

}
