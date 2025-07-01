package com.somdiproy.lambda.screening.util;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Advanced token optimization for Nova models
 * Minimizes token usage while preserving code analysis quality
 */
public class TokenOptimizer {
    
    // Programming language patterns for intelligent filtering
    private static final Map<String, LanguageConfig> LANGUAGE_CONFIGS = new HashMap<>();
    
    static {
        LANGUAGE_CONFIGS.put("java", new LanguageConfig(
            Arrays.asList("public", "private", "protected", "class", "interface", "extends", "implements"),
            Arrays.asList("//.*", "/\\*.*?\\*/", "import\\s+[^;]+;"),
            Arrays.asList("\\{[^}]*\\}", "\"[^\"]*\"", "'[^']*'")
        ));
        
        LANGUAGE_CONFIGS.put("python", new LanguageConfig(
            Arrays.asList("def", "class", "import", "from", "if", "elif", "else", "for", "while"),
            Arrays.asList("#.*", "\"\"\".*?\"\"\"", "'''.*?'''"),
            Arrays.asList("\"[^\"]*\"", "'[^']*'", "f\"[^\"]*\"")
        ));
        
        LANGUAGE_CONFIGS.put("javascript", new LanguageConfig(
            Arrays.asList("function", "class", "const", "let", "var", "if", "else", "for", "while"),
            Arrays.asList("//.*", "/\\*.*?\\*/"),
            Arrays.asList("\"[^\"]*\"", "'[^']*'", "`[^`]*`")
        ));
    }
    
    /**
     * Optimize content for Nova Micro screening (aggressive optimization)
     */
    public static String optimizeForScreening(String content, String language) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        LanguageConfig config = LANGUAGE_CONFIGS.getOrDefault(language.toLowerCase(), getDefaultConfig());
        
        // Stage 1: Remove comments and documentation
        String optimized = removeComments(content, config);
        
        // Stage 2: Extract method signatures and class declarations only
        optimized = extractSignatures(optimized, language);
        
        // Stage 3: Collapse whitespace
        optimized = optimized.replaceAll("\\s+", " ").trim();
        
