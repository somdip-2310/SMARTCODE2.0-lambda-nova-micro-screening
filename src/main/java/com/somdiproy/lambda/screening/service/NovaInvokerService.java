package com.somdiproy.lambda.screening.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException;
import software.amazon.awssdk.services.bedrockruntime.model.BedrockRuntimeException;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ModelTimeoutException;
import software.amazon.awssdk.services.bedrockruntime.model.ResourceNotFoundException;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ValidationException;

/**
 * Service for invoking Amazon Nova models via Bedrock Converse API
 * Handles authentication, rate limiting, and response parsing
 */
public class NovaInvokerService {
    
    private static final Logger logger = LoggerFactory.getLogger(NovaInvokerService.class);
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String bedrockRegion;
    private final Map<String, Long> lastCallTime = new HashMap<>();
    private final Map<String, Integer> callCount = new HashMap<>();
    
    // Rate limiting: Nova Micro has 5 requests per second limit
    private static final long MIN_CALL_INTERVAL_MS = 200; // 5 calls per second
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    
    public NovaInvokerService(String bedrockRegion) {
        this.bedrockRegion = bedrockRegion != null ? bedrockRegion : "us-east-1";
    }
    
    /**
     * Invoke Nova Micro model for file screening
     */
    public NovaResponse invokeNovaMicro(String prompt, NovaRequest request) throws NovaInvokerException {
        return invokeNova("amazon.nova-micro-v1:0", prompt, request, 500);
    }
    
    /**
     * Invoke Nova Lite model for issue detection  
     */
    public NovaResponse invokeNovaLite(String prompt, NovaRequest request) throws NovaInvokerException {
        return invokeNova("amazon.nova-lite-v1:0", prompt, request, 4000);
    }
    
