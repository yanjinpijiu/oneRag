package com.onerag.document.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SiliconFlow Embedding 服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiliconFlowEmbeddingService implements EmbeddingService {

    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();

    @Value("${ai.providers.siliconflow.api-key}")
    private String apiKey;

    @Value("${ai.providers.siliconflow.url}")
    private String baseUrl;

    @Value("${ai.providers.siliconflow.endpoints.embedding}")
    private String embeddingEndpoint;

    @Value("${ai.embedding.candidates[0].model}")
    private String defaultModel;

    /**
     * 单文本向量化（委托批量接口实现）。
     */
    @Override
    public List<Float> embed(String text) {
        List<List<Float>> result = embedBatch(List.of(text));
        return result.isEmpty() ? new ArrayList<>() : result.get(0);
    }

    /**
     * 批量调用 SiliconFlow 向量接口。
     */
    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, Object> request = new HashMap<>();
        request.put("model", defaultModel);
        request.put("input", texts);
        request.put("encoding_format", "float");

        try {
            String url = baseUrl + embeddingEndpoint;
            Request httpRequest = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(gson.toJson(request), MediaType.parse("application/json")))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    log.error("SiliconFlow embeddings HTTP error: status={}, body={}", response.code(), errorBody);
                    throw new RuntimeException(
                            "调用 SiliconFlow Embedding 失败: HTTP " + response.code() + " - " + errorBody);
                }

                String responseBody = response.body() != null ? response.body().string() : "{}";
                JsonObject root = gson.fromJson(responseBody, JsonObject.class);

                if (root.has("data") && root.get("data").isJsonArray()) {
                    JsonArray dataArray = root.getAsJsonArray("data");
                    List<List<Float>> embeddings = new ArrayList<>();

                    for (int i = 0; i < dataArray.size(); i++) {
                        JsonObject item = dataArray.get(i).getAsJsonObject();
                        if (item.has("embedding") && item.get("embedding").isJsonArray()) {
                            JsonArray embeddingArray = item.getAsJsonArray("embedding");
                            List<Float> vector = new ArrayList<>();

                            for (int j = 0; j < embeddingArray.size(); j++) {
                                vector.add(embeddingArray.get(j).getAsFloat());
                            }

                            embeddings.add(vector);
                        }
                    }

                    log.info("成功向量化 {} 个文本", embeddings.size());
                    return embeddings;
                } else {
                    log.error("SiliconFlow response format error: {}", responseBody);
                    throw new RuntimeException("SiliconFlow response format error");
                }
            }
        } catch (IOException e) {
            log.error("调用 SiliconFlow Embedding 失败", e);
            throw new RuntimeException("调用 SiliconFlow Embedding 失败: " + e.getMessage(), e);
        }
    }
}
