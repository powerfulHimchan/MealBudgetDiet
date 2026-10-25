package app.sikbi;

import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.json.JSONObject;

final class WidgetApi {
    static final class Failure extends java.io.IOException {
        final int status;
        Failure(int status) { super("Widget request failed"); this.status = status; }
    }
    static JSONObject call(String path, String token, JSONObject body) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) new URL("https://sikbi.app/api/v1/widget/device/" + path).openConnection();
        connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (body != null) {
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                try (java.io.OutputStream out = connection.getOutputStream()) {
                    out.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            int status = connection.getResponseCode();
            if (status != 200) throw new Failure(status);
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > 16384) throw new java.io.IOException("Widget response too large");
                    bytes.write(buffer, 0, count);
                }
                return new JSONObject(new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); }
    }
}
