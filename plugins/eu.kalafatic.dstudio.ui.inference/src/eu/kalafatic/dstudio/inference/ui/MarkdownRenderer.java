package eu.kalafatic.dstudio.inference.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Display;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Self-contained markdown renderer for StyledText: headings, emphasis, inline code,
 * fenced code blocks with syntax highlighting, lists, block quotes, links and
 * horizontal rules. No external markdown dependency; SWT-only.
 */
public final class MarkdownRenderer {

    private static final Pattern INLINE = Pattern.compile(
            "(\\*\\*[^*\\n]+\\*\\*)|(__[^_\\n]+__)|(`[^`\\n]+`)|(\\[[^\\]\\n]+\\]\\([^)\\n]*\\))"
                    + "|(\\*[^*\\n]+\\*)|(_[^_\\n]+_)");

    private final Display display;
    private final Font monoFont;
    private final Color headingColor;
    private final Color linkColor;
    private final Color quoteColor;
    private final Color inlineCodeColor;
    private final Color inlineCodeBackground;
    private final Color codeBackground;
    private final CodeHighlighter.HighlightStyle highlightStyle;

    public MarkdownRenderer(Display display) {
        this.display = display;
        this.monoFont = new Font(display, new FontData("Monospaced", 10, SWT.NORMAL));
        this.headingColor = new Color(display, 0, 51, 153);
        this.linkColor = new Color(display, 0, 102, 204);
        this.quoteColor = new Color(display, 92, 92, 92);
        this.inlineCodeColor = new Color(display, 153, 0, 0);
        this.inlineCodeBackground = new Color(display, 238, 238, 238);
        this.codeBackground = new Color(display, 245, 245, 245);
        this.highlightStyle = CodeHighlighter.defaultPalette(display);
    }

    public void dispose() {
        monoFont.dispose();
        headingColor.dispose();
        linkColor.dispose();
        quoteColor.dispose();
        inlineCodeColor.dispose();
        inlineCodeBackground.dispose();
        codeBackground.dispose();
        highlightStyle.keyword().dispose();
        highlightStyle.string().dispose();
        highlightStyle.comment().dispose();
        highlightStyle.number().dispose();
        highlightStyle.type().dispose();
    }

    /** Renders markdown into the widget, replacing its content and styles. */
    public void render(StyledText widget, String markdown) {
        if (markdown == null) {
            markdown = "";
        }
        widget.setStyleRanges(new StyleRange[0]);
        widget.setText("");
        List<Block> blocks = parseBlocks(markdown);
        for (Block block : blocks) {
            renderBlock(widget, block);
        }
    }

    // ---------------------------------------------------------------- blocks

    private enum BlockType {PARAGRAPH, HEADING, CODE, LIST_ITEM, QUOTE, RULE}

    private record Block(BlockType type, String text, int level, String language) {
    }

