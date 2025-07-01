package com.somdiproy.lambda.screening.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

/**
 * Service for invoking Amazon Nova models via Bedrock API
 * Handles authentication, rate limiting, and response parsing
 */
public class NovaInvokerService {
    
    private static final Logger logger = LoggerFactory.getLogger(NovaInvokerService.class);
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CloseableHttpClient httpClient;
    private final String bedrockRegion;
    private final Map<String, Long> lastCallTime = new HashMap<>();
    private final Map<String, Integer> callCount = new HashMap<>();
    
    // Rate limiting: Nova Micro has 5 requests per second limit
    private static final long MIN_CALL_INTERVAL_MS = 200; // 5 calls per second
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    
    public NovaInvokerService(String bedrockRegion) {
        this.bedrockRegion = bedrockRegion != null ? bedrockRegion : "us-east-1";
        this.httpClient = HttpClients.createDefault();
    }
    
    /**
     * Invoke Nova Micro model for file screening
     */
    public NovaResponse invokeNovaMicro(String prompt, NovaRequest request) throws NovaInvokerException {
        return invokeNova("us.amazon.nova-micro-v1:0", prompt, request, 500);
    }
    
    /**
     * Invoke Nova Lite model for issue detection  
     */
    public NovaResponse invokeNovaLite(String prompt, NovaRequest request) throws NovaInvokerException {
        return invokeNova("us.amazon.nova-lite-v1:0", prompt, request, 4000);
    }
    
    /**
     * Invoke Nova Premier model for suggestion generation
     */
    public NovaResponse invokeNovaPremier(String prompt, NovaRequest request) throws NovaInvokerException {
        return invokeNova("us.amazon.nova-premier-v1:0", prompt, request, 8000);
    }
    
    /**
     * Generic Nova model invocation with rate limiting and retries
     */
    private NovaResponse invokeNova(String modelId, String prompt, NovaRequest request, int maxTokens) 
            throws NovaInvokerException {
        
        String callKey = modelId + "-" + Thread.currentThread().getId();
        
        try {
            // Rate limiting
            enforceRateLimit(callKey);
            
            // Build request payload
            Map<String, Object> payload = buildRequestPayload(modelId, prompt, request, maxTokens);
            
            // Make API call with retries
            NovaResponse response = callBedrockWithRetries(modelId, payload);
            
            // Track metrics
            updateCallMetrics(callKey);
            
            logger.debug("Successfully invoked {} for analysis", modelId);
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to invoke Nova model {}: {}", modelId, e.getMessage());
            throw new NovaInvokerException("Nova invocation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Build the request payload for Bedrock API
     */
    private Map<String, Object> buildRequestPayload(String modelId, String prompt, 
                                                   NovaRequest request, int maxTokens) {
        Map<String, Object> payload = new HashMap<>();
        
        // Basic model parameters
        payload.put("modelId", modelId);
        
        // Message structure for Nova models
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        
        List<Map<String, Object>> content = new ArrayList<>();
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("text", prompt);
        content.add(textContent);
        
        message.put("content", content);
        messages.add(message);
        payload.put("messages", messages);
        
        // Inference configuration
        Map<String, Object> inferenceConfig = new HashMap<>();
        inferenceConfig.put("maxTokens", maxTokens);
        inferenceConfig.put("temperature", request != null ? request.getTemperature() : 0.1);
        inferenceConfig.put("topP", request != null ? request.getTopP() : 0.9);
        
        payload.put("inferenceConfig", inferenceConfig);
        
        // Additional configuration if provided
        if (request != null && request.getStopSequences() != null) {
            inferenceConfig.put("stopSequences", request.getStopSequences());
        }
        
        return payload;
    }
    
    /**
     * Make Bedrock API call with retry logic
     */
    private NovaResponse callBedrockWithRetries(String modelId, Map<String, Object> payload) 
            throws NovaInvokerException {
        
        String endpoint = String.format("https://bedrock-runtime.%s.amazonaws.com/model/%s/converse", 
                                       bedrockRegion, modelId);
        
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                logger.debug("Attempt {} to call Nova model {}", attempt, modelId);
                
                HttpPost request = new HttpPost(endpoint);
                request.setHeader("Content-Type", "application/json");
                request.setHeader("Accept", "application/json");
                
                // AWS Signature V4 would be handled by AWS SDK in production
                // For now, using environment-based authentication
                
                String jsonPayload = objectMapper.writeValueAsString(payload);
                request.setEntity(new StringEntity(jsonPayload));
                
                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    String responseBody = EntityUtils.toString(response.getEntity());
                    
                    if (statusCode == 200) {
                        return parseNovaResponse(responseBody, modelId);
                    } else if (statusCode == 429) {
                        // Rate limit exceeded
                        logger.warn("Rate limit exceeded for {}, attempt {}", modelId, attempt);
                        if (attempt < MAX_RETRIES) {
                            Thread.sleep(RETRY_DELAY_MS * attempt);
                            continue;
                        }
                    } else if (statusCode >= 500) {
                        // Server error, retry
                        logger.warn("Server error {} for {}, attempt {}", statusCode, modelId, attempt);
                        if (attempt < MAX_RETRIES) {
                            Thread.sleep(RETRY_DELAY_MS);
                            continue;
                        }
                    }
                    
                    throw new NovaInvokerException(
                        String.format("Bedrock API call failed: %d - %s", statusCode, responseBody));
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NovaInvokerException("Nova invocation interrupted", e);
            } catch (IOException e) {
                lastException = e;
                logger.warn("Network error on attempt {} for {}: {}", attempt, modelId, e.getMessage());
                
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new NovaInvokerException("Nova invocation interrupted", ie);
                    }
                }
            } catch (Exception e) {
                lastException = e;
                logger.error("Unexpected error on attempt {} for {}: {}", attempt, modelId, e.getMessage());
                break; // Don't retry on unexpected errors
            }
        }
        
