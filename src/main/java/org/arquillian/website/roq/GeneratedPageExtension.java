package org.arquillian.website.roq;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.Map;
import org.arquillian.website.roq.GeneratedModels.IdentityPage;
import org.arquillian.website.roq.GeneratedModels.ModulePage;
import org.arquillian.website.roq.GeneratedModels.NewsItem;
import org.arquillian.website.roq.GeneratedModels.ReleasePage;
import org.arquillian.website.roq.GeneratedModels.SiteData;

/** Registers typed generated page routes with Roq's HTTP generator. */
@ApplicationScoped
public final class GeneratedPageExtension {

    private final GeneratedDataExtension data;
    private final Template page;
    private final Template module;
    private final Template release;
    private final Template identity;
    private final Template blog;

    /** Creates the route extension with the normalized data loader and shared Qute template. */
    @Inject
    public GeneratedPageExtension(GeneratedDataExtension data, @io.quarkus.qute.Location("generated/page.html") Template page,
            @io.quarkus.qute.Location("roq-templates/layouts/module.html") Template module,
            @io.quarkus.qute.Location("roq-templates/layouts/release.html") Template release,
            @io.quarkus.qute.Location("roq-templates/layouts/identity.html") Template identity,
            @io.quarkus.qute.Location("roq-templates/layouts/generated-blog.html") Template blog) {
        this.data = data;
        this.page = page;
        this.module = module;
        this.release = release;
        this.identity = identity;
        this.blog = blog;
    }

    /** Registers generated route families when Roq creates its Vert.x router. */
    void register(@Observes Router router) {
        try {
            SiteData generated = data.load();
            registerIndex(router, "/modules/", "Modules", generated.modules());
            registerIndex(router, "/community/contributors", "Contributors", generated.identities());
            generated.modules().forEach(module -> registerModule(router, module));
            generated.news().forEach(news -> registerNews(router, news));
            generated.releases().forEach(release -> registerRelease(router, release));
            generated.identities().forEach(identity -> registerIdentity(router, identity));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to load generated Roq page data", exception);
        }
    }

    /** Registers one module URL while retaining its production path. */
    private void registerModule(Router router, ModulePage module) {
        registerHtmlRoute(router, module.url(), context -> render(context, module.title(), module.url(), "module", module));
    }

    /** Registers one release URL while retaining its production path. */
    private void registerRelease(Router router, ReleasePage release) {
        registerHtmlRoute(router, release.url(), context -> render(context, release.title(), release.url(), "release", release));
    }

    /** Registers one generated blog URL with the full news-entry layout. */
    private void registerNews(Router router, NewsItem news) {
        registerHtmlRoute(router, news.url(), context -> render(context, news.title(), news.url(), "blog", news));
    }

    /** Registers one identity URL while retaining its production path. */
    private void registerIdentity(Router router, IdentityPage identity) {
        registerHtmlRoute(router, identity.url(), context -> render(context, identity.name(), identity.url(), "identity", identity));
    }

    /** Registers a generated index page for a collection of typed records. */
    private void registerIndex(Router router, String path, String title, Object records) {
        registerHtmlRoute(router, path, context -> render(context, title, path, "index", records));
    }

    /** Registers both canonical and slashless forms used by browsers and Roq batch generation. */
    private void registerHtmlRoute(Router router, String path, Handler<RoutingContext> handler) {
        router.route(path).order(-100).method(HttpMethod.GET).handler(handler);
        if (path.endsWith("/")) {
            router.route(path.substring(0, path.length() - 1)).order(-100).method(HttpMethod.GET).handler(handler);
        } else {
            router.route(path + "/").order(-100).method(HttpMethod.GET).handler(handler);
        }
    }

    /** Renders one typed page model through the shared Qute template. */
    private void render(RoutingContext context, String title, String url, String kind, Object model) {
        Template template = switch (kind) {
            case "module" -> module;
            case "release" -> release;
            case "identity" -> identity;
            case "blog" -> blog;
            default -> page;
        };
        PageModel pageModel = new PageModel(title, url, kind, model);
        TemplateInstance rendered = template.data("generated", pageModel).data("page", pageModel);
        context.response().putHeader("content-type", "text/html; charset=UTF-8").end(rendered.render());
    }

    /** Supplies stable metadata and a typed model to the shared Qute template. */
    public record PageModel(String title, String url, String kind, Object model) {
        /** Supplies body metadata needed by the shared base layout for generated blog pages. */
        public Map<String, String> data() {
            return "blog".equals(kind) ? Map.of("body_class", "blog") : Map.of();
        }
    }
}
