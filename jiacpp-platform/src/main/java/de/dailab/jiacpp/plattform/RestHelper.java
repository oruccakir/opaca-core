package de.dailab.jiacpp.plattform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Helper class for issuing different REST calls in Java.
 */
@AllArgsConstructor
public class RestHelper {

    private String baseUrl;

    public static final ObjectMapper mapper = JsonMapper.builder()
            .findAndAddModules().build();


    public <T> T get(String path, Class<T> type) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod("GET");
        connection.connect();

        return mapper.readValue(connection.getInputStream(), type);
    }

    public void post(String path, Object payload) throws IOException {
        String json = mapper.writeValueAsString(payload);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(bytes.length);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.connect();
        try (OutputStream os = connection.getOutputStream()) {
            os.write(bytes);
        }
    }

    public static <T> T readJson(String json, Class<T> type) throws IOException {
        return mapper.readValue(json, type);
    }

}
