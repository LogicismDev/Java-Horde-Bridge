package me.Logicism.JavaHordeBridge.runnables;

import me.Logicism.JavaHordeBridge.HordeBridge;
import me.Logicism.JavaHordeBridge.HordeConfig;
import me.Logicism.JavaHordeBridge.network.BrowserClient;
import me.Logicism.JavaHordeBridge.network.BrowserData;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class InterrogationHordeRunnable implements Runnable {

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
    private boolean online = false;

    public InterrogationHordeRunnable(HordeBridge bridge, String kaiURL, String kaiName, String apiKey, String clusterURL, String backupClusterURL, String[] priorityUsernames) {
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

            if (lastValidated == null || System.currentTimeMillis() - lastValidated >= 30000) {
                try {
                    bridge.getLogger().debug("Testing if Interrogation Agent is online...");
                    online = bridge.getInterroGenerator().validateClient();
                    lastValidated = System.currentTimeMillis();

                    failedRequestsCount = 0;
                } catch (IOException e) {
                    bridge.getLogger().error("Interrogation Agent is offline! Trying again in 10 seconds");

                    failedRequestsCount++;

                    try {
                        TimeUnit.SECONDS.sleep(10);
                    } catch (InterruptedException ignored) {
                    }
                } catch (JSONException e) {
                    failedRequestsCount++;

                    bridge.getLogger().error("Interrogation Agent is up but has invalid response! ("
                            + e.getLocalizedMessage() + ") Trying again in 5 seconds!");

                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException ignored) {
                    }
                }
            }

            if (online) {
                JSONObject interrogatePayload = new JSONObject();
                interrogatePayload.put("name", kaiName).put("forms", bridge.getConfig().getInterrogationForms()).put("amount", 1).put("threads", 1).put("max_tiles", 64).put("bridge_version", HordeBridge.BRIDGE_VERSION).put("bridge_agent", HordeBridge.BRIDGE_AGENT);;
                if (priorityUsernames.length != 0) {
                    JSONArray priorityUsernameArrays = new JSONArray();

                    for (String username : priorityUsernames) {
                        priorityUsernameArrays.put(username);
                    }

                    interrogatePayload.put("priority_usernames", priorityUsernameArrays);
                }

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
                        bd = BrowserClient.executePOSTRequest(new URL(backupClusterURL + "/api/v2/interrogate/pop"), interrogatePayload.toString(), headers);
                    } else {
                        try {
                            bd = BrowserClient.executePOSTRequest(new URL(clusterURL + "/api/v2/interrogate/pop"), interrogatePayload.toString(), headers);
                        } catch (IOException | JSONException e) {
                            bridge.getLogger().error("Main Horde Cluster is down! (" + e.getLocalizedMessage() + ") Swapping to Backup Horde Cluster for the next 10 generations!");
                            useBackupCluster = true;
                        }
                    }

                    if (bd != null) {
                        JSONObject popObject = new JSONObject(BrowserClient.requestToString(bd.getResponse()));
                        if (bd.getResponseCode() == 200) {
                            if (!popObject.getJSONArray("forms").isEmpty()) {
                                JSONArray forms = popObject.getJSONArray("forms");

                                bridge.getLogger().info("Received a job with " + forms.length() + " forms to be processed");
                                for (int i = 0; i < forms.length(); i++) {
                                    String currentId = forms.getJSONObject(i).getString("id");

                                    JSONObject generationObject = null;
                                    if (forms.getJSONObject(i).getString("form").equals("caption")) {
                                        generationObject = bridge.getInterroGenerator().startCaptionGeneration(forms.getJSONObject(i).getString("source_image"));
                                    } else if (forms.getJSONObject(i).getString("form").equals("nsfw")) {
                                        generationObject = bridge.getInterroGenerator().startSafetyCheckGeneration(forms.getJSONObject(i).getString("source_image"));
                                    } else if (forms.getJSONObject(i).getString("form").equals("interrogation")) {
                                        generationObject = bridge.getInterroGenerator().startInterrogationGeneration(forms.getJSONObject(i).getString("source_image"));
                                    } else if (forms.getJSONObject(i).getString("form").equals("strip_background")) {
                                        generationObject = bridge.getInterroGenerator().startStripBackgroundGeneration(forms.getJSONObject(i).getString("source_image"));
                                    }

                                    JSONObject submitObject;
                                    if (generationObject == null) {
                                        submitObject = new JSONObject().put("id", currentId).put("result", "faulted").put("state", "faulted").put("seed", -1);
                                    } else {
                                        submitObject = new JSONObject().put("id", currentId).put("state", "ok").put("seed", 0);

                                        if (forms.getJSONObject(i).getString("form").equals("strip_background")) {
                                            byte[] image = Base64.getDecoder().decode(generationObject.getString("strip_background").substring("data:image/webp;base64,".length()));
                                            bd = BrowserClient.executePUTRequest(new URL(forms.getJSONObject(i).getString("r2_upload")), image, headers);

                                            submitObject.put("result", "R2");
                                        } else {
                                            submitObject.put("result", generationObject);
                                        }
                                    }

                                    bd = BrowserClient.executePOSTRequest(new URL(clusterURL + "/api/v2/interrogate/submit"), submitObject.toString(), headers);

                                    if (bd.getResponseCode() == 200) {
                                        if (generationObject == null) {
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
                                }
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
            } else {
                bridge.getLogger().error("Interrogation Agent is offline! Trying again in 10 seconds");

                failedRequestsCount++;

                try {
                    TimeUnit.SECONDS.sleep(10);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    public void stop() {
        isRunning = false;
    }
}