    /**
     * Invoke Nova Premier model for suggestion generation
     */
    public NovaResponse invokeNovaPremier(String prompt, NovaRequest request) throws NovaInvokerException {
        return invokeNova("amazon.nova-pro-v1:0", prompt, request, 8000);
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
            
            // Make API call with retries using Converse API
            NovaResponse response = callBedrockWithRetries(modelId, prompt, request, maxTokens);
            
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
     * Call Bedrock with retries using Converse API
     */
    private NovaResponse callBedrockWithRetries(String modelId, String prompt, 
                                               NovaRequest request, int maxTokens) 
            throws NovaInvokerException {
        
        Exception lastException = null;
        
        // Initialize AWS SDK client
        BedrockRuntimeClient bedrockClient = BedrockRuntimeClient.builder()
                .region(Region.of(bedrockRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                logger.debug("Attempt {} to call Nova model {} using Converse API", attempt, modelId);
                
                // Create message content
                ContentBlock contentBlock = ContentBlock.builder()
                        .text(prompt)
                        .build();
                
                // Create user message
                Message userMessage = Message.builder()
                        .role(ConversationRole.USER)
                        .content(contentBlock)
                        .build();
                
                // Create inference configuration
                InferenceConfiguration.Builder inferenceConfigBuilder = InferenceConfiguration.builder()
                        .maxTokens(maxTokens)
                        .temperature(request != null ? (float) request.getTemperature() : 0.1f)
                        .topP(request != null ? (float) request.getTopP() : 0.9f);
                
                // Add stop sequences if provided
                if (request != null && request.getStopSequences() != null && !request.getStopSequences().isEmpty()) {
                    inferenceConfigBuilder.stopSequences(request.getStopSequences());
                }
                
                InferenceConfiguration inferenceConfig = inferenceConfigBuilder.build();
                
                // Create Converse request
                ConverseRequest converseRequest = ConverseRequest.builder()
                        .modelId(modelId)
                        .messages(userMessage)
                        .inferenceConfig(inferenceConfig)
                        .build();
                
                // Make the API call using Converse
                ConverseResponse response = bedrockClient.converse(converseRequest);
                
                // Parse response
                return parseConverseResponse(response, modelId);
                
            } catch (ResourceNotFoundException e) {
                // Model not found - don't retry
                throw new NovaInvokerException("Model not found: " + modelId, e);
            } catch (AccessDeniedException e) {
                // Permission issue - don't retry
                throw new NovaInvokerException("Access denied to model: " + modelId, e);
            } catch (ValidationException e) {
                // Invalid request - don't retry
                throw new NovaInvokerException("Invalid request: " + e.getMessage(), e);
            } catch (ThrottlingException e) {
                // Rate limit exceeded - retry with backoff
                logger.warn("Rate limit exceeded for {}, attempt {}", modelId, attempt);
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new NovaInvokerException("Interrupted during retry", ie);
                    }
                }
            } catch (ModelTimeoutException e) {
                // Model timeout - retry
                logger.warn("Model timeout for {}, attempt {}", modelId, attempt);
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new NovaInvokerException("Interrupted during retry", ie);
                    }
                }
            } catch (BedrockRuntimeException e) {
                // General Bedrock service error
                logger.warn("Bedrock service error for {}, attempt {}: {}", modelId, attempt, e.getMessage());
                lastException = e;
                
                // Retry on 5xx errors or throttling
                if (attempt < MAX_RETRIES && (e.statusCode() >= 500 || e.statusCode() == 429)) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new NovaInvokerException("Interrupted during retry", ie);
                    }
                } else {
                    throw new NovaInvokerException("Bedrock service error: " + e.getMessage(), e);
                }
            } catch (SdkServiceException e) {
                // AWS service error - check status code
                logger.error("AWS service error for {}: {}", modelId, e.getMessage());
                
                // Retry on 5xx errors
                if (attempt < MAX_RETRIES && e.statusCode() >= 500) {
                    lastException = e;
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new NovaInvokerException("Interrupted during retry", ie);
                    }
                } else {
                    throw new NovaInvokerException("AWS service error: " + e.getMessage(), e);
                }
            } catch (SdkClientException e) {
                // Client-side error (network, config, etc.)
                logger.error("Client error for {}: {}", modelId, e.getMessage());
                lastException = e;
                
                // Retry on network errors
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new NovaInvokerException("Interrupted during retry", ie);
                    }
                } else {
                    throw new NovaInvokerException("Client error: " + e.getMessage(), e);
                }
            } catch (Exception e) {
                // Unexpected error - don't retry
                logger.error("Unexpected error for {}: {}", modelId, e.getMessage());
                throw new NovaInvokerException("Unexpected error: " + e.getMessage(), e);
            }
        }
        
        throw new NovaInvokerException("All retry attempts failed", lastException);
    }
    
    /**
     * Parse Converse API response
     */
    private NovaResponse parseConverseResponse(ConverseResponse response, String modelId) throws NovaInvokerException {
        try {
            // Extract response text from Converse response
            String responseText = "";
            ConverseOutput output = response.output();
            
            if (output != null && output.message() != null) {
                Message outputMessage = output.message();
                if (outputMessage.content() != null && !outputMessage.content().isEmpty()) {
                    ContentBlock firstContent = outputMessage.content().get(0);
                    if (firstContent.text() != null) {
                        responseText = firstContent.text();
                    }
                }
            }
            
            // Extract token usage
            TokenUsage usage = response.usage();
            int inputTokens = usage != null ? usage.inputTokens() : 0;
            int outputTokens = usage != null ? usage.outputTokens() : 0;
            int totalTokens = usage != null ? usage.totalTokens() : (inputTokens + outputTokens);
            
            // Calculate cost based on model
            double cost = calculateCost(modelId, inputTokens, outputTokens);
            
            // Extract additional metadata if available
            String stopReason = response.stopReason() != null ? 
                response.stopReason().toString() : "COMPLETE";
            
            NovaResponse novaResponse = NovaResponse.builder()
                .responseText(responseText)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(totalTokens)
                .estimatedCost(cost)
                .modelId(modelId)
                .successful(true)
                .timestamp(Instant.now().toEpochMilli())
                .build();
            
            // Add stop reason to metadata
            novaResponse.getMetadata().put("stopReason", stopReason);
            
            return novaResponse;
                
        } catch (Exception e) {
            throw new NovaInvokerException("Failed to parse Converse response: " + e.getMessage(), e);
        }
    }
    
    /**
     * Calculate cost based on Nova model pricing
     */
    private double calculateCost(String modelId, int inputTokens, int outputTokens) {
        double costPer1MTokens;
        
        switch (modelId) {
            case "amazon.nova-micro-v1:0":
                costPer1MTokens = 0.0075; // $0.0075 per 1M tokens
                break;
            case "amazon.nova-lite-v1:0":
                costPer1MTokens = 0.015; // $0.015 per 1M tokens
                break;
            case "amazon.nova-pro-v1:0":
            case "amazon.nova-premier-v1:0":
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