package org.arquillian.site;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A converter that transforms Textile markup to AsciiDoc.
 * The converter handles:
    - Headers (h1-h6)
    - Text formatting (bold, italic)
    - Links (both inline and reference links)
    - Code blocks with language specification
    - Lists (ordered and unordered)
    - Tables
    - Special blocks (info, warning)
    - Images
    - Front matter (YAML)
    - File tree structures

    The conversion follows these mapping rules:
    - Textile headers (`h1.` to `h6.`) → AsciiDoc headers (`=` to `======`)
    - Textile bold (`*text*`) → AsciiDoc bold (`*text*`)
    - Textile italic (`_text_`) → AsciiDoc italic (`_text_`)
    - Textile links (`"text":url`) → AsciiDoc links (`link:url[text]`)
    - Textile code blocks (`bc..`) → AsciiDoc source blocks (`[source,language]` with `----` delimiters)
    - Textile lists (`* item` and `# item`) → AsciiDoc lists (`* item` and `. item`)
    - Textile tables (pipe syntax) → AsciiDoc tables (pipe syntax with `|===` delimiters)
    - Textile info blocks (`p(info).`) → AsciiDoc NOTE blocks (`[NOTE]` with `====` delimiters)
    - Textile warning blocks (`p(warning).`) → AsciiDoc WARNING blocks (`[WARNING]` with `====` delimiters)
    - Textile images (`!image.png!`) → AsciiDoc images (`image::image.png[]`)
 */
public class TextileToAsciidocConverter {

    private Map<String, String> referenceLinks = new HashMap<>();
    
    /**
     * Converts a textile document to AsciiDoc
     * 
     * @param textile The textile content to convert
     * @return The converted AsciiDoc content
     */
    public String convert(String textile) {
        // Extract reference links first
        extractReferenceLinks(textile);
        
        // Process the textile content line by line
        String[] lines = textile.split("\n");
        StringBuilder asciidoc = new StringBuilder();
        
        boolean inCodeBlock = false;
        boolean inFrontMatter = false;
        boolean inFiletree = false;
        StringBuilder codeBlock = new StringBuilder();
        String codeBlockLanguage = "";
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimedLine = line.trim();
            
            // Handle front matter (YAML) - Not in standard textile but common in static site generators
            if (i == 0 && trimedLine.equals("---")) {
                inFrontMatter = true;
                asciidoc.append(line).append("\n");
                continue;
            }
            
            if (inFrontMatter) {
                asciidoc.append(line).append("\n");
                if (trimedLine.equals("---")) {
                    inFrontMatter = false;
                }
                continue;
            }
            if (inFiletree) {
                if (line.isBlank()) {
                    asciidoc.append("----\n");
                    inFiletree = false;
                } else {
                    asciidoc.append(line).append("\n");
                }
                continue;
            }

            // Handle code blocks - See https://learnxinyminutes.com/textile under "Code blocks"
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
                if (trimedLine.equals("p.") || trimedLine.equals("p. ") || 
                    (trimedLine.isEmpty() && i < lines.length - 1 && 
                    (lines[i+1].startsWith("h.") || lines[i+1].startsWith("p.")))) {
                    asciidoc.append("[source").append(codeBlockLanguage.isEmpty() ? "" : "," + codeBlockLanguage).append("]\n");
                    asciidoc.append("----\n");
                    asciidoc.append(codeBlock.toString());
                    asciidoc.append("----\n\n");
                    inCodeBlock = false;
                    continue;
                } else {
                    codeBlock.append(line).append("\n");
                    continue;
                }
            }
            
            // Handle file tree - Custom extension, not in standard textile
            if (line.startsWith("(filetree)")) {
                line = line.replace("(filetree)", "");
                asciidoc.append(".File Structure\n");
                asciidoc.append("----\n");
                inFiletree = true;
                continue;
            }
            
