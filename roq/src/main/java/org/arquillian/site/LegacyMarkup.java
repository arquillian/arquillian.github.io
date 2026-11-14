package org.arquillian.site;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

/** Adapts AsciiDoc's structural wrappers to the existing Textile stylesheet and browser hooks. */
@Named("legacyMarkup")
@ApplicationScoped
public class LegacyMarkup {

    /** Returns body HTML without the document title already supplied by the enclosing site layout. */
    public String render(String html) {
        return normalize(html).body().html();
    }

    /** Separates introductory prose from chapters so the original guide header styles still apply. */
    public GuideBody guide(String html) {
        Document document = normalize(html);
        Element introduction = new Element("div");
        for (Node node : new ArrayList<>(document.body().childNodes())) {
            if (node instanceof Element element && element.tagName().equals("h3")) break;
            introduction.appendChild(node);
        }
        return new GuideBody(introduction.html(), document.body().html());
    }

    /** Holds the two structural regions expected by the legacy guide layout. */
    public record GuideBody(String introduction, String sections) {
    }

    /** Preserves semantic content while replacing only wrappers used differently by the old stylesheet. */
    private static Document normalize(String html) {
        Document document = Jsoup.parseBodyFragment(html);
        document.outputSettings().prettyPrint(false);
        Element first = document.body().children().first();
        if (first != null && first.tagName().equals("h1")) first.remove();
        for (Element block : document.select("div.paragraph")) {
            if (block.childrenSize() != 1 || !block.child(0).tagName().equals("p")) continue;
            Element paragraph = block.child(0);
            if (block.hasClass("filename")) {
                paragraph.unwrap();
                block.removeClass("paragraph");
            } else {
                transferRoles(block, paragraph, "paragraph");
                block.unwrap();
            }
        }
        for (Element paragraph : document.select("p.info,p.warning,p.important")) {
            if (paragraph.childrenSize() == 1 && paragraph.child(0).tagName().equals("span")
                    && paragraph.ownText().isBlank()) continue;
            List<Node> contents = paragraph.childNodesCopy();
            paragraph.empty().appendElement("span").insertChildren(0, contents);
        }
        for (Element block : document.select("div.listingblock,div.literalblock")) {
            Element pre = block.selectFirst("div.content > pre");
            if (pre == null) continue;
            transferRoles(block, pre, "listingblock", "literalblock");
            Element code = pre.selectFirst("code");
            if (code == null) {
                List<Node> contents = pre.childNodesCopy();
                pre.empty();
                code = pre.appendElement("code");
                code.insertChildren(0, contents);
            }
            transferRoles(block, code, "listingblock", "literalblock");
            pre.parent().unwrap();
            block.unwrap();
        }
        for (Element wrapper : document.select("div.ulist,div.olist,div.dlist")) {
            if (wrapper.childrenSize() != 1) continue;
            transferRoles(wrapper, wrapper.child(0), "ulist", "olist", "dlist");
            wrapper.unwrap();
        }
        for (Element item : document.select("li")) {
            List<Element> paragraphs = item.children().stream().filter(e -> e.tagName().equals("p")).toList();
            if (paragraphs.size() == 1) paragraphs.getFirst().unwrap();
        }
        for (Element span : document.select("strong > span[class],em > span[class]")) {
            Element parent = span.parent();
            if (span.attributesSize() != 1 || parent.childrenSize() != 1 || !parent.ownText().isBlank()) continue;
            transferRoles(span, parent);
            span.unwrap();
        }
        for (Element paragraph : document.select("td > p.tableblock,th > p.tableblock")) {
            if (paragraph.parent().childrenSize() == 1) paragraph.unwrap();
        }
        for (Element paragraph : document.select("dd > p")) {
            if (paragraph.attributesSize() == 0 && paragraph.parent().childrenSize() == 1) paragraph.unwrap();
        }
        for (Element block : document.select("div.quoteblock")) {
            if (block.childrenSize() != 1 || !block.child(0).tagName().equals("blockquote")) continue;
            Element quote = block.child(0);
            if (block.hasAttr("id")) {
                if (quote.hasAttr("id")) continue;
                quote.attr("id", block.id());
            }
            transferRoles(block, quote, "quoteblock");
            block.unwrap();
        }
        for (Element table : document.select("table.tableblock.data,table.tableblock.legacy-plain")) {
            table.select("colgroup").remove();
            for (Element section : new ArrayList<>(table.children())) {
                if (section.tagName().equals("thead") || section.tagName().equals("tbody")) section.unwrap();
            }
            for (String structural : List.of("tableblock", "frame-all", "grid-all", "stretch", "legacy-plain")) table.removeClass(structural);
        }
        return document;
    }

    /** Moves only meaningful presentation roles while discarding AsciiDoc's structural class names. */
    private static void transferRoles(Element source, Element target, String... structural) {
        List<String> omitted = List.of(structural);
        for (String role : new ArrayList<>(source.classNames())) {
            if (!omitted.contains(role)) target.addClass(role);
        }
    }
}
