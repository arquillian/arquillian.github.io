package org.arquillian.site.extensions;

import io.quarkus.qute.TemplateExtension;

@TemplateExtension(namespace = "utils")
public class Utils {
    public static String[] asList(String value) {
        return value == null ? new String[0] : value.split(",");
    }
}
