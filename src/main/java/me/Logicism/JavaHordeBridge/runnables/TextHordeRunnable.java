package me.Logicism.JavaHordeBridge.runnables;

import me.Logicism.JavaHordeBridge.HordeBridge;
import me.Logicism.JavaHordeBridge.network.BrowserClient;
import me.Logicism.JavaHordeBridge.network.BrowserData;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TextHordeRunnable implements Runnable {

    private HordeBridge bridge;
    private String kaiURL;
    private String kaiName;
    private String apiKey;
    private String clusterURL;
    private String backupClusterURL;
    private String[] priorityUsernames;

    private boolean isRunning = true;
    private int failedRequestsCount = 0;
    private boolean useBackupCluster = false;
    private int backupClusterGenCount = 0;
    private Long lastValidated = null;
    private JSONObject clientData = null;
    private String currentId = "";
    private String softprompt = "";

    public TextHordeRunnable(HordeBridge bridge, String kaiURL, String kaiName, String apiKey, String clusterURL, String backupClusterURL, String[] priorityUsernames) {
        this.bridge = bridge;
        this.kaiURL = kaiURL;
        this.kaiName = kaiName;
        this.apiKey = apiKey;
        this.backupClusterURL = backupClusterURL;
        this.clusterURL = clusterURL;
        this.priorityUsernames = priorityUsernames;
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

            if (clientData == null && lastValidated == null || System.currentTimeMillis() - lastValidated >= 30000) {
                try {
                    bridge.getLogger().debug("Retrieving KoboldAI Worker Settings...");
                    clientData = bridge.getGenerator().validateClient(kaiName, priorityUsernames);
                    lastValidated = System.currentTimeMillis();

                    failedRequestsCount = 0;
                } catch (IOException e) {
                    failedRequestsCount++;

                    bridge.getLogger().error("Cannot connect to KoboldAI Client! ("
                            + e.getLocalizedMessage() + ") Trying again in 5 seconds!");

                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException ignored) {
                    }
                } catch (JSONException e) {
                    failedRequestsCount++;

                    bridge.getLogger().error("Client is up but has invalid response! ("
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
                        bd = BrowserClient.executePOSTRequest(new URL(backupClusterURL + "/api/v2/generate/text/pop"), clientData.toString(), headers);
                    } else {
                        try {
                            bd = BrowserClient.executePOSTRequest(new URL(clusterURL + "/api/v2/generate/text/pop"), clientData.toString(), headers);
                        } catch (IOException | JSONException e) {
                            bridge.getLogger().error("Main Horde Cluster is down! (" + e.getLocalizedMessage() + ") Swapping to Backup Horde Cluster for the next 10 generations!");
                            useBackupCluster = true;
                        }
                    }

                    if (bd != null) {
                        JSONObject popObject = new JSONObject(BrowserClient.requestToString(bd.getResponse()));
                        if (bd.getResponseCode() == 200) {
                            if (!popObject.isNull("id")) {
                                currentId = popObject.getString("id");

                                JSONObject payloadObject = popObject.getJSONObject("payload");
                                payloadObject.put("quiet", true);

                                bridge.getLogger().info("Received a job with " + payloadObject.optInt("max_length", 80) + " tokens and " + payloadObject.optInt("max_context_length", 1024) + " max context length");

                                if (!softprompt.equals(popObject.optString("softprompt", ""))) {
                                    softprompt = popObject.optString("softprompt", "");

                                    bd = BrowserClient.executePUTRequest(new URL(kaiURL + "/api/latest/config/soft_prompt/"), new JSONObject().put("value", softprompt).toString(), null);

                                    try {
                                        TimeUnit.SECONDS.sleep(1);
                                    } catch (InterruptedException ignored) {
                                    }
                                }

                                String generation = bridge.getGenerator().startGeneration(payloadObject);

                                JSONObject submitObject;
                                if (generation == null) {
                                    submitObject = new JSONObject().put("id", currentId).put("generation", "faulted").put("state", "faulted").put("seed", -1);
                                } else {
                                    submitObject = new JSONObject().put("id", currentId).put("generation", generation).put("state", "ok").put("seed", 0);
                                }


                                bd = BrowserClient.executePOSTRequest(new URL(clusterURL + "/api/v2/generate/text/submit"), submitObject.toString(), headers);

                                if (bd.getResponseCode() == 200) {
                                    if (generation == null) {
                                        failedRequestsCount++;

                                        bridge.getLogger().error("Aborting generation " + currentId + " due to exceeded 5 retry counts");
                                    } else {
                                        JSONObject rewardObject = new JSONObject(BrowserClient.requestToString(bd.getResponse()));

                                        failedRequestsCount = 0;
                                        if (useBackupCluster) {
                                            backupClusterGenCount++;
                                        }

                                        bridge.getLogger().info("Submitted generation " + currentId + " for the reward of " + rewardObject.getDouble("reward") + " kudos");
                                    }
                                } else if (bd.getResponseCode() == 404) {
                                    bridge.getLogger().warn("Generation " + currentId + " is stale while we working on it!");
                                }

                                currentId = "";
                            } else {
                                bridge.getLogger().debug("There are no generations popped to do. Skipped: " + popObject.getJSONObject("skipped").toString());

                                try {
                                    TimeUnit.SECONDS.sleep(HordeBridge.INTERVAL);
                                } catch (InterruptedException ignored) {
                                }
                            }
                        } else if (bd.getResponseCode() == 400) {
                            bridge.getLogger().debug("Horde Cluster has a Validation Error! (" + popObject.getString("message") + ") Trying again in 5 seconds");

                            failedRequestsCount++;

                            try {
                                TimeUnit.SECONDS.sleep(5);
                            } catch (InterruptedException ignored) {
                            }
                        } else if (bd.getResponseCode() == 401) {
                            bridge.getLogger().error("Invalid API Key! (" + popObject.getString("message") + ") Please check if it is valid! Closing in 10 seconds.");

                            failedRequestsCount++;

                            try {
                                TimeUnit.SECONDS.sleep(10);
                            } catch (InterruptedException ignored) {
                            }

                            System.exit(0);
                        } else if (bd.getResponseCode() == 403) {
                            bridge.getLogger().warn("Access is Denied! (" + popObject.getString("message") + ") Trying again in 10 seconds");

                            failedRequestsCount++;

                            try {
                                TimeUnit.SECONDS.sleep(10);
                            } catch (InterruptedException ignored) {
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    bridge.getLogger().error("Cannot populate worker! (" + e.getLocalizedMessage() + ") Trying again in 10 seconds!");

                    failedRequestsCount++;

                    try {
                        TimeUnit.SECONDS.sleep(10);
                    } catch (InterruptedException ignored) {
                    }
                } catch (JSONException e) {
                    bridge.getLogger().error("Horde Cluster is up but has invalid response! (" + e.getLocalizedMessage() + ") Trying again in 5 seconds!");

                    failedRequestsCount++;

                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }
    }

    public void stop() {
        isRunning = false;
    }
}
