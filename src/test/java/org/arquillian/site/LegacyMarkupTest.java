package org.arquillian.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/** Protects the legacy CSS and JavaScript hooks when AsciiDoc introduces its standard wrappers. */
class LegacyMarkupTest {

    /** Retains callout, filename and listing roles without changing literal examples or fragments. */
    @Test
    void adaptsAsciiDocWrappers() {
        String source = """
                <div class="paragraph info"><p>Read <strong><span class="greenbar">this</span></strong>.</p></div>
                <h3 class="discrete" id="kept">Chapter</h3>
                <div class="paragraph filename"><p>pom.xml</p></div>
                <div class="listingblock prettify"><div class="content"><pre>&lt;v&gt;{literal}&lt;/v&gt;
                  {not_a_template}</pre></div></div>
                <div class="ulist"><ul><li><p>One</p><ul><li><p>Nested</p></li></ul></li></ul></div>
                """;
        Document result = Jsoup.parseBodyFragment(new LegacyMarkup().render(source));
        assertEquals("Read this.", result.selectFirst("p.info").text());
        assertEquals(1, result.select("p.info > span").size());
        assertEquals("this", result.selectFirst("strong.greenbar").text());
        assertEquals("pom.xml", result.selectFirst("div.filename").text());
        assertEquals("pre", result.selectFirst("div.filename").nextElementSibling().tagName());
        assertEquals("<v>{literal}</v>\n  {not_a_template}", result.selectFirst("pre.prettify > code.prettify").wholeText());
        assertEquals("Chapter", result.getElementById("kept").text());
        assertTrue(result.select("div.paragraph,div.listingblock,div.ulist,li > p").isEmpty());
        assertEquals(2, result.select("ul").size());
    }

    /** Preserves multi-paragraph list spacing, HTML entities, links and already compatible legacy HTML. */
    @Test
    void retainsIntentionalStructure() {
        String source = """
                <ul><li><p>First</p><p>Second</p></li></ul>
                <p id="original"><a href="/guides/a/#kept">A &amp; B</a></p>
                <pre class="command"><code class="command">echo &lt;hello&gt;</code></pre>
                """;
        Document result = Jsoup.parseBodyFragment(new LegacyMarkup().render(source));
        assertEquals(2, result.select("li > p").size());
        assertEquals("/guides/a/#kept", result.selectFirst("#original a").attr("href"));
        assertEquals("A & B", result.selectFirst("#original").text());
        assertEquals("echo <hello>", result.selectFirst("pre.command code.command").wholeText());
        assertFalse(result.html().contains("&amp;amp;"));
    }

    /** Matches authored definition spacing, quote anchors and data-table widths/striping without changing content. */
    @Test
    void adaptsAuthoredStructures() {
        String source = """
                <dl><dt>Term</dt><dd><p>Definition <a href="#kept">link</a></p></dd>
                <dt>Long</dt><dd><p>First</p><p>Second</p></dd>
                <dt>Anchored</dt><dd><p id="definition">Anchored definition</p></dd></dl>
                <div class="quoteblock message" id="kept"><blockquote><p>Quote</p></blockquote></div>
                <table class="tableblock frame-all grid-all stretch data"><colgroup><col style="width:50%"></colgroup>
                <thead><tr><th><p class="tableblock">Name</p></th></tr></thead>
                <tbody><tr><td><p class="tableblock">Contributor</p></td></tr></tbody></table>
                <table class="tableblock"><colgroup><col style="width:30%"></colgroup>
                <thead><tr><th>Other</th></tr></thead></table>
                """;
        Document result = Jsoup.parseBodyFragment(new LegacyMarkup().render(source));
        assertEquals("Definition link", result.selectFirst("dd").text());
        assertTrue(result.selectFirst("dd").select("p").isEmpty());
        assertEquals(2, result.select("dd").get(1).select("p").size());
        assertEquals("Anchored definition", result.selectFirst("dd > p#definition").text());
        assertEquals("Quote", result.selectFirst("blockquote#kept.message").text());
        assertEquals("#kept", result.selectFirst("dd a").attr("href"));
        assertTrue(result.select("div.quoteblock,table.data colgroup,table.data thead").isEmpty());
        assertEquals("data", result.selectFirst("table.data").className());
        assertEquals(2, result.select("table.data tr").size());
        assertEquals("Name Contributor", result.selectFirst("table.data").text());
        assertEquals(1, result.select("table.tableblock colgroup").size());
        assertEquals(1, result.select("table.tableblock thead").size());
    }

    /** Restores opted-in plain table sizing while leaving unmarked AsciiDoc tables and authored content intact. */
    @Test
    void adaptsMarkedPlainTablesOnly() {
        String source = """
                <table id="plain" class="tableblock frame-all grid-all stretch legacy-plain authored">
                <colgroup><col style="width:33.3333%"></colgroup>
                <thead><tr><th><p class="tableblock">Heading</p></th></tr></thead>
                <tbody><tr><td><p class="tableblock"><a href="#kept">{literal} &amp; text</a></p></td></tr></tbody></table>
                <table id="unmarked" class="tableblock frame-all grid-all stretch">
                <colgroup><col style="width:33.3333%"></colgroup>
                <thead><tr><th>Unmarked</th></tr></thead><tbody><tr><td>Keep width</td></tr></tbody></table>
                """;
        String html = new LegacyMarkup().render(source);
        Document result = Jsoup.parseBodyFragment(html);
        assertEquals("authored", result.selectFirst("#plain").className());
        assertTrue(result.select("#plain colgroup,#plain thead").isEmpty());
        assertFalse(html.substring(html.indexOf("<table id=\"plain\""), html.indexOf("</table>")).contains("<tbody>"));
        assertEquals(2, result.select("#plain tr").size());
        assertEquals("Heading", result.selectFirst("#plain th").text());
        assertEquals("{literal} & text", result.selectFirst("#plain td").text());
        assertEquals("#kept", result.selectFirst("#plain a").attr("href"));
        assertEquals("tableblock frame-all grid-all stretch", result.selectFirst("#unmarked").className());
        assertEquals("width:33.3333%", result.selectFirst("#unmarked col").attr("style"));
        assertEquals(1, result.select("#unmarked thead").size());
        assertEquals(1, result.select("#unmarked tbody").size());
    }

    /** Keeps the introduction in the legacy guide header and removes the layout's duplicate document title. */
    @Test
    void groupsGuideIntroduction() {
        String source = "<h1>Guide title</h1><div class='paragraph'><p>Intro</p></div><ul><li>Goal</li></ul>"
                + "<h3 id='first'>First chapter</h3><p>Chapter body</p>";
        LegacyMarkup.GuideBody body = new LegacyMarkup().guide(source);
        Document introduction = Jsoup.parseBodyFragment(body.introduction());
        Document sections = Jsoup.parseBodyFragment(body.sections());
        assertEquals("Intro Goal", introduction.text());
        assertTrue(introduction.select("h1,h3").isEmpty());
        assertEquals("First chapter", sections.getElementById("first").text());
        assertEquals(1, sections.select("p").size());
    }
}
