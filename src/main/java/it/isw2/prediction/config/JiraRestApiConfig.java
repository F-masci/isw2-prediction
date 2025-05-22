package it.isw2.prediction.config;

public class JiraRestApiConfig {

    private static final String BASE_URL = "https://issues.apache.org/jira/rest/api/latest";

    private JiraRestApiConfig() {}

    public static String getBaseUrl() {
        return BASE_URL;
    }

}
