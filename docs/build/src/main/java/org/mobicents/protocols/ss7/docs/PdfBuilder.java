package org.mobicents.protocols.ss7.docs;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.util.XRLog;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

/**
 * Binds docs/Chapter-*.md into PDF books as listed in build/books.properties. Markdown -> HTML with flexmark (GFM tables,
 * heading ids), GitHub alert blockquotes to styled boxes, shell examples coloured by line, chapter-prefixed anchors so
 * chapters can be concatenated, then HTML -> PDF with openhtmltopdf. Pure Java, no external tools.
 * <p>
 * Usage: PdfBuilder &lt;docsDir&gt; &lt;outDir&gt;
 */
public class PdfBuilder {

    private static final Pattern ALERT = Pattern.compile(
            "<blockquote>\\s*<p>\\[!(NOTE|TIP|WARNING|IMPORTANT|CAUTION)\\]\\s*(.*?)</blockquote>", Pattern.DOTALL);
    private static final Pattern PRE = Pattern.compile("<pre><code(?: class=\"([^\"]*)\")?>(.*?)</code></pre>", Pattern.DOTALL);
    private static final Pattern TITLE = Pattern.compile("^# (.+)$", Pattern.MULTILINE);

    private final Parser parser;
    private final HtmlRenderer renderer;
    private final Path docsDir;

