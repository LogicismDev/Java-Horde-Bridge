package me.Logicism.JavaHordeBridge.runnables;

import me.Logicism.JavaHordeBridge.HordeBridge;
import me.Logicism.JavaHordeBridge.network.BrowserClient;
import me.Logicism.JavaHordeBridge.network.BrowserData;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TextHordeRunnable implements Runnable {

    // Threads 1-2 hand filler jobs to the rest so real traffic keeps two slots.
    private static final java.util.concurrent.BlockingQueue<JSONObject> FILLER_JOBS =
            new java.util.concurrent.LinkedBlockingQueue<>();
    private static final java.util.concurrent.atomic.AtomicInteger ACTIVE =
            new java.util.concurrent.atomic.AtomicInteger();

    private static boolean isFiller(JSONObject popObject) {
        JSONObject payload = popObject.optJSONObject("payload");
        return payload != null && payload.optString("prompt", "").stripTrailing().endsWith("(ignore)");
    }

    // Horde only accepts these four; the shim reports "error" for a failed job.
    private static String safeFinishReason(JSONObject generation) {
        String fr = generation.optString("finish_reason", "stop");
        return switch (fr) {
            case "stop", "length", "content_filter", "tool_calls", "function_call" -> fr;
            default -> "stop";
        };
    }


    // Build the submit body: chat jobs submit a message, prompt jobs a
    // generation. Sending a chat body for a text job is rejected by Horde.
    private JSONObject buildSubmit(JSONObject generation, JSONObject payloadObject, String modelId) {
        boolean chatJob = payloadObject.has("messages") || payloadObject.has("messages_url");
        JSONObject submitObject = new JSONObject().put("id", currentId).put("model_id", modelId);
        if (generation == null) {
            if (chatJob) submitObject.put("finish_reason", "stop");
            return submitObject.put("state", "faulted").put("fault_reason", "generation_error");
        }
        submitObject.put("state", "ok");
        if (chatJob) {
            JSONObject messageObject = new JSONObject().put("role", "assistant");
            String finishReason = generation.optString("finish_reason", "stop");
            if (finishReason.equals("tool_calls") || finishReason.equals("function_call")) {
                messageObject.put("content", JSONObject.NULL);
                submitObject.put("finish_reason", "tool_calls");
                if (generation.has("tool_calls")) messageObject.put("tool_calls", generation.getJSONArray("tool_calls"));
            } else {
                submitObject.put("finish_reason", safeFinishReason(generation));
                messageObject.put("content", generation.getString("content"));
                if (generation.has("reasoning_content")) {
                    messageObject.put("reasoning_content", generation.getString("reasoning_content"));
                }
            }
            submitObject.put("message", messageObject);
        } else {
            submitObject.put("generation", generation.getString("content"));
        }
        JSONObject usage = generation.optJSONObject("usage", new JSONObject());
        for (String key : List.of("prompt_tokens", "completion_tokens")) {
            int n = usage.optInt(key, -1);
            if (n != -1) submitObject.put(key, n);
        }
        int rtokens = usage.optInt("completion_tokens_details", -1);
        if (rtokens != -1) submitObject.put("reasoning_tokens", rtokens);
        return submitObject;
    }

    private final HordeBridge bridge;
    private final int threadID;
    private final String kaiURL;
    private final String kaiName;
    private final String apiKey;
    private final String clusterURL;
    private final String backupClusterURL;
    private final String[] priorityUsernames;
    private final boolean enableChatCompletions;
    private final boolean enablePromptJobs;
    private final boolean disableKoboldClientVerifier;
    private final String model;
    private final int maxTokens;
    private final int maxTokenLength;
    private final String[] chatFeatures;
    private final String[] imageTransports;
    private final int threads;

    private boolean isRunning = true;
    private int failedRequestsCount = 0;
    private boolean useBackupCluster = false;
    private int backupClusterGenCount = 0;
    private Long lastValidated = null;
    private JSONObject clientData = null;
    private String currentId = "";
    private String softprompt = "";


    // One popped job (or the no-job case). Runs off the calling thread.
    private void processJob(JSONObject popObject, JSONObject clientData) throws IOException, URISyntaxException, InterruptedException, JSONException {
        ACTIVE.incrementAndGet();
        try {
            BrowserData bd = null;
            Map<String, String> headers = new HashMap<>();
            headers.put("apikey", apiKey);
            headers.put("Content-Type", "application/json");
            headers.put("User-Agent", "Java 11 / Java Horde Bridge " + HordeBridge.BRIDGE_VERSION);
            if (!popObject.has("id") || popObject.isNull("id")) {
                bridge.getLogger().debug((threads > 1 ? "[Thread " + threadID + "] " : "") + "There are no generations popped to do. Skipped: " + popObject.optJSONObject("skipped"));
                try {
                    TimeUnit.SECONDS.sleep(HordeBridge.INTERVAL);
                } catch (InterruptedException ignored) {
                }
                return;
            }
                                        currentId = popObject.getString("id");

                                        JSONObject payloadObject = popObject.getJSONObject("payload");

                                        bridge.getLogger().info((threads > 1 ? "[Thread " + threadID + "] " : "") + "Received a job " + currentId + " with " + payloadObject.optInt("max_length", 80) + " tokens and " + payloadObject.optInt("max_context_length", 1024) + " max context length");

                                        JSONObject generation = bridge.getGenerator().startChatGeneration(payloadObject, (useBackupCluster ? backupClusterURL : clusterURL), clientData.getJSONArray("models").getString(0), apiKey, currentId);
                                        JSONObject submitObject = buildSubmit(generation, payloadObject, clientData.getJSONArray("models").getString(0));

                                        bd = BrowserClient.executePOSTRequestString(new URL((useBackupCluster ? backupClusterURL : clusterURL) + "/api/v2/generate/text/submit"), submitObject.toString(), headers);

                                        if (bd.getResponseCode() == 200) {
                                            if (generation == null) {
                                                failedRequestsCount++;

                                                bridge.getLogger().error((threads > 1 ? "[Thread " + threadID + "] " : "") + "Aborting generation " + currentId + " due to exceeded 5 retry counts");
                                            } else {
                                                JSONObject rewardObject = new JSONObject(bd.getResponseString());

                                                failedRequestsCount = 0;
                                                if (useBackupCluster) {
                                                    backupClusterGenCount++;
                                                }

                                                bridge.getLogger().info((threads > 1 ? "[Thread " + threadID + "] " : "") + "Submitted generation " + currentId + " for the reward of " + rewardObject.getDouble("reward") + " kudos");
                                            }
                                        } else if (bd.getResponseCode() == 404) {
                                            bridge.getLogger().warn((threads > 1 ? "[Thread " + threadID + "] " : "") + "Generation " + currentId + " is stale while we working on it!");
                                        } else {
                                            bridge.getLogger().warn((threads > 1 ? "[Thread " + threadID + "] " : "") + "Submit rejected " + bd.getResponseCode() + " for " + currentId + ": " + bd.getResponseString());
                                        }

                                        currentId = "";
        } finally {
            ACTIVE.decrementAndGet();
        }
    }
    public TextHordeRunnable(HordeBridge bridge, int threadID, String kaiURL, String kaiName, String apiKey, String clusterURL, String backupClusterURL, String[] priorityUsernames, boolean enableChatCompletions, boolean enablePromptJobs, boolean disableKoboldClientVerifier, String model, int maxTokens, int maxTokenLength, String[] chatFeatures, String[] imageTransports, int threads) {
        this.bridge = bridge;
        this.threadID = threadID;
        this.kaiURL = kaiURL;
        this.kaiName = kaiName;
        this.apiKey = apiKey;
        this.backupClusterURL = backupClusterURL;
        this.clusterURL = clusterURL;
        this.priorityUsernames = priorityUsernames;
        this.enableChatCompletions = enableChatCompletions;
        this.enablePromptJobs = enablePromptJobs;
        this.disableKoboldClientVerifier = disableKoboldClientVerifier;
        this.model = model;
        this.maxTokens = maxTokens;
        this.maxTokenLength = maxTokenLength;
        this.chatFeatures = chatFeatures;
        this.imageTransports = imageTransports;
        this.threads = threads;
    }

    @Override
    public void run() {
        while (isRunning) {
            if (failedRequestsCount > 3) {
                bridge.getLogger().error("Exceeded 3 failed requests in a row, shutting down bridge in 5 seconds!");

                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ignored) {
                }

                System.exit(0);
            }

            if (disableKoboldClientVerifier) {
                if (clientData == null && lastValidated == null ||System.currentTimeMillis() - lastValidated >= 30000) {
                    try {
                        bridge.getLogger().debug((threads > 1 ? "[Thread " + threadID + "] " : "") + "Retrieving OpenAI Worker Settings...");
                        clientData = bridge.getGenerator().validateOpenAIClient(kaiName, priorityUsernames, enableChatCompletions, enablePromptJobs, model, maxTokens, maxTokenLength, chatFeatures, imageTransports, threads);
                        lastValidated = System.currentTimeMillis();

                        failedRequestsCount = 0;
                    } catch (IOException | URISyntaxException | InterruptedException e) {
                        failedRequestsCount++;

                        bridge.getLogger().error((threads > 1 ? "[Thread " + threadID + "] " : "") + "Cannot connect to OpenAI Client! ("
                                + e.getLocalizedMessage() + ") Trying again in 5 seconds!");

                        try {
                            TimeUnit.SECONDS.sleep(5);
                        } catch (InterruptedException ignored) {
                        }
                    } catch (JSONException e) {
                        failedRequestsCount++;

                        bridge.getLogger().error((threads > 1 ? "[Thread " + threadID + "] " : "") + "Client is up but has invalid response! ("
                                + e.getLocalizedMessage() + ") Trying again in 5 seconds!");

                        try {
                            TimeUnit.SECONDS.sleep(5);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }

                if (clientData != null) {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("apikey", apiKey);
                    headers.put("Content-Type", "application/json");
                    headers.put("User-Agent", "Java 11 / Java Horde Bridge " + HordeBridge.BRIDGE_VERSION);
                    try {
                        BrowserData bd = null;
                        if (threadID > 2) {
                            JSONObject queued = FILLER_JOBS.poll();
                            if (queued != null) {
                                bridge.getLogger().debug("[Thread " + threadID + "] taking queued filler " + queued.optString("id"));
                                processJob(queued, clientData);
                                continue;
                            }
                        }


                        if (useBackupCluster && backupClusterGenCount > 10) {
                            backupClusterGenCount = 0;
                            useBackupCluster = false;
                        }

                        if (useBackupCluster && backupClusterGenCount < 10) {
                            bd = BrowserClient.executePOSTRequestString(new URL(backupClusterURL + "/api/v2/generate/text/pop"), clientData.toString(), headers);
                        } else {
                            try {
                                bd = BrowserClient.executePOSTRequestString(new URL(clusterURL + "/api/v2/generate/text/pop"), clientData.toString(), headers);
                            } catch (IOException | JSONException e) {
                                bridge.getLogger().error((threads > 1 ? "[Thread " + threadID + "] " : "") + "Main Horde Cluster is down! (" + e.getLocalizedMessage() + ") Swapping to Backup Horde Cluster for the next 10 generations!");
                                useBackupCluster = true;
                            }
                        }

                        if (bd != null) {
                            JSONObject popObject = new JSONObject(bd.getResponseString());
                            if (bd.getResponseCode() == 200) {
                                if (threadID <= 2 && popObject.has("id") && !popObject.isNull("id")
                                        && isFiller(popObject) && ACTIVE.get() < threads - 2) {
                                    FILLER_JOBS.offer(popObject);
                                    bridge.getLogger().debug("[Thread " + threadID + "] queued filler " + popObject.optString("id"));
                                } else {
                                    processJob(popObject, clientData);
                                }
                            } else if (bd.getResponseCode() == 400) {
                                bridge.getLogger().debug((threads > 1 ? "[Thread " + threadID + "] " : "") + "Horde Cluster has a Validation Error! (" + popObject.getString("message") + ") Trying again in 5 seconds");

                                failedRequestsCount++;

                                try {
                                    TimeUnit.SECONDS.sleep(5);
                                } catch (InterruptedException ignored) {
                                }
                            } else if (bd.getResponseCode() == 401) {
                                bridge.getLogger().error((threads > 1 ? "[Thread " + threadID + "] " : "") + "Invalid API Key! (" + popObject.getString("message") + ") Please check if it is valid! Closing in 10 seconds.");

                                failedRequestsCount++;

                                try {
                                    TimeUnit.SECONDS.sleep(10);
                                } catch (InterruptedException ignored) {
                                }

                                System.exit(0);
                            } else if (bd.getResponseCode() == 403) {
                                bridge.getLogger().warn((threads > 1 ? "[Thread " + threadID + "] " : "") + "Access is Denied! (" + popObject.getString("message") + ") Trying again in 10 seconds");

                                failedRequestsCount++;

                                try {
                                    TimeUnit.SECONDS.sleep(10);
                                } catch (InterruptedException ignored) {
                                }
                            }
                        }
                    } catch (IOException | InterruptedException | URISyntaxException e) {
                        e.printStackTrace();
                        bridge.getLogger().error((threads > 1 ? "[Thread " + threadID + "] " : "") + "Cannot populate worker! (" + e.getLocalizedMessage() + ") Trying again in 10 seconds!");

                        failedRequestsCount++;

                        try {
                            TimeUnit.SECONDS.sleep(10);
                        } catch (InterruptedException ignored) {
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        bridge.getLogger().error((threads > 1 ? "[Thread " + threadID + "] " : "") + "Horde Cluster is up but has invalid response! (" + e.getLocalizedMessage() + ") Trying again in 5 seconds!");

                        failedRequestsCount++;

                        try {
                            TimeUnit.SECONDS.sleep(5);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            } else {
                if (clientData == null && lastValidated == null || System.currentTimeMillis() - lastValidated >= 30000) {
                    try {
                        bridge.getLogger().debug((threads > 1 ? "[Thread " + threadID + "] " : "") + "Retrieving KoboldAI Worker Settings...");
                        clientData = bridge.getGenerator().validateClient(kaiName, priorityUsernames, enableChatCompletions, enablePromptJobs, chatFeatures, imageTransports);
                        lastValidated = System.currentTimeMillis();

                        failedRequestsCount = 0;
                    } catch (IOException | URISyntaxException | InterruptedException e) {
                        failedRequestsCount++;

                        bridge.getLogger().error((threads > 1 ? "[Thread " + threadID + "] " : "") + "Cannot connect to KoboldAI Client! ("
                                + e.getLocalizedMessage() + ") Trying again in 5 seconds!");

                        try {
                            TimeUnit.SECONDS.sleep(5);
                        } catch (InterruptedException ignored) {
                        }
                    } catch (JSONException e) {
                        failedRequestsCount++;

                        bridge.getLogger().error((threads > 1 ? "[Thread " + threadID + "] " : "") + "Client is up but has invalid response! ("
                                + e.getLocalizedMessage() + ") Trying again in 5 seconds!");

                        try {
                            TimeUnit.SECONDS.sleep(5);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }

                if (clientData != null) {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("apikey", apiKey);
                    headers.put("Content-Type", "application/json");
                    headers.put("User-Agent", "Java 11 / Java Horde Bridge " + HordeBridge.BRIDGE_VERSION);
                    try {
                        BrowserData bd = null;

                        if (useBackupCluster && backupClusterGenCount > 10) {
                            backupClusterGenCount = 0;
                            useBackupCluster = false;
                        }

                        if (useBackupCluster && backupClusterGenCount < 10) {
                            bd = BrowserClient.executePOSTRequestString(new URL(backupClusterURL + "/api/v2/generate/text/pop"), clientData.toString(), headers);
                        } else {
                            try {
                                bd = BrowserClient.executePOSTRequestString(new URL(clusterURL + "/api/v2/generate/text/pop"), clientData.toString(), headers);
                            } catch (IOException | JSONException e) {
                                bridge.getLogger().error((threads > 1 ? "[Thread " + threadID + "] " : "") + "Main Horde Cluster is down! (" + e.getLocalizedMessage() + ") Swapping to Backup Horde Cluster for the next 10 generations!");
                                useBackupCluster = true;
                            }
                        }

                        if (bd != null) {
                            JSONObject popObject = new JSONObject(bd.getResponseString());
                            if (bd.getResponseCode() == 200) {
                                if (!popObject.isNull("id")) {
                                    currentId = popObject.getString("id");

                                    JSONObject payloadObject = popObject.getJSONObject("payload");
                                    payloadObject.put("quiet", true);

                                    bridge.getLogger().info((threads > 1 ? "[Thread " + threadID + "] " : "") + "Received a job " + currentId + " with " + payloadObject.optInt("max_length", 80) + " tokens and " + payloadObject.optInt("max_context_length", 1024) + " max context length");

                                    if (!softprompt.equals(popObject.optString("softprompt", ""))) {
                                        softprompt = popObject.optString("softprompt", "");

                                        bd = BrowserClient.executePUTRequest(new URL(kaiURL + "/api/latest/config/soft_prompt/"), new JSONObject().put("value", softprompt).toString(), new HashMap<>());

                                        try {
                                            TimeUnit.SECONDS.sleep(1);
                                        } catch (InterruptedException ignored) {
                                        }
                                    }

                                    if (popObject.optBoolean("chat") || payloadObject.has("messages") || payloadObject.has("messages_url")) {
                                        JSONObject generation = bridge.getGenerator().startChatGeneration(payloadObject, (useBackupCluster ? backupClusterURL : clusterURL), clientData.getJSONArray("models").getString(0), apiKey, currentId);
                                        JSONObject submitObject = buildSubmit(generation, payloadObject, clientData.getJSONArray("models").getString(0));

                                        bd = BrowserClient.executePOSTRequestString(new URL((useBackupCluster ? backupClusterURL : clusterURL) + "/api/v2/generate/text/submit"), submitObject.toString(), headers);

                                        if (bd.getResponseCode() == 200) {
                                            if (generation == null) {
                                                failedRequestsCount++;

                                                bridge.getLogger().error((threads > 1 ? "[Thread " + threadID + "] " : "") + "Aborting generation " + currentId + " due to exceeded 5 retry counts");
                                            } else {
                                                JSONObject rewardObject = new JSONObject(bd.getResponseString());

                                                failedRequestsCount = 0;
                                                if (useBackupCluster) {
                                                    backupClusterGenCount++;
                                                }

                                                bridge.getLogger().info((threads > 1 ? "[Thread " + threadID + "] " : "") + "Submitted generation " + currentId + " for the reward of " + rewardObject.getDouble("reward") + " kudos");
                                            }
                                        } else if (bd.getResponseCode() == 404) {
                                            bridge.getLogger().warn("Generation " + currentId + " is stale while we working on it!");
                                        } else {
                                            bridge.getLogger().warn((threads > 1 ? "[Thread " + threadID + "] " : "") + "Submit rejected " + bd.getResponseCode() + " for " + currentId + ": " + bd.getResponseString());
                                        }

                                        currentId = "";
                                    } else {
                                        String generation = bridge.getGenerator().startGeneration(payloadObject);

                                        JSONObject submitObject;
                                        if (generation == null) {
                                            submitObject = new JSONObject().put("id", currentId).put("generation", "faulted").put("state", "faulted").put("seed", -1);
                                        } else {
                                            submitObject = new JSONObject().put("id", currentId).put("generation", generation).put("state", "ok").put("seed", 0);
                                        }

                                        bd = BrowserClient.executePOSTRequestString(new URL((useBackupCluster ? backupClusterURL : clusterURL) + "/api/v2/generate/text/submit"), submitObject.toString(), headers);

                                        if (bd.getResponseCode() == 200) {
                                            if (generation == null) {
                                                failedRequestsCount++;

                                                bridge.getLogger().error((threads > 1 ? "[Thread " + threadID + "] " : "") + "Aborting generation " + currentId + " due to exceeded 5 retry counts");
                                            } else {
                                                JSONObject rewardObject = new JSONObject(bd.getResponseString());

                                                failedRequestsCount = 0;
                                                if (useBackupCluster) {
                                                    backupClusterGenCount++;
                                                }

                                                bridge.getLogger().info((threads > 1 ? "[Thread " + threadID + "] " : "") + "Submitted generation " + currentId + " for the reward of " + rewardObject.getDouble("reward") + " kudos");
                                            }
                                        } else if (bd.getResponseCode() == 404) {
                                            bridge.getLogger().warn("Generation " + currentId + " is stale while we working on it!");
                                        } else {
                                            bridge.getLogger().warn((threads > 1 ? "[Thread " + threadID + "] " : "") + "Submit rejected " + bd.getResponseCode() + " for " + currentId + ": " + bd.getResponseString());
                                        }

                                        currentId = "";
                                    }
                                } else {
                                    bridge.getLogger().debug((threads > 1 ? "[Thread " + threadID + "] " : "") + "There are no generations popped to do. Skipped: " + popObject.getJSONObject("skipped").toString());

                                    try {
                                        TimeUnit.SECONDS.sleep(HordeBridge.INTERVAL);
                                    } catch (InterruptedException ignored) {
                                    }
                                }
                            } else if (bd.getResponseCode() == 400) {
                                bridge.getLogger().debug((threads > 1 ? "[Thread " + threadID + "] " : "") + "Horde Cluster has a Validation Error! (" + popObject.getString("message") + ") Trying again in 5 seconds");

                                failedRequestsCount++;

                                try {
                                    TimeUnit.SECONDS.sleep(5);
                                } catch (InterruptedException ignored) {
                                }
                            } else if (bd.getResponseCode() == 401) {
                                bridge.getLogger().error((threads > 1 ? "[Thread " + threadID + "] " : "") + "Invalid API Key! (" + popObject.getString("message") + ") Please check if it is valid! Closing in 10 seconds.");

                                failedRequestsCount++;

                                try {
                                    TimeUnit.SECONDS.sleep(10);
                                } catch (InterruptedException ignored) {
                                }

                                System.exit(0);
                            } else if (bd.getResponseCode() == 403) {
                                bridge.getLogger().warn((threads > 1 ? "[Thread " + threadID + "] " : "") + "Access is Denied! (" + popObject.getString("message") + ") Trying again in 10 seconds");

                                failedRequestsCount++;

                                try {
                                    TimeUnit.SECONDS.sleep(10);
                                } catch (InterruptedException ignored) {
                                }
                            }
                        }
                    } catch (IOException | InterruptedException | URISyntaxException e) {
                        e.printStackTrace();
                        bridge.getLogger().error("Cannot populate worker! (" + e.getLocalizedMessage() + ") Trying again in 10 seconds!");

                        failedRequestsCount++;

                        try {
                            TimeUnit.SECONDS.sleep(10);
                        } catch (InterruptedException ignored) {
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        bridge.getLogger().error((threads > 1 ? "[Thread " + threadID + "] " : "") + "Horde Cluster is up but has invalid response! (" + e.getLocalizedMessage() + ") Trying again in 5 seconds!");

                        failedRequestsCount++;

                        try {
                            TimeUnit.SECONDS.sleep(5);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            }
        }
    }

    public void stop() {
        isRunning = false;
    }
}