        throw new NovaInvokerException("All retry attempts failed", lastException);
    }
    
    /**
     * Parse Nova model response
     */
    private NovaResponse parseNovaResponse(String responseBody, String modelId) throws NovaInvokerException {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            
            // Extract response content
            JsonNode output = root.path("output");
            JsonNode message = output.path("message");
            JsonNode content = message.path("content");
            
            String responseText = "";
            if (content.isArray() && content.size() > 0) {
                responseText = content.get(0).path("text").asText();
            }
            
            // Extract usage information
            JsonNode usage = root.path("usage");
            int inputTokens = usage.path("inputTokens").asInt(0);
            int outputTokens = usage.path("outputTokens").asInt(0);
            int totalTokens = usage.path("totalTokens").asInt(inputTokens + outputTokens);
            
            // Calculate cost based on model
            double cost = calculateCost(modelId, inputTokens, outputTokens);
            
            return NovaResponse.builder()
                .responseText(responseText)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(totalTokens)
                .estimatedCost(cost)
                .modelId(modelId)
                .successful(true)
                .timestamp(Instant.now().toEpochMilli())
                .build();
                
        } catch (Exception e) {
            throw new NovaInvokerException("Failed to parse Nova response: " + e.getMessage(), e);
        }
    }
    
    /**
     * Calculate cost based on Nova model pricing
     */
    private double calculateCost(String modelId, int inputTokens, int outputTokens) {
        double costPer1MTokens;
        
        switch (modelId) {
            case "us.amazon.nova-micro-v1:0":
                costPer1MTokens = 0.0075; // $0.0075 per 1M tokens
                break;
            case "us.amazon.nova-lite-v1:0":
                costPer1MTokens = 0.015; // $0.015 per 1M tokens
                break;
            case "us.amazon.nova-premier-v1:0":
                costPer1MTokens = 0.80; // $0.80 per 1M tokens
                break;
            default:
                costPer1MTokens = 0.015; // Default to Lite pricing
        }
        
        return ((double) (inputTokens + outputTokens) / 1_000_000.0) * costPer1MTokens;
    }
    
    /**
     * Enforce rate limiting between API calls
     */
    private void enforceRateLimit(String callKey) throws InterruptedException {
        Long lastCall = lastCallTime.get(callKey);
        if (lastCall != null) {
            long timeSinceLastCall = System.currentTimeMillis() - lastCall;
            if (timeSinceLastCall < MIN_CALL_INTERVAL_MS) {
                Thread.sleep(MIN_CALL_INTERVAL_MS - timeSinceLastCall);
            }
        }
        lastCallTime.put(callKey, System.currentTimeMillis());
    }
    
    /**
     * Update call metrics for monitoring
     */
    private void updateCallMetrics(String callKey) {
        callCount.merge(callKey, 1, Integer::sum);
    }
    
    /**
     * Get call statistics
     */
    public Map<String, Integer> getCallStatistics() {
        return new HashMap<>(callCount);
    }
    
    /**
     * Reset call statistics
     */
    public void resetStatistics() {
        callCount.clear();
        lastCallTime.clear();
    }
    
    /**
     * Close HTTP client resources
     */
    public void close() {
        try {
            if (httpClient != null) {
                httpClient.close();
            }
        } catch (IOException e) {
            logger.error("Error closing HTTP client: {}", e.getMessage());
        }
    }
    
    /**
     * Request configuration for Nova models
     */
    public static class NovaRequest {
        private double temperature = 0.1;
        private double topP = 0.9;
        private List<String> stopSequences;
        private Map<String, Object> additionalParams = new HashMap<>();
        
        // Constructors
        public NovaRequest() {}
        
        public NovaRequest(double temperature, double topP) {
            this.temperature = temperature;
            this.topP = topP;
        }
        
        // Builder pattern
        public static NovaRequest builder() {
            return new NovaRequest();
        }
        
        public NovaRequest temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }
        
        public NovaRequest topP(double topP) {
            this.topP = topP;
            return this;
        }
        
        public NovaRequest stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }
        
        public NovaRequest addParam(String key, Object value) {
            this.additionalParams.put(key, value);
            return this;
        }
        
        // Getters
        public double getTemperature() { return temperature; }
        public double getTopP() { return topP; }
        public List<String> getStopSequences() { return stopSequences; }
        public Map<String, Object> getAdditionalParams() { return additionalParams; }
    }
    
    /**
     * Response from Nova models
     */
    public static class NovaResponse {
        private String responseText;
        private int inputTokens;
        private int outputTokens;
        private int totalTokens;
        private double estimatedCost;
        private String modelId;
        private boolean successful;
        private long timestamp;
        private String errorMessage;
        private Map<String, Object> metadata = new HashMap<>();
        
        // Constructors
        public NovaResponse() {}
        
        // Builder pattern
        public static NovaResponseBuilder builder() {
            return new NovaResponseBuilder();
        }
        
        // Getters and setters
        public String getResponseText() { return responseText; }
        public void setResponseText(String responseText) { this.responseText = responseText; }
        
        public int getInputTokens() { return inputTokens; }
        public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }
        
        public int getOutputTokens() { return outputTokens; }
        public void setOutputTokens(int outputTokens) { this.outputTokens = outputTokens; }
        
        public int getTotalTokens() { return totalTokens; }
        public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
        
        public double getEstimatedCost() { return estimatedCost; }
        public void setEstimatedCost(double estimatedCost) { this.estimatedCost = estimatedCost; }
        
        public String getModelId() { return modelId; }
        public void setModelId(String modelId) { this.modelId = modelId; }
        
        public boolean isSuccessful() { return successful; }
        public void setSuccessful(boolean successful) { this.successful = successful; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        
        public static class NovaResponseBuilder {
            private NovaResponse response = new NovaResponse();
            
            public NovaResponseBuilder responseText(String responseText) {
                response.setResponseText(responseText);
                return this;
            }
            
            public NovaResponseBuilder inputTokens(int inputTokens) {
                response.setInputTokens(inputTokens);
                return this;
            }
            
            public NovaResponseBuilder outputTokens(int outputTokens) {
                response.setOutputTokens(outputTokens);
                return this;
            }
            
            public NovaResponseBuilder totalTokens(int totalTokens) {
                response.setTotalTokens(totalTokens);
                return this;
            }
            
            public NovaResponseBuilder estimatedCost(double estimatedCost) {
                response.setEstimatedCost(estimatedCost);
                return this;
            }
            
            public NovaResponseBuilder modelId(String modelId) {
                response.setModelId(modelId);
                return this;
            }
            
            public NovaResponseBuilder successful(boolean successful) {
                response.setSuccessful(successful);
                return this;
            }
            
            public NovaResponseBuilder timestamp(long timestamp) {
                response.setTimestamp(timestamp);
                return this;
            }
            
            public NovaResponseBuilder errorMessage(String errorMessage) {
                response.setErrorMessage(errorMessage);
                return this;
            }
            
            public NovaResponse build() {
                return response;
            }
        }
        
        @Override
        public String toString() {
            return String.format("NovaResponse{model='%s', tokens=%d, cost=%.6f, successful=%s}", 
                               modelId, totalTokens, estimatedCost, successful);
        }
    }
    
    /**
     * Custom exception for Nova invocation errors
     */
    public static class NovaInvokerException extends Exception {
        public NovaInvokerException(String message) {
            super(message);
        }
        
        public NovaInvokerException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}