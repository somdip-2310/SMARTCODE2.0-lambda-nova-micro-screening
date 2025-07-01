package com.somdiproy.lambda.screening.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Response model for Nova Micro screening Lambda
 * Contains screened files ready for next analysis stage
 */
public class ScreeningResponse {
    
    @JsonProperty("status")
    private String status; // success, partial_success, error
    
    @JsonProperty("analysisId")
    private String analysisId;
    
    @JsonProperty("sessionId")
    private String sessionId;
    
    @JsonProperty("stage")
    private String stage = "screening";
    
    @JsonProperty("files")
    private List<ScreenedFile> files = new ArrayList<>();
    
    @JsonProperty("summary")
    private ScreeningSummary summary;
    
    @JsonProperty("tokenUsage")
    private TokenUsage tokenUsage;
    
    @JsonProperty("processingTime")
    private ProcessingTime processingTime;
    
    @JsonProperty("errors")
    private List<ProcessingError> errors = new ArrayList<>();
    
    @JsonProperty("warnings")
    private List<String> warnings = new ArrayList<>();
    
    @JsonProperty("metadata")
    private Map<String, Object> metadata = new HashMap<>();
    
    @JsonProperty("timestamp")
    private Long timestamp;
    
    @JsonProperty("nextStage")
    private String nextStage = "detection";
    
    // Constructors
    public ScreeningResponse() {
        this.timestamp = System.currentTimeMillis();
        this.stage = "screening";
        this.nextStage = "detection";
    }
    
    public ScreeningResponse(String analysisId, String sessionId) {
        this();
        this.analysisId = analysisId;
        this.sessionId = sessionId;
    }
    
    // Factory methods for different response types
    public static ScreeningResponse success(String analysisId, String sessionId, List<ScreenedFile> files) {
        ScreeningResponse response = new ScreeningResponse(analysisId, sessionId);
        response.setStatus("success");
        response.setFiles(files);
        return response;
    }
    
    public static ScreeningResponse partialSuccess(String analysisId, String sessionId, 
                                                 List<ScreenedFile> files, List<ProcessingError> errors) {
        ScreeningResponse response = new ScreeningResponse(analysisId, sessionId);
        response.setStatus("partial_success");
        response.setFiles(files);
        response.setErrors(errors);
        return response;
    }
    
    public static ScreeningResponse error(String analysisId, String sessionId, String errorMessage) {
        ScreeningResponse response = new ScreeningResponse(analysisId, sessionId);
        response.setStatus("error");
        response.addError("SCREENING_FAILED", errorMessage, null);
        return response;
    }
    
    // Getters and Setters
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getAnalysisId() {
        return analysisId;
    }
    
    public void setAnalysisId(String analysisId) {
        this.analysisId = analysisId;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public String getStage() {
        return stage;
    }
    
    public void setStage(String stage) {
        this.stage = stage;
    }
    
    public List<ScreenedFile> getFiles() {
        return files;
    }
    
    public void setFiles(List<ScreenedFile> files) {
        this.files = files;
    }
    
    public ScreeningSummary getSummary() {
        return summary;
    }
    
    public void setSummary(ScreeningSummary summary) {
        this.summary = summary;
    }
    
    public TokenUsage getTokenUsage() {
        return tokenUsage;
    }
    
    public void setTokenUsage(TokenUsage tokenUsage) {
        this.tokenUsage = tokenUsage;
    }
    
    public ProcessingTime getProcessingTime() {
        return processingTime;
    }
    
    public void setProcessingTime(ProcessingTime processingTime) {
        this.processingTime = processingTime;
    }
    
    public List<ProcessingError> getErrors() {
        return errors;
    }
    
    public void setErrors(List<ProcessingError> errors) {
        this.errors = errors;
    }
    
    public List<String> getWarnings() {
        return warnings;
    }
    
    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    public Long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getNextStage() {
        return nextStage;
    }
    
    public void setNextStage(String nextStage) {
        this.nextStage = nextStage;
    }
    
    // Utility methods
    public void addFile(ScreenedFile file) {
        if (this.files == null) {
            this.files = new ArrayList<>();
        }
        this.files.add(file);
    }
    
    public void addError(String code, String message, String filePath) {
        if (this.errors == null) {
            this.errors = new ArrayList<>();
        }
        this.errors.add(new ProcessingError(code, message, filePath));
    }
    
    public void addWarning(String warning) {
        if (this.warnings == null) {
            this.warnings = new ArrayList<>();
        }
        this.warnings.add(warning);
    }
    
    public void addMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }
    
    public boolean isSuccessful() {
        return "success".equals(status) || "partial_success".equals(status);
    }
    
    public int getFileCount() {
        return files != null ? files.size() : 0;
    }
    
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
    
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
    
    /**
     * Nested class for screened file data
     */
    public static class ScreenedFile {
        @JsonProperty("path")
        private String path;
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("content")
        private String content;
        
