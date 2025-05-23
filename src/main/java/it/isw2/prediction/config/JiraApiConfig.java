package it.isw2.prediction.config;

public class JiraApiConfig {

    private static final String BASE_URL = "https://issues.apache.org/jira/rest/api/latest";

    private JiraApiConfig() {}

    public static String getBaseUrl() {
        return BASE_URL;
    }

}
