package org.arquillian.tests.pom.fragmentObjects;

import org.arquillian.tests.utilities.FragmentVerifier;
import org.jboss.arquillian.graphene.fragment.Root;
import org.openqa.selenium.WebElement;

public class ContentFragment {

    @Root
    private WebElement contentRoot;

    private String selector;

    public ContentFragment(WebElement contentRoot, String selector) {
        this.contentRoot = contentRoot;
        this.selector = selector;
    }

    public ContentVerifier verify() {
        return new ContentVerifier(contentRoot, selector);
    }

    public class ContentVerifier extends FragmentVerifier {
        public ContentVerifier(WebElement contentRoot, String selector) {
            super(contentRoot, selector);
        }
    }
}
