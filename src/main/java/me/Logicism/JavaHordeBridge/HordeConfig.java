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

    private boolean enablePromptJobs;

    private boolean enableChatCompletions;

    private boolean disableKoboldClientVerifier;

    private String model;

    private int maxTokens;

    private int maxTokenLength;

    private List<String> chatFeatures;

    private List<String> imageTransports;

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

    public boolean isEnablePromptJobs() {
        return enablePromptJobs;
    }

    public boolean isEnableChatCompletions() {
        return enableChatCompletions;
    }

    public boolean isDisableKoboldClientVerifier() {
        return disableKoboldClientVerifier;
    }

    public String getModel() {
        return model;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public int getMaxTokenLength() {
        return maxTokenLength;
    }

    public List<String> getChatFeatures() {
        return chatFeatures;
    }

    public List<String> getImageTransports() {
        return imageTransports;
    }
}
