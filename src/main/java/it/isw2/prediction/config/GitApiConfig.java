package it.isw2.prediction.config;

import it.isw2.prediction.Project;

public class GitApiConfig {

    private static final String PROJECTS_PATH = "projects";

    private GitApiConfig() {}

    public static String getProjectsPath() {
        return PROJECTS_PATH;
    }
    public static String getProjectsPath(Project project) {
        return PROJECTS_PATH + "/" + project.getFolder();
    }

}
