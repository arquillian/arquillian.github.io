package adoc;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.arquillian.site.TextileToAsciidocConverter;

/**
 * A simple test class for the TextileToAsciidocConverter

 */
public class TextileToAsciidocTest {

    public static void main(String[] args) {
        // Test with sample textile content
        testConversion();
        
        // Test with a real file if available
        testFileConversion();
    }
    
    private static void testConversion() {
        System.out.println("Testing conversion with sample content...");
        
        String textile = """
h3. Sample Header

p(info). %This is an info block with some content.%

This is a paragraph with _italic_ and *bold* text.

"Link text":http://example.com

bc(prettify)..
public class Example {
    public static void main(String[] args) {
        System.out.println("Hello World");
    }
}

p. An example of a code block.

bc(prettify).. .addClass(Greeter.class)

p. that is a sinle line codeblock

p. Now, about that flair. An Arquillian test case must have three things:

# A @@RunWith(Arquillian.class)@ annotation on the class
# A public static method annotated with @@Deployment@ that returns a ShrinkWrap archive
# At least one method annotated with @@Test@

The @@RunWith@ annotation tells JUnit to use Arquillian as the test controller. Arquillian then looks for a public static method annotated with the @@Deployment@ annotation to retrieve the test archive (i.e., micro-deployment). Then some magic happens and each @@Test@ method is run inside the container environment.

h4(#generate_project_from_archetype). Heading with an ID

First line in h4 header block. This is a reference to the ID: #generate_project_from_archetype.

p. looks for a public static method annotated with the @@Deployment@ annotation in the class.

bc(prettify).. <!-- clip -->
<dependencies>
...
</dependencies>
<!-- clip -->

p(info). %The Java EE API dependency has been moved to the profile since some containers, like Embedded GlassFish, already provide these libraries. Having both on the classpath at the same time results in conflicts. Therefore, we have to play this classpath dance.%

p. This is another paragraph.

p. A paragraph with an id reference: #generate_project_from_archetype.

p. A paragraph with an id reference with alt title "Archetype": #generate_project_from_archetype.

* List item 1
* List item 2

A reference to an image: !/images/example.png!

p. A paragraph with an exclamations that are not an image reference! This is just another exciting sentence!

p. See "Getting Started: Rinse and Repeat":/guides/getting_started_rinse_and_repeat guide
""";

        
        TextileToAsciidocConverter converter = new TextileToAsciidocConverter();
        String asciidoc = converter.convert(textile);
        
        System.out.println("Original Textile:");
        System.out.println("----------------");
        System.out.println(textile);
        System.out.println("\nConverted AsciiDoc:");
        System.out.println("------------------");
        System.out.println(asciidoc);
    }
    
    private static void testFileConversion() {
        System.out.println("\nTesting conversion with real file...");
        
        // Check if the Arquillian textile file exists
        String inputPath = "content/guides/getting_started_rinse_and_repeat.textile";
        String outputPath = "/tmp/getting_started_rinse_and_repeat.adoc";
        
        File inputFile = new File(inputPath);
        if (inputFile.exists()) {
            try {
                // Read the textile file
                String pwd = System.getenv("PWD");
                inputPath = pwd + "/" + inputPath;
                String textileContent = new String(Files.readAllBytes(Paths.get(inputPath)));
                
                // Convert to AsciiDoc
                TextileToAsciidocConverter converter = new TextileToAsciidocConverter();
                String asciidocContent = converter.convert(textileContent);
                
                // Write the AsciiDoc file
                Files.write(Paths.get(outputPath), asciidocContent.getBytes());
                
                System.out.println("Conversion completed successfully!");
                System.out.println("Textile file: " + inputPath);
                System.out.println("AsciiDoc file: " + outputPath);
                
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("Textile file not found: " + inputPath);
        }
    }
}