        @JsonProperty("optimizedContent")
        private String optimizedContent;
        
        @JsonProperty("size")
        private Long size;
        
        @JsonProperty("sha")
        private String sha;
        
        @JsonProperty("language")
        private String language;
        
        @JsonProperty("confidence")
        private Float confidence;
        
        @JsonProperty("complexity")
        private String complexity; // low, medium, high
        
        @JsonProperty("mimeType")
        private String mimeType;
        
        @JsonProperty("encoding")
        private String encoding;
        
        @JsonProperty("lineCount")
        private Integer lineCount;
        
        @JsonProperty("characterCount")
        private Integer characterCount;
        
        @JsonProperty("estimatedTokens")
        private Integer estimatedTokens;
        
        @JsonProperty("screeningScore")
        private Float screeningScore;
        
        @JsonProperty("metadata")
        private Map<String, Object> metadata = new HashMap<>();
        
        // Constructors
        public ScreenedFile() {}
        
        public ScreenedFile(String path, String name, String content, Long size, 
                          String language, Float confidence) {
            this.path = path;
            this.name = name;
            this.content = content;
            this.size = size;
            this.language = language;
            this.confidence = confidence;
            this.lineCount = content != null ? content.split("\n").length : 0;
            this.characterCount = content != null ? content.length() : 0;
            this.estimatedTokens = characterCount != null ? characterCount / 4 : 0; // Rough estimate
        }
        
        // Getters and Setters (generated for brevity)
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getContent() { return content; }
        public void setContent(String content) { 
            this.content = content;
            if (content != null) {
                this.lineCount = content.split("\n").length;
                this.characterCount = content.length();
                this.estimatedTokens = content.length() / 4;
            }
        }
        
        public String getOptimizedContent() { return optimizedContent; }
        public void setOptimizedContent(String optimizedContent) { this.optimizedContent = optimizedContent; }
        
        public Long getSize() { return size; }
        public void setSize(Long size) { this.size = size; }
        
        public String getSha() { return sha; }
        public void setSha(String sha) { this.sha = sha; }
        
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        
        public Float getConfidence() { return confidence; }
        public void setConfidence(Float confidence) { this.confidence = confidence; }
        
        public String getComplexity() { return complexity; }
        public void setComplexity(String complexity) { this.complexity = complexity; }
        
        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }
        
        public String getEncoding() { return encoding; }
        public void setEncoding(String encoding) { this.encoding = encoding; }
        
        public Integer getLineCount() { return lineCount; }
        public void setLineCount(Integer lineCount) { this.lineCount = lineCount; }
        
        public Integer getCharacterCount() { return characterCount; }
        public void setCharacterCount(Integer characterCount) { this.characterCount = characterCount; }
        
        public Integer getEstimatedTokens() { return estimatedTokens; }
        public void setEstimatedTokens(Integer estimatedTokens) { this.estimatedTokens = estimatedTokens; }
        
        public Float getScreeningScore() { return screeningScore; }
        public void setScreeningScore(Float screeningScore) { this.screeningScore = screeningScore; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        
        @Override
        public String toString() {
            return String.format("ScreenedFile{path='%s', language='%s', confidence=%.2f, complexity='%s'}", 
                               path, language, confidence, complexity);
        }
    }
    
    /**
     * Summary statistics for screening process
     */
    public static class ScreeningSummary {
        @JsonProperty("inputFiles")
        private Integer inputFiles;
        
        @JsonProperty("processedFiles")
        private Integer processedFiles;
        
        @JsonProperty("validFiles")
        private Integer validFiles;
        
        @JsonProperty("skippedFiles")
        private Integer skippedFiles;
        
        @JsonProperty("errorFiles")
        private Integer errorFiles;
        
        @JsonProperty("languageDistribution")
        private Map<String, Integer> languageDistribution = new HashMap<>();
        
        @JsonProperty("complexityDistribution")
        private Map<String, Integer> complexityDistribution = new HashMap<>();
        
        @JsonProperty("averageConfidence")
        private Float averageConfidence;
        
        @JsonProperty("totalSize")
        private Long totalSize;
        
        @JsonProperty("totalLines")
        private Integer totalLines;
        
        // Constructors and getters/setters
        public ScreeningSummary() {}
        
        public Integer getInputFiles() { return inputFiles; }
        public void setInputFiles(Integer inputFiles) { this.inputFiles = inputFiles; }
        
        public Integer getProcessedFiles() { return processedFiles; }
        public void setProcessedFiles(Integer processedFiles) { this.processedFiles = processedFiles; }
        
        public Integer getValidFiles() { return validFiles; }
        public void setValidFiles(Integer validFiles) { this.validFiles = validFiles; }
        
        public Integer getSkippedFiles() { return skippedFiles; }
        public void setSkippedFiles(Integer skippedFiles) { this.skippedFiles = skippedFiles; }
        
