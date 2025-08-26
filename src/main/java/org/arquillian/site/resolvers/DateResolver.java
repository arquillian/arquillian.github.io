package org.arquillian.site.resolvers;

import io.quarkus.qute.CompletedStage;
import io.quarkus.qute.EvalContext;
import io.quarkus.qute.NamespaceResolver;
import jakarta.inject.Singleton;
import java.time.LocalDate;
import java.util.concurrent.CompletionStage;

@Singleton
public class DateResolver implements NamespaceResolver {

    @Override
    public String getNamespace() {
        return "date";
    }

    @Override
    public CompletionStage<Object> resolve(EvalContext context) {
        String name = context.getName();
        System.out.printf("resolving date: %s(%s)%n", name, context.getParams());
        CompletedStage<Object> result = CompletedStage.ofNull();
        if(name.equals("now.year")) {
            LocalDate now = LocalDate.now();
            result = CompletedStage.of(now.getYear());
        }
        else if(name.equals("now")) {
            result = CompletedStage.of(LocalDate.now());
        }
        return result;
    }
}