            // Handle reference links - See https://learnxinyminutes.com/textile under "Links"
            if (line.matches("\\[\\w+\\]http.*")) {
                String linkName = line.substring(1, line.indexOf(']'));
                String linkUrl = line.substring(line.indexOf(']') + 1);
                asciidoc.append(":" + linkName + ": " + linkUrl + "\n");
                continue;
            }
            
            // Handle headers - See https://learnxinyminutes.com/textile under "Headers"
            if (line.matches("h[1-6](\\([^)]*\\))?\\..*")) {
                int level = Character.getNumericValue(line.charAt(1));
                String headerText = line.substring(line.indexOf(' ') + 1);
                
                // Check for header ID
                String id = "";
                if (line.contains("(#")) {
                    int idStart = line.indexOf("(#") + 1;
                    int idEnd = line.indexOf(")", idStart);
                    id = line.substring(idStart, idEnd);
                    System.out.println("//Header ID: " + id);
                }
                
                StringBuilder header = new StringBuilder();
                for (int j = 0; j < level; j++) {
                    header.append("=");
                }
                
                if (!id.isEmpty()) {
                    asciidoc.append("[[").append(id.substring(1)).append("]]\n");
                }
                
                asciidoc.append(header).append(" ").append(headerText.trim()).append("\n\n");
                continue;
            }
            
            // Handle blockquotes and info/warning blocks - See https://learnxinyminutes.com/textile under "Block quotes" and "CSS classes"
            if (line.startsWith("p(info).") || line.startsWith("p(warning).")) {
                String type = line.contains("info") ? "NOTE" : "WARNING";
                String content = "";
                if (line.contains("%")) {
                    content = line.substring(line.indexOf('%') + 1, line.lastIndexOf('%'));
                } else {
                    content = line.substring(line.indexOf('.') + 1).trim();
                }
                asciidoc.append("[").append(type).append("]\n");
                asciidoc.append("====\n");
                asciidoc.append(content).append("\n");
                asciidoc.append("====\n\n");
                continue;
            }
            
            // Handle images - See https://learnxinyminutes.com/textile under "Images"
            if (line.matches("!(\\S+)!")) {
                line = line.replaceAll("!(\\S+)!", "image::$1[]");
            }
            
            // Handle links - See https://learnxinyminutes.com/textile under "Links"
            line = convertLinks(line);
            
            // Handle emphasis - See https://learnxinyminutes.com/textile under "Phrase modifiers"
            line = line.replaceAll("\\*\\(greenbar\\)(.*?)\\*", "*$1*"); // Bold with greenbar -> bold in asciidoc
            line = line.replaceAll("\\*(.*?)\\*", "*$1*"); // Bold -> bold in asciidoc
            line = line.replaceAll("_(.*?)_", "_$1_"); // Italic -> italic in asciidoc
            // Handle inline codeblocks - See https://learnxinyminutes.com/textile under "Code blocks"
            line = convertInlineCode(line);
            
            // Handle filename divs - See https://learnxinyminutes.com/textile under "Divs and spans"
            if (line.startsWith("div(filename).")) {
                String filename = line.substring(line.indexOf('.') + 1).trim();
                asciidoc.append("." + filename + "\n");
                continue;
            }
            
            // Handle tables - See https://learnxinyminutes.com/textile under "Tables"
            if (line.contains("|")) {
                String tableRow = convertTableRow(line);
                asciidoc.append(tableRow).append("\n");
                
                // If this is the first row of a table, add the separator row
                if (i + 1 < lines.length && lines[i + 1].contains("|")) {
                    asciidoc.append("|===\n");
                } else {
                    asciidoc.append("|===\n\n");
                }
                continue;
            }
            
