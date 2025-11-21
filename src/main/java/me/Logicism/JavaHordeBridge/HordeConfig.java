package me.Logicism.JavaHordeBridge;

import java.util.List;

public class HordeConfig {

    private String clusterURL;

    private String backupClusterURL;

    private String workerName;

    private String workerType;

    private String kaiURL;

    private String apiKey;

    private List<String> priorityUsernames;

    private List<String> interrogationForms;

    public String getWorkerName() {
        return workerName;
    }

    public String getWorkerType() {
        return workerType;
    }

    public String getKaiURL() {
        return kaiURL;
    }

    public String getClusterURL() {
        return clusterURL;
    }

    public String getBackupClusterURL() {
        return backupClusterURL;
    }

    public String getApiKey() {
        return apiKey;
    }

    public List<String> getPriorityUsernames() {
        return priorityUsernames;
    }

    public List<String> getInterrogationForms() {
        return interrogationForms;
    }
}
