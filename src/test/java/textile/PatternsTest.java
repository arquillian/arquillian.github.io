package textile;

import static org.junit.jupiter.api.Assertions.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PatternsTest {

    @Test
    public void testHeaderPattern() {
        String hline = "h4(#generate_project_from_archetype). Generate a Project from a Maven Archetype\n";
        String hline2 = "h3. Generate a Project from a Maven Archetype\n";
        
        String pattern = "h[1-6](\\([^)]*\\))?\\..*";
        // Match header pattern: h1-h6 followed by optional attributes in parentheses and a period
        if(hline.trim().matches(pattern)) {
            System.out.println("hline matches");
        } else {
            System.out.println("hline does NOT match");
        }
        if(hline2.trim().matches(pattern)) {
            System.out.println("hline2 matches");
        } else {
            System.out.println("hline2 does NOT match");
        }
    }

    @Test
    public void testRefReplace() {
        String refline = "See #generate_project_from_archetype for ...";
        if (refline.contains("#")) {
            // Handle ID ref
            // Match reference IDs: captures both the prefix and the ID
            // Group 1: ([^#]*) - captures any characters before the #
            // # - the # character
            // Group 2: (\\w+(?:_\\w+)*) - captures the ID (word chars with optional underscores)
            Matcher idMatcher = Pattern.compile("([^#]*)#(\\w+(?:_\\w+)*)").matcher(refline);
            if(idMatcher.find()) {
                String prefix = idMatcher.group(1);
                String id = idMatcher.group(2);
                refline = idMatcher.replaceFirst(prefix+"<<"+id+">>");
                System.out.println("Success: "+refline);
            } else {
                System.out.println("Fail: "+refline);
            }
        }
        String refline2 = "p. The foundation of your project is now ready! Skip to the next section, \"Open the Project in Eclipse\":#open_the_project_in_eclipse, so we can start writing some code!";
        if (refline2.contains("#")) {
            // Handle ID ref
            // Match reference IDs: captures both the prefix and the ID
            // Group 1: ([^#]*) - captures any characters before the #
            // # - the # character
            // Group 2: (\\w+(?:_\\w+)*) - captures the ID (word chars with optional underscores)
            Matcher idMatcher = Pattern.compile("([^#]*)#(\\w+(?:_\\w+)*)").matcher(refline2);
            if(idMatcher.find()) {
                String prefix = idMatcher.group(1);
                String id = idMatcher.group(2);
                refline2 = idMatcher.replaceFirst(prefix+"<<"+id+">>");
                System.out.println("Success: "+refline2);
            } else {
                System.out.println("Fail: "+refline2);
            }
        }
    }

    @Test
    public void testImageRef() {
        String line = "A line with image ref: !image.png!";
        // Match image references: ! followed by non-whitespace chars followed by !
        // ! - literal exclamation mark
        // (\\S+) - one or more non-whitespace characters (captured in group 1)
        // ! - literal exclamation mark
        String regex = "!(\\S+)!";
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(line);
        if(m.find()) {
            line = m.replaceAll("image::$1[]");
            System.out.println("pass: "+line);
            Assertions.assertEquals("A line with image ref: image::image.png[]", line);
        } else {
            fail("No match found");
        }

        String line2 = "A line with excitement! And another!";
        Matcher m2 = p.matcher(line2);
        if(!m2.find()) {
            System.out.println("pass: line2 does not match");
        } else {
            fail("Should not match");
        }
    }
}
