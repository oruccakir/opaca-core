package de.dailab.jiacpp.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Helper class for issuing different REST calls in Java.
 */
@Log
@AllArgsConstructor
public class RestHelper {

    public final String baseUrl;
    public final String token;

    public static final ObjectMapper mapper = JsonMapper.builder()
            .findAndAddModules().build();


    public <T> T get(String path, Class<T> type) throws IOException {
        return request("GET", path, null, type);
    }

    public <T> T post(String path, Object payload, Class<T> type) throws IOException {
        return request("POST", path, payload, type);
    }

    public <T> T delete(String path, Object payload, Class<T> type) throws IOException {
        return request("DELETE", path, payload, type);
    }

    public <T> T request(String method, String path, Object payload, Class<T> type) throws IOException {
        log.info(String.format("%s %s%s (%s)", method, baseUrl, path, payload));
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod(method);

        if (token != null && ! token.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        
        if (payload != null) {
            String json = mapper.writeValueAsString(payload);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.connect();
            try (OutputStream os = connection.getOutputStream()) {
                os.write(bytes);
            }
        } else {
            connection.connect();
        }
        
        if (type != null) {
            return mapper.readValue(connection.getInputStream(), type);
        } else {
            return null;
        }
    }
    
    public static JsonNode readJson(String json) throws IOException {
        return mapper.readTree(json);
    }

    public static Map<String, JsonNode> readMap(String json) throws IOException {
        TypeReference<Map<String, JsonNode>> prototype = new TypeReference<>() {};
         return mapper.readValue(json, prototype);
    }

    public static <T> T readObject(String json, Class<T> type) throws IOException {
        return mapper.readValue(json, type);
    }

    public static String writeJson(Object obj) throws IOException {
        return mapper.writeValueAsString(obj);
    }

    public ResponseEntity<StreamingResponseBody> getStream(String path, Object payload) throws IOException {
        StreamingResponseBody responseBody = response -> {
            for (int i = 1; i <= 1000; i++) {
            try {
                Thread.sleep(10);
                response.write(("Data stream line - " + i + "\n").getBytes());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            }
        };
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .body(responseBody);
    }



}
