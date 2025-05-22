package it.isw2.prediction;

public enum Project {

    BOOKKEPER("BOOKKEPER", 12311293),
    OPENJPA("OPENJPA", 12310351);

    private final String key;
    private final int id;

    Project(String key, int id) {
        this.key = key;
        this.id = id;
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

}
