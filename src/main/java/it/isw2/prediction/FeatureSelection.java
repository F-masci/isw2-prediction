package it.isw2.prediction;

public enum FeatureSelection {

    NONE("none"),
    FORWARD("forward"),
    BACKWARD("backward"),
    INFO_GAIN("info-gain");

    private final String name;

    FeatureSelection(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static FeatureSelection getByConfig(String config) {
        return switch (config) {
            case "none" -> NONE;
            case "forward" -> FORWARD;
            case "backward" -> BACKWARD;
            case "info-gain" -> INFO_GAIN;
            default -> throw new IllegalArgumentException("Invalid feature selection method: " + config);
        };
    }

}
