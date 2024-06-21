package ru.mihalis.selsup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.apache.commons.lang3.concurrent.TimedSemaphore;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private static final String apiUrl = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private static final OkHttpClient restClient = new OkHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final TimedSemaphore timedSemaphore;

    public CrptApi(TimeUnit requestsTimePeriod, int requestsLimit) {
        if (requestsTimePeriod == null) {
            throw new RuntimeException("requestsTimePeriod must be non-null!");
        }
        if (requestsLimit <= 0) {
            throw new RuntimeException("requestsLimit must be positive!");
        }

        timedSemaphore = new TimedSemaphore(1, requestsTimePeriod, requestsLimit);
    }

    @NotNull
    public String createDocument(Map<String, ?> document, String sign) {
        try {
            timedSemaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException("Document creating interrupted!");
        }

        System.out.println(timedSemaphore.getAvailablePermits());

        try (Response response = postRequest(document).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                System.err.println("Api status code is unsuccessful: " + response.code() + ". Details: " + body);
                throw new RuntimeException("Can't execute post request to crpt api!");
            }

            // save document and sign to db...

            return body;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    private static Call postRequest(Object body) {
        try {
            return restClient.newCall(
                    new Request.Builder()
                            .url(apiUrl)
                            .post(
                                    RequestBody.create(
                                            objectMapper.writeValueAsString(body),
                                            MediaType.parse("application/json")
                                    )
                            )
                            .build()
            );
        } catch (JsonProcessingException e) {
            System.err.println("Can't serialize such body: " + body);
            throw new RuntimeException(e);
        }
    }
}
