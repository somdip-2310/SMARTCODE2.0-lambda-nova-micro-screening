package com.somdiproy.lambda.screening.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Request model for Nova Micro screening Lambda
 * Matches the input structure from Spring Boot orchestrator
 */
public class ScreeningRequest {
    
    @JsonProperty("sessionId")
    private String sessionId;
    
    @JsonProperty("analysisId") 
    private String analysisId;
    
    @JsonProperty("repository")
    private String repository;
    
    @JsonProperty("branch")
    private String branch;
    
    @JsonProperty("files")
    private List<FileInput> files;
    
    @JsonProperty("stage")
    private String stage = "screening";
    
    @JsonProperty("timestamp")
    private Long timestamp;
    
    @JsonProperty("scanNumber")
    private Integer scanNumber;
    
    @JsonProperty("options")
    private ScreeningOptions options;
    
    // Constructors
    public ScreeningRequest() {
        this.timestamp = System.currentTimeMillis();
        this.stage = "screening";
    }
    
    public ScreeningRequest(String sessionId, String analysisId, List<FileInput> files) {
        this();
        this.sessionId = sessionId;
        this.analysisId = analysisId;
        this.files = files;
    }
    
    // Getters and Setters
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public String getAnalysisId() {
        return analysisId;
    }
    
    public void setAnalysisId(String analysisId) {
        this.analysisId = analysisId;
    }
    
    public String getRepository() {
        return repository;
    }
    
    public void setRepository(String repository) {
        this.repository = repository;
    }
    
    public String getBranch() {
        return branch;
    }
    
    public void setBranch(String branch) {
        this.branch = branch;
    }
    
    public List<FileInput> getFiles() {
        return files;
    }
    
    public void setFiles(List<FileInput> files) {
        this.files = files;
    }
    
    public String getStage() {
        return stage;
    }
    
    public void setStage(String stage) {
        this.stage = stage;
    }
    
    public Long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    
    public Integer getScanNumber() {
        return scanNumber;
    }
    
    public void setScanNumber(Integer scanNumber) {
        this.scanNumber = scanNumber;
    }
    
    public ScreeningOptions getOptions() {
        return options;
    }
    
    public void setOptions(ScreeningOptions options) {
        this.options = options;
    }
    
    // Validation methods
    public boolean isValid() {
        return sessionId != null && 
               analysisId != null && 
               files != null && 
               !files.isEmpty();
    }
    
    public int getFileCount() {
        return files != null ? files.size() : 0;
    }
    
    public long getTotalFileSize() {
        return files != null ? 
            files.stream().mapToLong(FileInput::getSize).sum() : 0;
    }
    
    /**
     * Nested class for file input data
     */
    public static class FileInput {
        @JsonProperty("path")
        private String path;
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("content")
        private String content;
        
        @JsonProperty("size")
        private Long size;
        
        @JsonProperty("sha")
        private String sha;
        
        @JsonProperty("language")
        private String language;
        
        @JsonProperty("mimeType")
        private String mimeType;
        
        @JsonProperty("encoding")
        private String encoding = "UTF-8";
        
        // Constructors
        public FileInput() {}
        
        public FileInput(String path, String name, String content, Long size) {
            this.path = path;
            this.name = name;
            this.content = content;
            this.size = size;
        }
        
        // Getters and Setters
        public String getPath() {
            return path;
        }
        
        public void setPath(String path) {
            this.path = path;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getContent() {
            return content;
        }
        
        public void setContent(String content) {
            this.content = content;
        }
        
        public Long getSize() {
            return size;
        }
        
        public void setSize(Long size) {
            this.size = size;
        }
        
        public String getSha() {
            return sha;
        }
        
        public void setSha(String sha) {
            this.sha = sha;
        }
        
        public String getLanguage() {
            return language;
        }
        
        public void setLanguage(String language) {
            this.language = language;
        }
        
        public String getMimeType() {
            return mimeType;
        }
        
        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }
        
        public String getEncoding() {
            return encoding;
        }
        
        public void setEncoding(String encoding) {
            this.encoding = encoding;
        }
        
        // Utility methods
        public String getFileExtension() {
            if (path == null) return "";
            int lastDot = path.lastIndexOf('.');
            return (lastDot > 0 && lastDot < path.length() - 1) ? 
                path.substring(lastDot) : "";
        }
        
        public boolean isValidSourceFile() {
            return content != null && 
                   content.trim().length() > 0 &&
                   size != null && 
                   size > 0 && 
                   size < 5 * 1024 * 1024; // 5MB limit
        }
        
        @Override
        public String toString() {
            return String.format("FileInput{path='%s', size=%d, language='%s'}", 
                               path, size, language);
        }
    }
    
    /**
     * Nested class for screening options
     */
    public static class ScreeningOptions {
        @JsonProperty("maxFileSize")
        private Long maxFileSize = 5 * 1024 * 1024L; // 5MB default
        
        @JsonProperty("maxFiles")
        private Integer maxFiles = 50;
        
        @JsonProperty("includeTests")
        private Boolean includeTests = false;
        
        @JsonProperty("includeDocs")
        private Boolean includeDocs = false;
        
        @JsonProperty("languageFilter")
        private List<String> languageFilter;
        
        @JsonProperty("confidenceThreshold")
        private Float confidenceThreshold = 0.7f;
        
        @JsonProperty("enableNovaAnalysis")
        private Boolean enableNovaAnalysis = true;
        
        // Constructors
        public ScreeningOptions() {}
        
        // Getters and Setters
        public Long getMaxFileSize() {
            return maxFileSize;
        }
        
        public void setMaxFileSize(Long maxFileSize) {
            this.maxFileSize = maxFileSize;
        }
        
        public Integer getMaxFiles() {
            return maxFiles;
        }
        
        public void setMaxFiles(Integer maxFiles) {
            this.maxFiles = maxFiles;
        }
        
        public Boolean getIncludeTests() {
            return includeTests;
        }
        
        public void setIncludeTests(Boolean includeTests) {
            this.includeTests = includeTests;
        }
        
        public Boolean getIncludeDocs() {
            return includeDocs;
        }
        
        public void setIncludeDocs(Boolean includeDocs) {
            this.includeDocs = includeDocs;
        }
        
        public List<String> getLanguageFilter() {
            return languageFilter;
        }
        
        public void setLanguageFilter(List<String> languageFilter) {
            this.languageFilter = languageFilter;
        }
        
        public Float getConfidenceThreshold() {
            return confidenceThreshold;
        }
        
        public void setConfidenceThreshold(Float confidenceThreshold) {
            this.confidenceThreshold = confidenceThreshold;
        }
        
        public Boolean getEnableNovaAnalysis() {
            return enableNovaAnalysis;
        }
        
        public void setEnableNovaAnalysis(Boolean enableNovaAnalysis) {
            this.enableNovaAnalysis = enableNovaAnalysis;
        }
    }
    
    @Override
    public String toString() {
        return String.format("ScreeningRequest{sessionId='%s', analysisId='%s', files=%d, stage='%s'}", 
                           sessionId, analysisId, getFileCount(), stage);
    }
}