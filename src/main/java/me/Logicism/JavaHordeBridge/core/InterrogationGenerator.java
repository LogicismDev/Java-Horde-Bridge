package me.Logicism.JavaHordeBridge.core;

import me.Logicism.JavaHordeBridge.HordeBridge;
import me.Logicism.JavaHordeBridge.network.BrowserClient;
import me.Logicism.JavaHordeBridge.network.BrowserData;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class InterrogationGenerator {

    private HordeBridge bridge;
    private String kaiURL;

    public InterrogationGenerator(HordeBridge bridge, String kaiURL) {
        this.bridge = bridge;
        this.kaiURL = kaiURL;
    }

    public JSONObject startCaptionGeneration(String imageURL) {
        JSONObject generationObject = null;
        int retryCount = 0;
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", "Java 11 / Java Horde Bridge " + HordeBridge.BRIDGE_VERSION);
        while (retryCount < 5) {
            try {
                BrowserData generationData = BrowserClient.executePOSTRequest(new URL(kaiURL + "/caption"), new JSONObject().put("url", imageURL).toString(), headers);

                if (generationData.getResponseCode() == 200) {
                    generationObject = new JSONObject(BrowserClient.requestToString(generationData.getResponse()));

                    break;
                } else if (generationData.getResponseCode() == 500) {
                    bridge.getLogger().debug("Interrogation Agent had an error, retrying generation in 5 seconds...");

                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException ignored) {
                    }
                } else {
                    bridge.getLogger().debug("Client responded with " + generationData.getResponseCode() + " (attempt " + retryCount++ + "), retrying generation in 5 seconds...");

                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException ignored) {
                    }
                }
            } catch (JSONException e) {
                bridge.getLogger().debug("Client returned unexpected response (attempt " + retryCount++ + "), retrying generation in 5 seconds...");

                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ignored) {
                }
            } catch (IOException e) {
                bridge.getLogger().debug("Client is unavailable (attempt " + retryCount++ + "), retrying generation in 5 seconds...");

                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ignored) {
                }
            }
        }

        if (generationObject == null) {
            generationObject = new JSONObject().put("error", "The generation could not be completed due to an unknown error");
        }

        return generationObject;
    }

    public JSONObject startSafetyCheckGeneration(String imageURL) {
        JSONObject generationObject = null;
        int retryCount = 0;
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", "Java 11 / Java Horde Bridge " + HordeBridge.BRIDGE_VERSION);
        while (retryCount < 5) {
            try {
                BrowserData generationData = BrowserClient.executePOSTRequest(new URL(kaiURL + "/safetycheck"), new JSONObject().put("url", imageURL).toString(), headers);

                if (generationData.getResponseCode() == 200) {
                    generationObject = new JSONObject(BrowserClient.requestToString(generationData.getResponse()));

                    break;
                } else if (generationData.getResponseCode() == 500) {
                    bridge.getLogger().debug("Interrogation Agent had an error, retrying generation in 5 seconds...");

                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException ignored) {
                    }
                } else {
                    bridge.getLogger().debug("Client responded with " + generationData.getResponseCode() + " (attempt " + retryCount++ + "), retrying generation in 5 seconds...");

                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException ignored) {
                    }
                }
            } catch (JSONException e) {
                bridge.getLogger().debug("Client returned unexpected response (attempt " + retryCount++ + "), retrying generation in 5 seconds...");

                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ignored) {
                }
            } catch (IOException e) {
                bridge.getLogger().debug("Client is unavailable (attempt " + retryCount++ + "), retrying generation in 5 seconds...");

                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ignored) {
                }
            }
        }

        if (generationObject == null) {
            generationObject = new JSONObject().put("error", "The generation could not be completed due to an unknown error");
        }

        return generationObject;
    }

    public JSONObject startInterrogationGeneration(String imageURL) {
        JSONObject generationObject = null;
        int retryCount = 0;
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", "Java 11 / Java Horde Bridge " + HordeBridge.BRIDGE_VERSION);
        while (retryCount < 5) {
            try {
                BrowserData generationData = BrowserClient.executePOSTRequest(new URL(kaiURL + "/interrogation"), new JSONObject().put("url", imageURL).toString(), headers);

                if (generationData.getResponseCode() == 200) {
                    generationObject = new JSONObject(BrowserClient.requestToString(generationData.getResponse()));

                    break;
                } else if (generationData.getResponseCode() == 500) {
                    bridge.getLogger().debug("Interrogation Agent had an error, retrying generation in 5 seconds...");

                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException ignored) {
                    }
                } else {
                    bridge.getLogger().debug("Client responded with " + generationData.getResponseCode() + " (attempt " + retryCount++ + "), retrying generation in 5 seconds...");

                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException ignored) {
                    }
                }
            } catch (JSONException e) {
                bridge.getLogger().debug("Client returned unexpected response (attempt " + retryCount++ + "), retrying generation in 5 seconds...");

                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ignored) {
                }
            } catch (IOException e) {
                bridge.getLogger().debug("Client is unavailable (attempt " + retryCount++ + "), retrying generation in 5 seconds...");

                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ignored) {
                }
            }
        }

        if (generationObject == null) {
            generationObject = new JSONObject().put("error", "The generation could not be completed due to an unknown error");
        }

        return generationObject;
    }

    public JSONObject startStripBackgroundGeneration(String imageURL) {
        JSONObject generationObject = null;
        int retryCount = 0;
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", "Java 11 / Java Horde Bridge " + HordeBridge.BRIDGE_VERSION);
        while (retryCount < 5) {
            try {
                BrowserData generationData = BrowserClient.executePOSTRequest(new URL(kaiURL + "/stripbackground"), new JSONObject().put("url", imageURL).toString(), headers);

                if (generationData.getResponseCode() == 200) {
                    generationObject = new JSONObject(BrowserClient.requestToString(generationData.getResponse()));

                    break;
                } else if (generationData.getResponseCode() == 500) {
                    bridge.getLogger().debug("Interrogation Agent had an error, retrying generation in 5 seconds...");

                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException ignored) {
                    }
                } else {
                    bridge.getLogger().debug("Client responded with " + generationData.getResponseCode() + " (attempt " + retryCount++ + "), retrying generation in 5 seconds...");

                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException ignored) {
                    }
                }
            } catch (JSONException e) {
                bridge.getLogger().debug("Client returned unexpected response (attempt " + retryCount++ + "), retrying generation in 5 seconds...");

                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ignored) {
                }
            } catch (IOException e) {
                bridge.getLogger().debug("Client is unavailable (attempt " + retryCount++ + "), retrying generation in 5 seconds...");

                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ignored) {
                }
            }
        }

        if (generationObject == null) {
            generationObject = new JSONObject().put("error", "The generation could not be completed due to an unknown error");
        }

        return generationObject;
    }

    public boolean validateClient() throws IOException {
        BrowserData bd = BrowserClient.executeGETRequest(new URL(kaiURL + "/health"), null);

        return bd.getResponseCode() == 200;
    }
}
