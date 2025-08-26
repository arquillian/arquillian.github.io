package org.arquillian.site;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A converter that transforms Textile markup to Markdown.
 */
public class TextileToMarkdownConverter {

    private Map<String, String> referenceLinks = new HashMap<>();
    
    /**
     * Converts a textile document to markdown
     * 
     * @param textile The textile content to convert
     * @return The converted markdown content
     */
    public String convert(String textile) {
        // Extract reference links first
        extractReferenceLinks(textile);
        
        // Process the textile content line by line
        String[] lines = textile.split("\n");
        StringBuilder markdown = new StringBuilder();
        
        boolean inCodeBlock = false;
        boolean inFrontMatter = false;
        StringBuilder codeBlock = new StringBuilder();
        String codeBlockLanguage = "";
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            // Handle front matter (YAML)
            if (i == 0 && line.trim().equals("---")) {
                inFrontMatter = true;
                markdown.append(line).append("\n");
                continue;
            }
            
            if (inFrontMatter) {
                markdown.append(line).append("\n");
                if (line.trim().equals("---")) {
                    inFrontMatter = false;
                }
                continue;
            }
            
            // Handle code blocks
            if (line.startsWith("bc(") || line.startsWith("bc.") || line.startsWith("bc..")) {
                if (!inCodeBlock) {
                    inCodeBlock = true;
                    codeBlockLanguage = extractCodeBlockLanguage(line);
                    codeBlock = new StringBuilder();
                    continue;
                }
            }
            
            if (inCodeBlock) {
                // End of code block
                if (line.trim().equals("p.") || line.trim().equals("p. ") || 
                    (line.trim().isEmpty() && i < lines.length - 1 && 
                    (lines[i+1].startsWith("h") || lines[i+1].startsWith("p")))) {
                    markdown.append("```").append(codeBlockLanguage).append("\n");
                    markdown.append(codeBlock.toString());
                    markdown.append("```\n\n");
                    inCodeBlock = false;
                    continue;
                } else {
                    codeBlock.append(line).append("\n");
                    continue;
                }
            }
            
            // Handle file tree
            if (line.startsWith("(filetree)")) {
                line = line.replace("(filetree)", "```");
                markdown.append(line).append("\n");
                continue;
            }
            
            // Handle reference links
            if (line.matches("\\[\\w+\\]http.*")) {
                String linkName = line.substring(1, line.indexOf(']'));
                String linkUrl = line.substring(line.indexOf(']') + 1);
                markdown.append("[").append(linkName).append("]: ").append(linkUrl).append("\n");
                continue;
            }
            
            // Handle headers
            if (line.matches("h[1-6]\\. .*")) {
                int level = Character.getNumericValue(line.charAt(1));
                String headerText = line.substring(line.indexOf(' ') + 1);
                
                // Check for header ID
                String id = "";
                if (headerText.contains("(#")) {
                    int idStart = headerText.indexOf("(#") + 1;
                    int idEnd = headerText.indexOf(")", idStart);
                    id = headerText.substring(idStart, idEnd);
                    headerText = headerText.substring(0, idStart - 1) + headerText.substring(idEnd + 1);
                }
                
                StringBuilder header = new StringBuilder();
                for (int j = 0; j < level; j++) {
                    header.append("#");
                }
                
                markdown.append(header).append(" ").append(headerText.trim());
                
                if (!id.isEmpty()) {
                    markdown.append(" {").append(id).append("}");
                }
                
                markdown.append("\n\n");
                continue;
            }
            
            // Handle blockquotes and info/warning blocks
            if (line.startsWith("p(info).") || line.startsWith("p(warning).")) {
                String type = line.contains("info") ? "INFO" : "WARNING";
                String content = line.substring(line.indexOf('%') + 1, line.lastIndexOf('%'));
                markdown.append("> [").append(type).append("] ").append(content).append("\n\n");
                continue;
            }
            
            // Handle images
            if (line.contains("!") && line.contains("!")) {
                line = line.replaceAll("!([^!]+)!", "![]($1)");
                markdown.append(line).append("\n\n");
                continue;
            }
            
            // Handle links
            line = convertLinks(line);
            
            // Handle emphasis
            line = line.replaceAll("\\*\\(greenbar\\)(.*?)\\*", "**$1**"); // Bold with greenbar
            line = line.replaceAll("\\*(.*?)\\*", "**$1**"); // Bold
            line = line.replaceAll("_(.*?)_", "*$1*"); // Italic
            
            // Handle filename divs
            if (line.startsWith("div(filename).")) {
                String filename = line.substring(line.indexOf('.') + 1).trim();
                markdown.append(filename).append("\n\n");
                continue;
            }
            
            // Handle tables
            if (line.contains("|")) {
                String tableRow = convertTableRow(line);
                markdown.append(tableRow).append("\n");
                
                // If this is the first row of a table, add the separator row
                if (i + 1 < lines.length && lines[i + 1].contains("|")) {
                    int columns = countTableColumns(line);
                    markdown.append(createTableSeparator(columns)).append("\n");
                }
                continue;
            }
            
            // Handle lists
            if (line.startsWith("# ")) {
                line = "1. " + line.substring(2);
            } else if (line.startsWith("* ")) {
                // Already in markdown format
            }
            
            // Add the processed line
            if (!line.trim().isEmpty()) {
                markdown.append(line).append("\n\n");
            } else {
                markdown.append("\n");
            }
        }
        
