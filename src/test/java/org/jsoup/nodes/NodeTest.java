package org.jsoup.nodes;

import org.jsoup.Jsoup;
import org.jsoup.TextUtil;
import org.jsoup.parser.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class NodeTest {

    private Document doc;

    @BeforeEach
    public void setUp() {
        doc = Jsoup.parse("<div><p></p></div>");
    }

    @Nested
    class BaseUriTests {
        @Test
        public void handlesBaseUri() {
            Tag tag = Tag.valueOf("a");
            Attributes attribs = new Attributes();
            attribs.put("relHref", "/foo");
            attribs.put("absHref", "http://bar/qux");

            Element noBase = new Element(tag, "", attribs);
            assertEquals("", noBase.absUrl("relHref"));
            assertEquals("http://bar/qux", noBase.absUrl("absHref"));

            Element withBase = new Element(tag, "http://foo/", attribs);
            assertEquals("http://foo/foo", withBase.absUrl("relHref"));
            assertEquals("http://bar/qux", withBase.absUrl("absHref"));
            assertEquals("", withBase.absUrl("noval"));

            Element dodgyBase = new Element(tag, "wtf://no-such-protocol/", attribs);
            assertEquals("http://bar/qux", dodgyBase.absUrl("absHref"));
            assertEquals("", dodgyBase.absUrl("relHref"));
        }

        @Test
        public void setBaseUriIsRecursive() {
            Document doc = Jsoup.parse("<div><p></p></div>");
            String baseUri = "https://jsoup.org";
            doc.setBaseUri(baseUri);

            assertEquals(baseUri, doc.baseUri());
            assertEquals(baseUri, doc.select("div").first().baseUri());
            assertEquals(baseUri, doc.select("p").first().baseUri());
        }
    }

    @Nested
    class AbsUrlTests {
        @Test
        public void handlesAbsPrefix() {
            Document doc = Jsoup.parse("<a href=/foo>Hello</a>", "https://jsoup.org/");
            Element a = doc.select("a").first();
            assertEquals("/foo", a.attr("href"));
            assertEquals("https://jsoup.org/foo", a.absUrl("href"));
            assertTrue(a.hasAttr("abs:href"));
        }

        @Test
        public void handlesAbsOnImage() {
            Document doc = Jsoup.parse("<p><img src=\"/rez/osi_logo.png\" /></p>", "https://jsoup.org/");
            Element img = doc.select("img").first();
            assertEquals("https://jsoup.org/rez/osi_logo.png", img.absUrl("src"));
            assertEquals(img.absUrl("src"), img.attr("abs:src"));
        }

        @Test
        public void handlesAbsPrefixOnHasAttr() {
            Document doc = Jsoup.parse("<a id=1 href='/foo'>One</a> <a id=2 href='https://jsoup.org/'>Two</a>");
            Element one = doc.select("#1").first();
            Element two = doc.select("#2").first();

            assertFalse(one.hasAttr("abs:href"));
            assertTrue(one.hasAttr("href"));
            assertEquals("", one.absUrl("href"));

            assertTrue(two.hasAttr("abs:href"));
            assertTrue(two.hasAttr("href"));
            assertEquals("https://jsoup.org/", two.absUrl("href"));
        }

        @Test
        public void literalAbsPrefix() {
            Document doc = Jsoup.parse("<a abs:href='odd'>One</a>");
            Element el = doc.select("a").first();
            assertTrue(el.hasAttr("abs:href"));
            assertEquals("odd", el.attr("abs:href"));
        }

        @Test
        public void handleAbsOnFileUris() {
            Document doc = Jsoup.parse("<a href='password'>One</a><a href='/var/log/messages'>Two</a>", "file:/etc/");
            Element one = doc.select("a").first();
            assertEquals("file:/etc/password", one.absUrl("href"));
            Element two = doc.select("a").get(1);
            assertEquals("file:/var/log/messages", two.absUrl("href"));
        }

        @Test
        public void handleAbsOnLocalhostFileUris() {
            Document doc = Jsoup.parse("<a href='password'>One</a><a href='/var/log/messages'>Two</a>", "file://localhost/etc/");
            Element one = doc.select("a").first();
            assertEquals("file://localhost/etc/password", one.absUrl("href"));
        }

        @Test
        public void handlesAbsOnProtocolessAbsoluteUris() {
            Document doc1 = Jsoup.parse("<a href='//example.net/foo'>One</a>", "http://example.com/");
            Document doc2 = Jsoup.parse("<a href='//example.net/foo'>One</a>", "https://example.com/");

            Element one = doc1.select("a").first();
            Element two = doc2.select("a").first();

            assertEquals("http://example.net/foo", one.absUrl("href"));
            assertEquals("https://example.net/foo", two.absUrl("href"));

            Document doc3 = Jsoup.parse("<img src=//www.google.com/images/errors/logo_sm.gif alt=Google>", "https://google.com");
            assertEquals("https://www.google.com/images/errors/logo_sm.gif", doc3.select("img").attr("abs:src"));
        }

        @Test
        public void absHandlesRelativeQuery() {
            Document doc = Jsoup.parse("<a href='?foo'>One</a> <a href='bar.html?foo'>Two</a>", "https://jsoup.org/path/file?bar");

            Element a1 = doc.select("a").first();
            assertEquals("https://jsoup.org/path/file?foo", a1.absUrl("href"));

            Element a2 = doc.select("a").get(1);
            assertEquals("https://jsoup.org/path/bar.html?foo", a2.absUrl("href"));
        }

        @Test
        public void absHandlesDotFromIndex() {
            Document doc = Jsoup.parse("<a href='./one/two.html'>One</a>", "http://example.com");
            Element a1 = doc.select("a").first();
            assertEquals("http://example.com/one/two.html", a1.absUrl("href"));
        }

        @Test
        public void handlesAbsOnUnknownProtocols() {
            // Mock handling for unknown protocols
            String[] urls = {"mailto:example@example.com", "tel:867-5309"};
            for (String url : urls) {
                Attributes attr = new Attributes().put("href", url);
                Element noBase = new Element(Tag.valueOf("a"), null, attr);
                assertEquals(url, noBase.absUrl("href"));

                Element withBase = new Element(Tag.valueOf("a"), "http://example.com/", attr);
                assertEquals(url, withBase.absUrl("href"));
            }
        }
    }

    @Nested
    class ManipulationTests {
        @Test
        public void testRemove() {
            Document doc = Jsoup.parse("<p>One <span>two</span> three</p>");
            Element p = doc.select("p").first();
            p.childNode(0).remove();

            assertEquals("two three", p.text());
            assertEquals("<span>two</span> three", TextUtil.stripNewlines(p.html()));
        }

        @Test
        public void removeOnOrphanIsNoop() {
            Element node = new Element("div");
            assertNull(node.parentNode());
            node.remove();
            assertNull(node.parentNode());
        }

        @Test
        public void testReplace() {
            Document doc = Jsoup.parse("<p>One <span>two</span> three</p>");
            Element p = doc.select("p").first();
            Element insert = doc.createElement("em").text("foo");
            p.childNode(1).replaceWith(insert);

            assertEquals("One <em>foo</em> three", p.html());
        }

        @Test
        public void ownerDocument() {
            Document doc = Jsoup.parse("<p>Hello");
            Element p = doc.select("p").first();
            assertSame(p.ownerDocument(), doc);
            assertSame(doc.ownerDocument(), doc);
            assertNull(doc.parent());
        }

        @Test
        public void root() {
            Document doc = Jsoup.parse("<div><p>Hello");
            Element p = doc.select("p").first();
            Node root = p.root();
            assertSame(doc, root);
            assertNull(root.parent());
            assertSame(doc.root(), doc);
        }

        @Test
        public void before() {
            Document doc = Jsoup.parse("<p>One <b>two</b> three</p><p>Four <b>five</b> six</p>");
            Element b = doc.select("b").first();
            b.before("<i>one and a half</i>");

            assertEquals("<p>One <i>one and a half</i><b>two</b> three</p><p>Four <b>five</b> six</p>", TextUtil.stripNewlines(doc.body().html()));
        }

        @Test
        public void beforeShuffle() {
            Document doc = Jsoup.parse("<p>One <b>two</b> three</p> <p> Four <b>five</b> six</p>");
            List<Node> nodes = doc.select("b").first().parent().childNodes();
            Node bNode = nodes.get(1);
            Node textNode = nodes.get(2);
            bNode.before(textNode);

            String expectedHtml = "<p>Onethree<b>two</b></p><p>Four<b>five</b>six</p>";
            String actualHtml = doc.body().html().replaceAll("\\s+", "").trim(); // Normalize whitespace

            assertEquals(expectedHtml, actualHtml);
        }


    }
}