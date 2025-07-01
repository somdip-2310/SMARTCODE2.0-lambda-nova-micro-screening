package com.somdiproy.lambda.screening;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.somdiproy.lambda.screening.model.ScreeningRequest;
import com.somdiproy.lambda.screening.model.ScreeningResponse;
import com.somdiproy.lambda.screening.model.ScreeningResponse.ScreenedFile;
import com.somdiproy.lambda.screening.service.NovaInvokerService;
import com.somdiproy.lambda.screening.util.TokenOptimizer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;

/**
 * Lambda function for Nova Micro file screening and filtering
 * First tier of the three-tier analysis system
 */
public class ScreeningHandler implements RequestHandler<ScreeningRequest, ScreeningResponse> {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NovaInvokerService novaInvoker;
    
    // Configuration from environment variables
    private static final String MODEL_ID = System.getenv("MODEL_ID"); // us.amazon.nova-micro-v1:0
    private static final String BEDROCK_REGION = System.getenv("BEDROCK_REGION"); // us-east-1
    private static final int MAX_TOKENS = Integer.parseInt(System.getenv("MAX_TOKENS")); // 500
    
    // File filtering patterns
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        ".java", ".py", ".js", ".ts", ".cs", ".go", ".rb", ".php", ".cpp", ".c", ".kt", ".scala", ".swift"
    );
    
    private static final Set<String> EXCLUDED_PATTERNS = Set.of(
        ".*/test/.*", ".*/tests/.*", ".*/node_modules/.*", ".*/__pycache__/.*", 
        ".*/target/.*", ".*/build/.*", ".*/dist/.*", ".*/.git/.*", ".*/vendor/.*", 
        ".*/coverage/.*", ".*\\.min\\.(js|css)$", ".*\\.generated\\.(java|cs)$"
    );
    
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    
    public ScreeningHandler() {
        this.novaInvoker = new NovaInvokerService(BEDROCK_REGION);
    }

    @Override
    public ScreeningResponse handleRequest(ScreeningRequest request, Context context) {
        LambdaLogger logger = context.getLogger();
        logger.log("🔍 Starting Nova Micro screening process");
        
        ScreeningResponse.ProcessingTime processingTime = new ScreeningResponse.ProcessingTime();
        
        try {
            // Validate input
            if (!request.isValid()) {
                return ScreeningResponse.error(request.getAnalysisId(), request.getSessionId(), 
                                             "Invalid request: missing required fields");
            }
            
            List<ScreeningRequest.FileInput> files = request.getFiles();
            logger.log(String.format("📁 Processing %d files for analysis: %s", 
                       files.size(), request.getAnalysisId()));
            
            // Stage 1: Basic file filtering
            List<ScreeningRequest.FileInput> candidates = files.stream()
                .filter(this::isValidFileType)
                .filter(this::isNotExcludedPath)
                .filter(this::isWithinSizeLimit)
                .collect(Collectors.toList());
            
            logger.log(String.format("📋 After basic filtering: %d/%d files remain", 
                       candidates.size(), files.size()));
            
            // Stage 2: Language detection and content analysis using Nova Micro
            List<ScreeningResponse.ScreenedFile> screenedFiles = performNovaScreening(candidates, logger);
            
            // Stage 3: Generate summary and metrics
            ScreeningResponse.ScreeningSummary summary = generateSummary(files, screenedFiles);
            ScreeningResponse.TokenUsage tokenUsage = calculateTokenUsage(screenedFiles);
            
            processingTime.markEnd();
            
            // Stage 4: Build response
            ScreeningResponse response = ScreeningResponse.success(
                request.getAnalysisId(), request.getSessionId(), screenedFiles);
            response.setSummary(summary);
            response.setTokenUsage(tokenUsage);
            response.setProcessingTime(processingTime);
            
            logger.log(String.format("✅ Screening complete: %d files ready for analysis", 
                       screenedFiles.size()));
            return response;
            
        } catch (Exception e) {
            logger.log("💥 Error in screening: " + e.getMessage());
            e.printStackTrace();
            
            processingTime.markEnd();
            ScreeningResponse errorResponse = ScreeningResponse.error(
                request.getAnalysisId(), request.getSessionId(), e.getMessage());
            errorResponse.setProcessingTime(processingTime);
            return errorResponse;
        }
    }
    
    /**
     * Parse file information from input
     */
    private FileCandidate parseFileFromInput(Map<String, Object> fileData) {
        return new FileCandidate(
            (String) fileData.get("path"),
            (String) fileData.get("name"),
            (String) fileData.get("content"),
            ((Number) fileData.get("size")).longValue(),
            (String) fileData.get("language")
        );
    }
    
    /**
     * Check if file has supported extension
     */
    private boolean isValidFileType(ScreeningRequest.FileInput file) {
        String extension = getFileExtension(file.getPath());
        return SUPPORTED_EXTENSIONS.contains(extension.toLowerCase());
    }
    
    /**
     * Check if file path should be excluded
     */
    private boolean isNotExcludedPath(ScreeningRequest.FileInput file) {
        return EXCLUDED_PATTERNS.stream()
            .noneMatch(pattern -> Pattern.matches(pattern, file.getPath()));
    }
    
    /**
     * Check if file is within size limits
     */
    private boolean isWithinSizeLimit(ScreeningRequest.FileInput file) {
        return file.getSize() <= MAX_FILE_SIZE;
    }
    
    /**
     * Perform Nova Micro screening for language detection and content validation
     */
    private List<ScreeningResponse.ScreenedFile> performNovaScreening(List<ScreeningRequest.FileInput> candidates, LambdaLogger logger) {
        List<ScreeningResponse.ScreenedFile> screenedFiles = new ArrayList<>();
        
        for (ScreeningRequest.FileInput candidate : candidates) {
            try {
                String optimizedContent = TokenOptimizer.optimizeForScreening(candidate.getContent(), 
                    candidate.getLanguage() != null ? candidate.getLanguage() : detectLanguageFromExtension(candidate.getPath()));
                
                ScreeningResult result = callNovaMicro(candidate, optimizedContent, logger);
                
                if (result.isValid) {
                    ScreeningResponse.ScreenedFile screenedFile = new ScreeningResponse.ScreenedFile();
                    screenedFile.setPath(candidate.getPath());
                    screenedFile.setName(candidate.getName());
                    screenedFile.setContent(candidate.getContent());
                    screenedFile.setOptimizedContent(optimizedContent);
                    screenedFile.setSize(candidate.getSize());
                    screenedFile.setLanguage(result.detectedLanguage);
                    screenedFile.setConfidence(result.confidence);
                    screenedFile.setComplexity(result.complexity);
                    screenedFile.setSha(candidate.getSha());
                    screenedFile.setMimeType(candidate.getMimeType());
                    screenedFile.setEncoding(candidate.getEncoding());
                    
                    screenedFiles.add(screenedFile);
                }
                
                // Add small delay to respect rate limits
                Thread.sleep(100);
                
            } catch (Exception e) {
                logger.log("⚠️ Failed to screen file: " + candidate.getPath() + " - " + e.getMessage());
            }
        }
        
        return screenedFiles;
    }
    
    /**
     * Optimize content for Nova Micro analysis (token reduction)
     */
    private String optimizeContentForScreening(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        // Remove comments and extra whitespace for screening
        String optimized = content
            .replaceAll("/\\*.*?\\*/", "") // Remove block comments
            .replaceAll("//.*", "") // Remove line comments
            .replaceAll("#.*", "") // Remove Python/shell comments
            .replaceAll("\\s+", " ") // Collapse whitespace
            .trim();
        
        // Limit to first 1000 characters for screening
        return optimized.length() > 1000 ? optimized.substring(0, 1000) : optimized;
    }
    
    /**
     * Call Nova Micro model for content analysis
     */
    private ScreeningResult callNovaMicro(ScreeningRequest.FileInput file, String optimizedContent, LambdaLogger logger) {
        try {
            String prompt = buildScreeningPrompt(file, optimizedContent);
            
            NovaInvokerService.NovaRequest request = NovaInvokerService.NovaRequest.builder()
                .temperature(0.1)
                .topP(0.9);
            
            NovaInvokerService.NovaResponse response = novaInvoker.invokeNovaMicro(prompt, request);
            
            if (response.isSuccessful()) {
                return parseNovaResponse(response.getResponseText(), file);
            } else {
                logger.log("❌ Nova Micro call failed: " + response.getErrorMessage());
                return createFallbackResult(file);
            }
            
        } catch (Exception e) {
            logger.log("❌ Nova Micro call failed for " + file.getPath() + ": " + e.getMessage());
            return createFallbackResult(file);
        }
    }
    
    /**
     * Create fallback result when Nova call fails
     */
    private ScreeningResult createFallbackResult(ScreeningRequest.FileInput file) {
        String extension = getFileExtension(file.getPath());
        String language = mapExtensionToLanguage(extension);
        return new ScreeningResult(true, language, 0.8f, "medium");
    }
    
    /**
     * Build screening prompt for Nova Micro
     */
    private String buildScreeningPrompt(ScreeningRequest.FileInput file, String content) {
        return String.format("""
            Analyze this code file for screening purposes:
            
            File: %s
            Size: %d bytes
            Content preview: %s
            
            Please provide:
            1. Programming language (java/python/javascript/typescript/csharp/go/ruby/php/cpp/c/other)
            2. Confidence level (0.0-1.0)
            3. Code complexity (low/medium/high)
            4. Is this a valid source code file? (yes/no)
            
            Respond in this exact format:
            LANGUAGE: [language]
            CONFIDENCE: [0.0-1.0]
            COMPLEXITY: [low/medium/high]
            VALID: [yes/no]
            REASON: [brief explanation]
            """, 
            file.getName(), file.getSize(), content.substring(0, Math.min(500, content.length()))
        );
    }
    
    /**
     * Call Bedrock API
     */
    private String callBedrockAPI(Map<String, Object> requestBody) throws Exception {
        String endpoint = String.format("https://bedrock-runtime.%s.amazonaws.com/model/%s/converse", 
                                       BEDROCK_REGION, MODEL_ID);
        
        HttpPost request = new HttpPost(endpoint);
        request.setHeader("Content-Type", "application/json");
        request.setHeader("Accept", "application/json");
        
        String json = objectMapper.writeValueAsString(requestBody);
        request.setEntity(new StringEntity(json));
        
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            return EntityUtils.toString(response.getEntity());
        }
    }
    
    /**
     * Parse Nova Micro response
     */
    private ScreeningResult parseNovaResponse(String response, FileCandidate file) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> output = (Map<String, Object>) responseMap.get("output");
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) output.get("message");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content = (List<Map<String, Object>>) message.get("content");
            
            String text = (String) content.get(0).get("text");
            
            // Parse the structured response
            String language = extractValue(text, "LANGUAGE:");
            float confidence = Float.parseFloat(extractValue(text, "CONFIDENCE:"));
            String complexity = extractValue(text, "COMPLEXITY:");
            boolean isValid = "yes".equalsIgnoreCase(extractValue(text, "VALID:"));
            
            return new ScreeningResult(isValid, language, confidence, complexity);
            
        } catch (Exception e) {
            // Fallback: Use basic file extension detection
            String extension = getFileExtension(file.path);
            String language = mapExtensionToLanguage(extension);
            return new ScreeningResult(true, language, 0.8f, "medium");
        }
    }
    
    /**
     * Extract value from structured response
     */
    private String extractValue(String text, String key) {
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.startsWith(key)) {
                return line.substring(key.length()).trim();
            }
        }
        return "unknown";
    }
    
    /**
     * Get file extension
     */
    private String getFileExtension(String path) {
        int lastDot = path.lastIndexOf('.');
        return (lastDot > 0 && lastDot < path.length() - 1) ? path.substring(lastDot) : "";
    }
    
    /**
     * Map file extension to programming language
     */
    private String mapExtensionToLanguage(String extension) {
        Map<String, String> extensionMap = Map.of(
            ".java", "java",
            ".py", "python",
            ".js", "javascript",
            ".ts", "typescript",
            ".cs", "csharp",
            ".go", "go",
            ".rb", "ruby",
            ".php", "php",
            ".cpp", "cpp",
            ".c", "c"
        );
        return extensionMap.getOrDefault(extension.toLowerCase(), "unknown");
    }
    
    /**
     * Calculate token usage for the screening process
     */
    private Map<String, Object> calculateTokenUsage(List<ScreenedFile> files) {
        int totalTokens = files.size() * 100; // Approximate tokens per file for screening
        double cost = (totalTokens / 1_000_000.0) * 0.0075; // Nova Micro pricing
        
        Map<String, Object> usage = new HashMap<>();
        usage.put("inputTokens", totalTokens);
        usage.put("outputTokens", files.size() * 50); // Response tokens
        usage.put("totalTokens", totalTokens + (files.size() * 50));
        usage.put("estimatedCost", cost);
        return usage;
    }
    
    // Helper classes remain at bottom
    private static class ScreeningResult {
        final boolean isValid;
        final String detectedLanguage;
        final float confidence;
        final String complexity;
        
        ScreeningResult(boolean isValid, String detectedLanguage, float confidence, String complexity) {
            this.isValid = isValid;
            this.detectedLanguage = detectedLanguage;
            this.confidence = confidence;
            this.complexity = complexity;
        }
    }
}