        // Handle any remaining code block
        if (inCodeBlock) {
            markdown.append("```").append(codeBlockLanguage).append("\n");
            markdown.append(codeBlock.toString());
            markdown.append("```\n\n");
        }
        
        return markdown.toString();
    }
    
    /**
     * Extracts reference links from the textile content
     * 
     * @param textile The textile content
     */
    private void extractReferenceLinks(String textile) {
        Pattern pattern = Pattern.compile("\\[(\\w+)\\](http[^\\s]+)");
        Matcher matcher = pattern.matcher(textile);
        
        while (matcher.find()) {
            String name = matcher.group(1);
            String url = matcher.group(2);
            referenceLinks.put(name, url);
        }
    }
    
    /**
     * Extracts the language for a code block
     * 
     * @param line The line containing the code block declaration
     * @return The language for the code block
     */
    private String extractCodeBlockLanguage(String line) {
        if (line.startsWith("bc(prettify)")) {
            return "";
        } else if (line.startsWith("bc(command)")) {
            return "bash";
        } else if (line.startsWith("bc(output)")) {
            return "";
        } else {
            return "";
        }
    }
    
    /**
     * Converts textile links to markdown links
     * 
     * @param line The line containing links
     * @return The line with converted links
     */
    private String convertLinks(String line) {
        // Convert inline links: "text":url
        Pattern inlinePattern = Pattern.compile("\"([^\"]+)\":([\\w]+)");
        Matcher inlineMatcher = inlinePattern.matcher(line);
        StringBuffer sb = new StringBuffer();
        
        while (inlineMatcher.find()) {
            String text = inlineMatcher.group(1);
            String linkRef = inlineMatcher.group(2);
            
            if (referenceLinks.containsKey(linkRef)) {
                inlineMatcher.appendReplacement(sb, "[" + text + "][" + linkRef + "]");
            } else {
                inlineMatcher.appendReplacement(sb, "[" + text + "](" + linkRef + ")");
            }
        }
        inlineMatcher.appendTail(sb);
        
        return sb.toString();
    }
    
    /**
     * Converts a textile table row to markdown format
     * 
     * @param line The textile table row
     * @return The markdown table row
     */
    private String convertTableRow(String line) {
        // Remove any textile table formatting
        line = line.replaceAll("\\|([^\\|]*)\\|", "| $1 |");
        
        // Ensure the table row starts and ends with |
        if (!line.startsWith("|")) {
            line = "| " + line;
        }
        if (!line.endsWith("|")) {
            line = line + " |";
        }
        
        return line;
    }
    
    /**
     * Counts the number of columns in a table row
     * 
     * @param line The table row
     * @return The number of columns
     */
    private int countTableColumns(String line) {
        return line.split("\\|").length - 1;
    }
    
    /**
     * Creates a markdown table separator row
     * 
     * @param columns The number of columns
     * @return The separator row
     */
    private String createTableSeparator(int columns) {
        StringJoiner joiner = new StringJoiner("|", "|", "|");
        for (int i = 0; i < columns; i++) {
            joiner.add("---");
        }
        return joiner.toString();
    }
    
    /**
     * Main method to convert a textile file to markdown
     * 
     * @param args Command line arguments: [inputFile] [outputFile]
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java TextileToMarkdownConverter <inputFile> <outputFile>");
            return;
        }
        
        String inputFile = args[0];
        String outputFile = args[1];
        
        try {
            // Read the textile file
            StringBuilder textileContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    textileContent.append(line).append("\n");
                }
            }
            
            // Convert to markdown
            TextileToMarkdownConverter converter = new TextileToMarkdownConverter();
            String markdownContent = converter.convert(textileContent.toString());
            
            // Write the markdown file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
                writer.write(markdownContent);
            }
            
            System.out.println("Conversion completed successfully!");
            System.out.println("Textile file: " + inputFile);
            System.out.println("Markdown file: " + outputFile);
            
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

// Made with Bob
