package me.Logicism.JavaHordeBridge.network;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class BrowserClient {

    public static BrowserData executeGETRequest(URL url, Map<String, String> headers) throws IOException {
        if (url.getProtocol().equals("https")) {
            HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
            c.setConnectTimeout(300000);
            c.setReadTimeout(300000);
            c.setInstanceFollowRedirects(true);
            c.setRequestMethod("GET");
            HttpsURLConnection.setFollowRedirects(true);

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    c.addRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            int resCode = c.getResponseCode();
            int resLength = c.getContentLength();

            return new BrowserData(c.getURL().toString(), c.getHeaderFields(), resCode, resLength, resCode == 200 ? c.getInputStream() : c.getErrorStream());
        } else {
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(300000);
            c.setReadTimeout(300000);
            c.setInstanceFollowRedirects(true);
            c.setRequestMethod("GET");
            HttpURLConnection.setFollowRedirects(true);

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    c.addRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            int resCode = c.getResponseCode();
            int resLength = c.getContentLength();

            return new BrowserData(c.getURL().toString(), c.getHeaderFields(), resCode, resLength, resCode == 200 ? c.getInputStream() : c.getErrorStream());
        }
    }

    public static BrowserData executePOSTRequest(URL url, String data, Map<String, String> headers) throws IOException {
        if (url.getProtocol().equals("https")) {
            HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
            c.setConnectTimeout(300000);
            c.setReadTimeout(300000);
            c.setInstanceFollowRedirects(true);
            c.setRequestMethod("POST");
            HttpsURLConnection.setFollowRedirects(true);

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    c.addRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            c.setDoInput(true);
            c.setDoOutput(true);

            DataOutputStream dos = new DataOutputStream(c.getOutputStream());
            dos.write(data.getBytes(StandardCharsets.UTF_8));
            dos.flush();
            dos.close();

            int resCode = c.getResponseCode();
            int resLength = c.getContentLength();

            return new BrowserData(c.getURL().toString(), c.getHeaderFields(), resCode, resLength, resCode == 200 ? c.getInputStream() : c.getErrorStream());
        } else {
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(300000);
            c.setReadTimeout(300000);
            c.setInstanceFollowRedirects(true);
            c.setRequestMethod("POST");
            HttpURLConnection.setFollowRedirects(true);

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    c.addRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            c.setDoInput(true);
            c.setDoOutput(true);

            DataOutputStream dos = new DataOutputStream(c.getOutputStream());
            dos.write(data.getBytes(StandardCharsets.UTF_8));
            dos.flush();
            dos.close();

            int resCode = c.getResponseCode();
            int resLength = c.getContentLength();

            return new BrowserData(c.getURL().toString(), c.getHeaderFields(), resCode, resLength, resCode == 200 ? c.getInputStream() : c.getErrorStream());
        }
    }

    public static BrowserData executePUTRequest(URL url, String data, Map<String, String> headers) throws IOException {
        return executePUTRequest(url, data.getBytes(StandardCharsets.UTF_8), headers);
    }

    public static BrowserData executePUTRequest(URL url, byte[] data, Map<String, String> headers) throws IOException {
        if (url.getProtocol().equals("https")) {
            HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
            c.setConnectTimeout(30000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(true);
            c.setRequestMethod("PUT");
            HttpsURLConnection.setFollowRedirects(true);

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    c.addRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            c.setDoInput(true);
            c.setDoOutput(true);

            DataOutputStream dos = new DataOutputStream(c.getOutputStream());
            dos.write(data);
            dos.flush();
            dos.close();

            int resCode = c.getResponseCode();
            int resLength = c.getContentLength();

            return new BrowserData(c.getURL().toString(), c.getHeaderFields(), resCode, resLength, resCode == 200 ? c.getInputStream() : c.getErrorStream());
        } else {
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(30000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(true);
            c.setRequestMethod("PUT");
            HttpURLConnection.setFollowRedirects(true);

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    c.addRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            c.setDoInput(true);
            c.setDoOutput(true);

            DataOutputStream dos = new DataOutputStream(c.getOutputStream());
            dos.write(data);
            dos.flush();
            dos.close();

            int resCode = c.getResponseCode();
            int resLength = c.getContentLength();

            return new BrowserData(c.getURL().toString(), c.getHeaderFields(), resCode, resLength, resCode == 200 ? c.getInputStream() : c.getErrorStream());
        }
    }

    public static String requestToString(InputStream is) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String s;
        StringBuilder sb = new StringBuilder();
        while ((s = br.readLine()) != null) {
            sb.append(s);
        }

        is.close();

        return sb.toString();
    }

}