    private static List<Block> parseBlocks(String markdown) {
        List<Block> blocks = new ArrayList<>();
        String[] lines = markdown.split("\n", -1);
        List<String> paragraph = new ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            String rawLine = lines[index];
            String line = rawLine.endsWith("\r") ? rawLine.substring(0, rawLine.length() - 1)
                    : rawLine;
            String fence = fenceOf(line);
            if (fence != null) {
                flushParagraph(blocks, paragraph);
                List<String> code = new ArrayList<>();
                int i = index + 1;
                for (; i < lines.length; i++) {
                    String candidate = lines[i].endsWith("\r")
                            ? lines[i].substring(0, lines[i].length() - 1) : lines[i];
                    if (candidate.strip().startsWith("```") || candidate.strip().startsWith("~~~")) {
                        break;
                    }
                    code.add(candidate);
                }
                index = i; // skip the closing fence
                blocks.add(new Block(BlockType.CODE, String.join("\n", code), 0, fence));
                continue;
            }
            String stripped = line.strip();
            if (stripped.isEmpty()) {
                flushParagraph(blocks, paragraph);
                continue;
            }
            if (stripped.matches("^(---+|\\*\\*\\*+|___+)$")) {
                flushParagraph(blocks, paragraph);
                blocks.add(new Block(BlockType.RULE, "", 0, null));
                continue;
            }
            if (stripped.startsWith(">")) {
                flushParagraph(blocks, paragraph);
                blocks.add(new Block(BlockType.QUOTE,
                        stripped.replaceAll("^>\\s?", ""), 0, null));
                continue;
            }
            Matcher heading = Pattern.compile("^(#{1,6})\\s+(.*)$").matcher(stripped);
            if (heading.matches()) {
                flushParagraph(blocks, paragraph);
                blocks.add(new Block(BlockType.HEADING, heading.group(2),
                        heading.group(1).length(), null));
                continue;
            }
            Matcher list = Pattern.compile("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$")
                    .matcher(stripped);
            if (list.matches()) {
                flushParagraph(blocks, paragraph);
                String marker = list.group(2).matches("\\d+[.)]")
                        ? list.group(2).replace(")", ".") : "\u2022";
                blocks.add(new Block(BlockType.LIST_ITEM,
                        list.group(1) + marker + " " + list.group(3), 0, null));
                continue;
            }
            paragraph.add(line);
        }
        flushParagraph(blocks, paragraph);
        return blocks;
    }

    private static String fenceOf(String line) {
        String stripped = line.strip();
        if (stripped.startsWith("```")) {
            String language = stripped.substring(3).trim();
            return language.isBlank() ? "text" : language;
        }
        if (stripped.startsWith("~~~")) {
            String language = stripped.substring(3).trim();
            return language.isBlank() ? "text" : language;
        }
        return null;
    }

    private static void flushParagraph(List<Block> blocks, List<String> paragraph) {
        if (!paragraph.isEmpty()) {
            blocks.add(new Block(BlockType.PARAGRAPH, String.join("\n", paragraph), 0,
                    null));
            paragraph.clear();
        }
    }

    // ---------------------------------------------------------------- render

    private void renderBlock(StyledText widget, Block block) {
        switch (block.type()) {
            case HEADING -> renderHeading(widget, block);
            case CODE -> renderCode(widget, block);
            case LIST_ITEM -> renderInline(widget, block.text(), false, true);
            case QUOTE -> renderQuote(widget, block.text());
            case RULE -> renderRule(widget);
            default -> renderInline(widget, block.text(), false, false);
        }
    }

    private void renderHeading(StyledText widget, Block block) {
        int start = widget.getCharCount();
        widget.append(block.text() + "\n");
        StyleRange range = new StyleRange(start, block.text().length(), headingColor, null,
                SWT.BOLD);
        switch (block.level()) {
            case 1 -> range.fontStyle = SWT.BOLD;
            case 2 -> range.fontStyle = SWT.BOLD;
            default -> range.fontStyle = SWT.NORMAL;
        }
        widget.setStyleRange(range);
    }

    private void renderCode(StyledText widget, Block block) {
        String[] lines = block.text().split("\n", -1);
        int offset = widget.getCharCount();
        for (String line : lines) {
            List<StyleRange> ranges = new ArrayList<>();
            CodeHighlighter.highlightLine(line, block.language(), offset, ranges,
                    highlightStyle);
            ranges.sort(java.util.Comparator.comparingInt(r -> r.start));
            int pos = offset;
            for (StyleRange range : ranges) {
                if (range.start > pos) {
                    widget.setStyleRange(plainRange(pos, range.start - pos));
                }
                range.font = monoFont;
                range.background = codeBackground;
                widget.setStyleRange(range);
                pos = range.start + range.length;
            }
            widget.setStyleRange(plainRange(pos, offset + line.length() - pos));
            widget.append(line + "\n");
            offset += line.length() + 1;
        }
    }

    private StyleRange plainRange(int start, int length) {
        if (length <= 0) {
            return new StyleRange(start, 0, null, null, SWT.NORMAL);
        }
        StyleRange range = new StyleRange(start, length, null, null, SWT.NORMAL);
        range.background = codeBackground;
        range.font = monoFont;
        return range;
    }

    private void renderQuote(StyledText widget, String text) {
        int start = widget.getCharCount();
        widget.append(text + "\n");
        widget.setStyleRange(new StyleRange(start, text.length(), quoteColor, null,
                SWT.ITALIC));
    }

    private void renderRule(StyledText widget) {
        widget.append("  \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500"
                + "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\n");
    }

    private void renderInline(StyledText widget, String text, boolean heading,
                              boolean list) {
        int start = widget.getCharCount();
        widget.append(text + "\n");
        Matcher matcher = INLINE.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            cursor = matcher.start();
            String group = matcher.group();
            if (group.startsWith("**") || group.startsWith("__")) {
                String inner = group.substring(2, group.length() - 2);
                int innerStart = start + matcher.start() + 2;
                widget.setStyleRange(new StyleRange(innerStart, inner.length(), null, null,
                        SWT.BOLD));
            } else if (group.startsWith("`")) {
                String inner = group.substring(1, group.length() - 1);
                int innerStart = start + matcher.start() + 1;
                StyleRange inlineCode = new StyleRange(innerStart, inner.length(),
                        inlineCodeColor, null, SWT.NORMAL);
                inlineCode.background = inlineCodeBackground;
                inlineCode.font = monoFont;
                widget.setStyleRange(inlineCode);
            } else if (group.startsWith("[")) {
                int close = group.indexOf("](");
                String label = group.substring(1, close);
                int labelStart = start + matcher.start() + 1;
                widget.setStyleRange(new StyleRange(labelStart, label.length(), linkColor,
                        null, SWT.NORMAL));
                widget.setStyleRange(new StyleRange(labelStart, label.length(), null, null,
                        SWT.NORMAL));
                StyleRange link = new StyleRange(labelStart, label.length(), linkColor,
                        null, SWT.NORMAL);
                link.underline = true;
                widget.setStyleRange(link);
            } else {
                String inner = group.substring(1, group.length() - 1);
                int innerStart = start + matcher.start() + 1;
                widget.setStyleRange(new StyleRange(innerStart, inner.length(), null, null,
                        SWT.ITALIC));
            }
        }
        if (list) {
            widget.setStyleRange(new StyleRange(start,
                    Math.min(text.length(), 2), headingColor, null, SWT.BOLD));
        }
    }
}
