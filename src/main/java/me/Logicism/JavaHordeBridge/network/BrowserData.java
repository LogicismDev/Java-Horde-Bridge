package me.Logicism.JavaHordeBridge.network;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class BrowserData {

    private String url;
    private Map<String, List<String>> headers;
    private int resCode;
    private int resLength;
    private InputStream response;
    private String resString;

    public BrowserData(String url, Map<String, List<String>> headers, int resCode, InputStream response, int resLength) {
        this.url = url;
        this.headers = headers;
        this.resCode = resCode;
        this.resLength = resLength;
        this.response = response;
    }

    public BrowserData(String url, Map<String, List<String>> headers, int resCode, String reponseString, int resLength) {
        this.url = url;
        this.headers = headers;
        this.resCode = resCode;
        this.resString = reponseString;
        this.resLength = resLength;
    }

    public BrowserData(String url, Map<String, List<String>> headers, int resCode) {
        this.url = url;
        this.headers = headers;
        this.resCode = resCode;
    }

    public String getURL() {
        return url;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public int getResponseCode() {
        return resCode;
    }

    public int getResponseLength() {
        return resLength;
    }

    public InputStream getResponse() {
        return response;
    }

    public String getResponseString() {
        return resString;
    }
}