    PdfBuilder(Path docsDir) {
        this.docsDir = docsDir;
        MutableDataSet options = new MutableDataSet()
                .set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create()))
                .set(HtmlRenderer.GENERATE_HEADER_ID, true)
                .set(HtmlRenderer.RENDER_HEADER_ID, true);
        this.parser = Parser.builder(options).build();
        this.renderer = HtmlRenderer.builder(options).build();
    }

    public static void main(String[] args) throws Exception {
        XRLog.setLoggingEnabled(false);
        Path docsDir = Paths.get(args[0]);
        Path outDir = Paths.get(args[1]);
        Files.createDirectories(outDir);

        Properties books = new Properties();
        try (InputStream in = Files.newInputStream(docsDir.resolve("build/books.properties"))) {
            books.load(in);
        }
        Map<String, String> chapterToBook = new HashMap<>();
        for (String book : books.stringPropertyNames()) {
            for (String chapter : books.getProperty(book).split("\\s*,\\s*")) chapterToBook.put(chapter, book);
        }
        PdfBuilder builder = new PdfBuilder(docsDir);
        for (String book : new TreeMap<>(books).keySet().toArray(new String[0])) {
            List<String> chapters = Arrays.asList(books.getProperty(book).split("\\s*,\\s*"));
            builder.buildBook(book, chapters, chapterToBook, outDir);
        }
    }

    void buildBook(String book, List<String> chapterFiles, Map<String, String> chapterToBook, Path outDir) throws Exception {
        Path pdf = outDir.resolve(book + ".pdf");
        String css = new String(Files.readAllBytes(docsDir.resolve("build/pdf.css")), StandardCharsets.UTF_8);
        List<String> titles = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        for (String file : chapterFiles) {
            String markdown = new String(Files.readAllBytes(docsDir.resolve(file)), StandardCharsets.UTF_8);
            Matcher t = TITLE.matcher(markdown);
            titles.add(t.find() ? t.group(1) : file);
            String html = colourExamples(alerts(renderer.render(parser.parse(markdown))));
            html = prefixAnchors(html, slug(file), chapterFiles);
            body.append("<div class=\"chapter\">").append(crossBookReferences(html, book, chapterToBook)).append("</div>");
        }
        checkAnchors(book, body.toString());

        StringBuilder cover = new StringBuilder("<div class=\"cover\"><h1>").append(book.replace('_', ' ')).append("</h1><ol>");
        for (int i = 0; i < titles.size(); i++) {
            cover.append("<li><a href=\"#").append(slug(chapterFiles.get(i))).append("\">").append(titles.get(i)).append("</a></li>");
        }
        cover.append("</ol></div>");

        String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"/><title>" + book.replace('_', ' ')
                + "</title><style>" + css + "</style></head><body>" + cover + body + "</body></html>";
        try (OutputStream os = Files.newOutputStream(pdf)) {
            String baseUri = docsDir.toUri().toString();
            PdfRendererBuilder pb = new PdfRendererBuilder();
            pb.useFastMode();
            pb.withW3cDocument(new W3CDom().fromJsoup(Jsoup.parse(html, baseUri)), baseUri);
            pb.toStream(os);
            pb.run();
        }
        System.out.println("wrote " + pdf + " (" + Files.size(pdf) / 1024 + " KB, " + chapterFiles.size() + " chapters)");
    }

    /** "Chapter-Shell.md" -> "chapter-shell": the id of the chapter's h1 and the prefix of every anchor inside it. */
    static String slug(String file) {
        return file.replaceAll("\\.md$", "").toLowerCase();
    }

    /**
     * Makes a chapter's anchors unique inside the book: ids and in-page links get the chapter prefix, the h1 gets the
     * chapter id itself, and links to another chapter of the same book ("Chapter-X.md#y") become internal links.
     */
    static String prefixAnchors(String html, String chapter, List<String> chapterFiles) {
        html = html.replaceFirst("<h1 id=\"[^\"]*\">", "<h1 id=\"" + chapter + "\">");
        html = html.replaceAll("<h([2-6]) id=\"([^\"]+)\"", "<h$1 id=\"" + chapter + "-$2\"");
        html = html.replaceAll("href=\"#([^\"]+)\"", "href=\"#" + chapter + "-$1\"");
        for (String other : chapterFiles) {
            String o = slug(other);
            html = html.replace("href=\"" + other + "#", "href=\"#" + o + "-").replace("href=\"" + other + "\"", "href=\"#" + o + "\"");
        }
        return html;
    }

    /**
     * A link to a chapter that lives in another book cannot be followed inside a PDF, so it becomes plain text naming
     * that book: "Shell Command Reference (JSS7 Shell Guide)". A link to a file that is in no book keeps only its text.
     * Links to the same book were already rewritten to anchors.
     */
    static String crossBookReferences(String html, String book, Map<String, String> chapterToBook) {
        Matcher m = Pattern.compile("<a href=\"([^\"#]+\\.md)(?:#[^\"]*)?\">(.*?)</a>").matcher(html);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String otherBook = chapterToBook.get(m.group(1));
            String ref = otherBook == null ? "" : " <span class=\"xref\">(" + otherBook.replace('_', ' ') + ")</span>";
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(2) + ref));
        }
        return m.appendTail(sb).toString();
    }

    /** Every in-book link must have a heading with that id, otherwise a Contents list is stale. */
    static void checkAnchors(String book, String html) {
        Matcher m = Pattern.compile("href=\"#([^\"]+)\"").matcher(html);
        while (m.find()) {
            if (!html.contains("id=\"" + m.group(1) + "\"")) {
                throw new IllegalStateException(book + ": link to #" + m.group(1) + " has no matching heading");
            }
        }
    }

    /** {@code > [!NOTE]} blockquotes become {@code <div class="note"><p class="title">Note</p>...</div>}. */
    static String alerts(String html) {
        Matcher m = ALERT.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String kind = m.group(1);
            String label = kind.charAt(0) + kind.substring(1).toLowerCase();
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    "<div class=\"" + kind.toLowerCase() + "\"><p class=\"title\">" + label + "</p><p>" + m.group(2) + "</div>"));
        }
        return m.appendTail(sb).toString();
    }

    /** Inside shell code blocks: commands bold blue, comments green, everything else (responses) muted italic. */
    static String colourExamples(String html) {
        Matcher m = PRE.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            boolean shell = m.group(1) == null;      // fenced blocks with a language (java) are left alone
            StringBuilder out = new StringBuilder();
            for (String ln : m.group(2).split("\n", -1)) {
                if (out.length() > 0) out.append('\n');
                if (!shell) out.append(ln);
                else if (ln.matches("(sccp|sctp|m3ua)\\b.*")) out.append("<span class=\"cmd\">").append(ln).append("</span>");
                else if (ln.startsWith("#")) out.append("<span class=\"cmt\">").append(ln).append("</span>");
                else if (!ln.trim().isEmpty() && !ln.startsWith("&lt;")) out.append("<span class=\"resp\">").append(ln).append("</span>");
                else out.append(ln);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement("<pre><code>" + out + "</code></pre>"));
        }
        return m.appendTail(sb).toString();
    }
}