        public Integer getErrorFiles() { return errorFiles; }
        public void setErrorFiles(Integer errorFiles) { this.errorFiles = errorFiles; }
        
        public Map<String, Integer> getLanguageDistribution() { return languageDistribution; }
        public void setLanguageDistribution(Map<String, Integer> languageDistribution) { this.languageDistribution = languageDistribution; }
        
        public Map<String, Integer> getComplexityDistribution() { return complexityDistribution; }
        public void setComplexityDistribution(Map<String, Integer> complexityDistribution) { this.complexityDistribution = complexityDistribution; }
        
        public Float getAverageConfidence() { return averageConfidence; }
        public void setAverageConfidence(Float averageConfidence) { this.averageConfidence = averageConfidence; }
        
        public Long getTotalSize() { return totalSize; }
        public void setTotalSize(Long totalSize) { this.totalSize = totalSize; }
        
        public Integer getTotalLines() { return totalLines; }
        public void setTotalLines(Integer totalLines) { this.totalLines = totalLines; }
    }
    
    /**
     * Token usage tracking
     */
    public static class TokenUsage {
        @JsonProperty("inputTokens")
        private Integer inputTokens;
        
        @JsonProperty("outputTokens")
        private Integer outputTokens;
        
        @JsonProperty("totalTokens")
        private Integer totalTokens;
        
        @JsonProperty("estimatedCost")
        private Double estimatedCost;
        
        @JsonProperty("modelCalls")
        private Integer modelCalls;
        
        // Constructors and getters/setters
        public TokenUsage() {}
        
        public TokenUsage(Integer inputTokens, Integer outputTokens, Double estimatedCost) {
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.totalTokens = inputTokens + outputTokens;
            this.estimatedCost = estimatedCost;
        }
        
        public Integer getInputTokens() { return inputTokens; }
        public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }
        
        public Integer getOutputTokens() { return outputTokens; }
        public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }
        
        public Integer getTotalTokens() { return totalTokens; }
        public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }
        
        public Double getEstimatedCost() { return estimatedCost; }
        public void setEstimatedCost(Double estimatedCost) { this.estimatedCost = estimatedCost; }
        
        public Integer getModelCalls() { return modelCalls; }
        public void setModelCalls(Integer modelCalls) { this.modelCalls = modelCalls; }
    }
    
    /**
     * Processing time metrics
     */
    public static class ProcessingTime {
        @JsonProperty("startTime")
        private Long startTime;
        
        @JsonProperty("endTime") 
        private Long endTime;
        
        @JsonProperty("totalDurationMs")
        private Long totalDurationMs;
        
        @JsonProperty("screeningDurationMs")
        private Long screeningDurationMs;
        
        @JsonProperty("novaDurationMs")
        private Long novaDurationMs;
        
        // Constructors and getters/setters
        public ProcessingTime() {
            this.startTime = System.currentTimeMillis();
        }
        
        public void markEnd() {
            this.endTime = System.currentTimeMillis();
            this.totalDurationMs = this.endTime - this.startTime;
        }
        
        public Long getStartTime() { return startTime; }
        public void setStartTime(Long startTime) { this.startTime = startTime; }
        
        public Long getEndTime() { return endTime; }
        public void setEndTime(Long endTime) { this.endTime = endTime; }
        
        public Long getTotalDurationMs() { return totalDurationMs; }
        public void setTotalDurationMs(Long totalDurationMs) { this.totalDurationMs = totalDurationMs; }
        
        public Long getScreeningDurationMs() { return screeningDurationMs; }
        public void setScreeningDurationMs(Long screeningDurationMs) { this.screeningDurationMs = screeningDurationMs; }
        
        public Long getNovaDurationMs() { return novaDurationMs; }
        public void setNovaDurationMs(Long novaDurationMs) { this.novaDurationMs = novaDurationMs; }
    }
    
    /**
     * Processing error details
     */
    public static class ProcessingError {
        @JsonProperty("code")
        private String code;
        
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("filePath")
        private String filePath;
        
        @JsonProperty("timestamp")
        private Long timestamp;
        
        @JsonProperty("severity")
        private String severity = "ERROR";
        
        // Constructors
        public ProcessingError() {
            this.timestamp = System.currentTimeMillis();
        }
        
        public ProcessingError(String code, String message, String filePath) {
            this();
            this.code = code;
            this.message = message;
            this.filePath = filePath;
        }
        
        // Getters and setters
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        
        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
        
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        
        @Override
        public String toString() {
            return String.format("ProcessingError{code='%s', message='%s', file='%s'}", 
                               code, message, filePath);
        }
    }
    
    @Override
    public String toString() {
        return String.format("ScreeningResponse{status='%s', files=%d, errors=%d, stage='%s'}", 
                           status, getFileCount(), errors.size(), stage);
    }
}