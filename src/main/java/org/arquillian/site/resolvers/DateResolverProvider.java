package org.arquillian.site.resolvers;

import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.qute.NamespaceResolver;

@ApplicationScoped
public class DateResolverProvider {
    public NamespaceResolver myNamespaceResolver() {
        return new DateResolver();
    }
}