            // Handle lists - See https://learnxinyminutes.com/textile under "Lists"
            if (line.startsWith("# ")) {
                line = ". " + line.substring(2);
            } else if (line.startsWith("* ")) {
                line = "* " + line.substring(2);
            } else if (line.contains("#")) {
                // Handle ID ref
                // Match reference IDs: captures both the prefix and the ID
                // Group 1: ([^#]*) - captures any characters before the #
                // # - the # character
                // Group 2: (\\w+(?:_\\w+)*) - captures the ID (word chars with optional underscores)
                Matcher idMatcher = Pattern.compile("([^#]*)#(\\w+(?:_\\w+)*)").matcher(line);
                if(idMatcher.find()) {
                    String prefix = idMatcher.group(1);
                    String id = idMatcher.group(2);
                    line = idMatcher.replaceFirst(prefix+"<<"+id+">>");
                }
            }
            // Handle paragraphs - See https://learnxinyminutes.com/textile under "Paragraphs"
            // This is not handling adjusted paragraphs (p<., p>., p=., p<>.) or indents (p(. ...))
            if (line.trim().startsWith("p.")) {
                asciidoc.append(line.trim().substring(2).trim())
                    .append("\n");
                continue;
            }

            // Add the processed line
            if (!line.isBlank()) {
                asciidoc.append(line).append("\n\n");
            } else {
                asciidoc.append("\n");
            }
        }
        
        // Handle any remaining code block
        if (inCodeBlock) {
            asciidoc.append("[source").append(codeBlockLanguage.isEmpty() ? "" : "," + codeBlockLanguage).append("]\n");
            asciidoc.append("----\n");
            asciidoc.append(codeBlock.toString());
            asciidoc.append("----\n\n");
        }
        
        return asciidoc.toString();
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
     * See https://learnxinyminutes.com/textile under "Code blocks"
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
            return "text";
        } else if (line.startsWith("bc(prettify)..")) {
            return "";
        } else {
            return "";
        }
    }
    
    /**
     * Converts textile links to AsciiDoc links
     * See https://learnxinyminutes.com/textile under "Links"
     *
     * @param line The line containing links
     * @return The line with converted links
     */
    private String convertLinks(String line) {
        // Convert inline links: "text":url
        Pattern inlinePattern = Pattern.compile("\"([^\"]+)\":([\\S]+)");
        Matcher inlineMatcher = inlinePattern.matcher(line);
        StringBuffer sb = new StringBuffer();
        
        while (inlineMatcher.find()) {
            String text = inlineMatcher.group(1);
            String url = inlineMatcher.group(2);
            
            if (referenceLinks.containsKey(url)) {
                inlineMatcher.appendReplacement(sb, referenceLinks.get(url) + "[" + text + "]");
            } else {
                inlineMatcher.appendReplacement(sb, url + "[" + text + "]");
            }
        }
        inlineMatcher.appendTail(sb);
        
        return sb.toString();
    }
    
    /**
     * Converts a textile table row to AsciiDoc format
     * See https://learnxinyminutes.com/textile under "Tables"
     *
     * @param line The textile table row
     * @return The AsciiDoc table row
     */
    private String convertTableRow(String line) {
        // Remove any textile table formatting and convert to AsciiDoc table format
        line = line.replaceAll("\\|([^\\|]*)\\|", "|$1|");
        
        // Ensure the table row starts and ends with |
        if (!line.startsWith("|")) {
            line = "|" + line;
        }
        
        return line;
    }

    private String convertInlineCode(String line) {
        line = line.replaceAll("@?@(.*)@", "`$1`"); // @ -> ` in asciidoc
        return line;
    }

    /**
     * Main method to convert a textile file to AsciiDoc
     * 
     * @param args Command line arguments: [inputFile] [outputFile]
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java TextileToAsciidocConverter <inputFile> <outputFile>");
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
            
            // Convert to AsciiDoc
            TextileToAsciidocConverter converter = new TextileToAsciidocConverter();
            String asciidocContent = converter.convert(textileContent.toString());
            
            // Write the AsciiDoc file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
                writer.write(asciidocContent);
            }
            
            System.out.println("Conversion completed successfully!");
            System.out.println("Textile file: " + inputFile);
            System.out.println("AsciiDoc file: " + outputFile);
            
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

