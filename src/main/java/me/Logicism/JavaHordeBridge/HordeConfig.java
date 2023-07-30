package me.Logicism.JavaHordeBridge;

import java.util.List;

public class HordeConfig {

    private String clusterURL;

    private String workerName;

    private String kaiURL;

    private String apiKey;

    private List<String> priorityUsernames;

    public String getWorkerName() {
        return workerName;
    }

    public String getKaiURL() {
        return kaiURL;
    }

    public String getClusterURL() {
        return clusterURL;
    }

    public String getApiKey() {
        return apiKey;
    }

    public List<String> getPriorityUsernames() {
        return priorityUsernames;
    }
}
