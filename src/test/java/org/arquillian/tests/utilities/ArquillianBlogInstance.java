package org.arquillian.tests.utilities;

public class ArquillianBlogInstance {

    public static String getUrl(){
        return System.getProperty("arquillian.blog.url", "http://arquillian.org");
    }
}