        // Stage 4: Limit to token budget (1000 chars ≈ 250 tokens)
        return limitLength(optimized, 1000);
    }
    
    /**
     * Optimize content for Nova Lite detection (balanced optimization)
     */
    public static String optimizeForDetection(String content, String language) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        LanguageConfig config = LANGUAGE_CONFIGS.getOrDefault(language.toLowerCase(), getDefaultConfig());
        
        // Stage 1: Remove comments but keep structure
        String optimized = removeComments(content, config);
        
        // Stage 2: Keep executable code blocks
        optimized = preserveExecutableCode(optimized, language);
        
        // Stage 3: Normalize whitespace
        optimized = normalizeWhitespace(optimized);
        
        // Stage 4: Limit to token budget (5000 chars ≈ 1250 tokens)
        return limitLength(optimized, 5000);
    }
    
    /**
     * Optimize issue context for Nova Premier suggestions (focused optimization)
     */
    public static Map<String, Object> optimizeForSuggestions(String issueCode, String surroundingContext, 
                                                            String language, String issueType) {
        Map<String, Object> optimized = new HashMap<>();
        
        // Stage 1: Extract the problematic code block
        String focusedCode = extractIssueContext(issueCode, 500);
        
        // Stage 2: Provide minimal surrounding context
        String context = extractContext(surroundingContext, 300);
        
        // Stage 3: Add language-specific hints
        List<String> hints = generateLanguageHints(language, issueType);
        
        optimized.put("focusedCode", focusedCode);
        optimized.put("context", context);
        optimized.put("language", language);
        optimized.put("issueType", issueType);
        optimized.put("hints", hints);
        optimized.put("tokenEstimate", estimateTokens(focusedCode + context));
        
        return optimized;
    }
    
    /**
     * Remove comments based on language configuration
     */
    private static String removeComments(String content, LanguageConfig config) {
        String result = content;
        for (String commentPattern : config.commentPatterns) {
            result = result.replaceAll(commentPattern, "");
        }
        return result;
    }
    
    /**
     * Extract method signatures and class declarations
     */
    private static String extractSignatures(String content, String language) {
        List<String> signatures = new ArrayList<>();
        
        switch (language.toLowerCase()) {
            case "java":
                signatures.addAll(extractJavaSignatures(content));
                break;
            case "python":
                signatures.addAll(extractPythonSignatures(content));
                break;
            case "javascript":
            case "typescript":
                signatures.addAll(extractJavaScriptSignatures(content));
                break;
            default:
                return extractGenericStructure(content);
        }
        
        return String.join("\n", signatures);
    }
    
    /**
     * Extract Java method and class signatures
     */
    private static List<String> extractJavaSignatures(String content) {
        List<String> signatures = new ArrayList<>();
        String[] lines = content.split("\n");
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches(".*\\b(class|interface|enum)\\s+\\w+.*")) {
                signatures.add(trimmed);
            } else if (trimmed.matches(".*(public|private|protected).*\\(.*\\).*")) {
                signatures.add(trimmed);
            } else if (trimmed.matches(".*\\w+\\s*\\(.*\\).*\\{?\\s*$")) {
                signatures.add(trimmed);
            }
        }
        
        return signatures;
    }
    
    /**
     * Extract Python function and class definitions
     */
    private static List<String> extractPythonSignatures(String content) {
        List<String> signatures = new ArrayList<>();
        String[] lines = content.split("\n");
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("class ") || trimmed.startsWith("def ")) {
                signatures.add(trimmed);
            } else if (trimmed.matches("^\\s*(async\\s+)?def\\s+\\w+.*:$")) {
                signatures.add(trimmed);
            }
        }
        
        return signatures;
    }
    
    /**
     * Extract JavaScript function declarations
     */
    private static List<String> extractJavaScriptSignatures(String content) {
        List<String> signatures = new ArrayList<>();
        String[] lines = content.split("\n");
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches(".*(function|class|const|let|var).*\\{?\\s*$")) {
                signatures.add(trimmed);
            } else if (trimmed.matches(".*=>.*")) {
                signatures.add(trimmed);
            }
        }
        
        return signatures;
    }
    
    /**
     * Generic structure extraction for unknown languages
     */
    private static String extractGenericStructure(String content) {
        return content.lines()
            .filter(line -> line.trim().length() > 0)
            .filter(line -> !line.trim().startsWith("#"))
            .filter(line -> !line.trim().startsWith("//"))
            .limit(50) // Limit to first 50 meaningful lines
            .reduce("", (a, b) -> a + "\n" + b);
    }
    
    /**
     * Preserve executable code while removing boilerplate
     */
    private static String preserveExecutableCode(String content, String language) {
        switch (language.toLowerCase()) {
            case "java":
                return preserveJavaExecutableCode(content);
            case "python":
                return preservePythonExecutableCode(content);
            default:
                return content;
        }
    }
    
    /**
     * Preserve Java executable code patterns
     */
    private static String preserveJavaExecutableCode(String content) {
        return content.lines()
            .filter(line -> {
                String trimmed = line.trim();
                return !trimmed.isEmpty() && 
                       !trimmed.startsWith("import") &&
                       !trimmed.startsWith("package") &&
                       !trimmed.matches("^\\s*\\}\\s*$") &&
                       !trimmed.matches("^\\s*\\{\\s*$");
            })
            .reduce("", (a, b) -> a + "\n" + b);
    }
    
    /**
     * Preserve Python executable code patterns
     */
    private static String preservePythonExecutableCode(String content) {
        return content.lines()
            .filter(line -> {
                String trimmed = line.trim();
                return !trimmed.isEmpty() && 
                       !trimmed.startsWith("import") &&
                       !trimmed.startsWith("from") &&
                       trimmed.length() > 5; // Skip very short lines
            })
            .reduce("", (a, b) -> a + "\n" + b);
    }
    
    /**
     * Normalize whitespace while preserving structure
     */
    private static String normalizeWhitespace(String content) {
        return content
            .replaceAll("\\s+", " ")
            .replaceAll("\\s*\\{\\s*", " { ")
            .replaceAll("\\s*\\}\\s*", " } ")
            .replaceAll("\\s*;\\s*", "; ")
            .trim();
    }
    
    /**
     * Extract focused issue context
     */
    private static String extractIssueContext(String code, int maxLength) {
        if (code == null || code.length() <= maxLength) {
            return code;
        }
        
        // Try to find a complete code block around the issue
        int start = Math.max(0, code.length() / 2 - maxLength / 2);
        int end = Math.min(code.length(), start + maxLength);
        
        return code.substring(start, end);
    }
    
    /**
     * Extract minimal surrounding context
     */
    private static String extractContext(String context, int maxLength) {
        if (context == null || context.length() <= maxLength) {
            return context;
        }
        
        return context.substring(0, maxLength) + "...";
    }
    
    /**
     * Generate language-specific hints for Nova Premier
     */
    private static List<String> generateLanguageHints(String language, String issueType) {
        List<String> hints = new ArrayList<>();
        
        switch (language.toLowerCase()) {
            case "java":
                if (issueType.contains("sql")) {
                    hints.add("Use PreparedStatement");
                    hints.add("Consider Spring Data JPA");
                }
                if (issueType.contains("performance")) {
                    hints.add("Check Collections usage");
                    hints.add("Consider Stream API");
                }
                break;
            case "python":
                if (issueType.contains("performance")) {
                    hints.add("Use list comprehensions");
                    hints.add("Consider numpy for large data");
                }
                break;
        }
        
        return hints;
    }
    
    /**
     * Estimate token count (rough approximation: 1 token ≈ 4 characters)
     */
    private static int estimateTokens(String content) {
        return content.length() / 4;
    }
    
    /**
     * Limit content length while preserving meaning
     */
    private static String limitLength(String content, int maxLength) {
        if (content.length() <= maxLength) {
            return content;
        }
        
        // Try to cut at a natural boundary (end of line, after semicolon, etc.)
        for (int i = maxLength; i > maxLength * 0.8; i--) {
            char c = content.charAt(i);
            if (c == '\n' || c == ';' || c == '}') {
                return content.substring(0, i + 1);
            }
        }
        
        return content.substring(0, maxLength);
    }
    
    /**
     * Get default language configuration
     */
    private static LanguageConfig getDefaultConfig() {
        return new LanguageConfig(
            Arrays.asList("function", "class", "def", "public", "private"),
            Arrays.asList("//.*", "#.*", "/\\*.*?\\*/"),
            Arrays.asList("\"[^\"]*\"", "'[^']*'")
        );
    }
    
    /**
     * Configuration for programming language-specific optimizations
     */
    private static class LanguageConfig {
        final List<String> keywords;
        final List<String> commentPatterns;
        final List<String> stringPatterns;
        
        LanguageConfig(List<String> keywords, List<String> commentPatterns, List<String> stringPatterns) {
            this.keywords = keywords;
            this.commentPatterns = commentPatterns;
            this.stringPatterns = stringPatterns;
        }
    }
}