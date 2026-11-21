package eu.kalafatic.dstudio.inference.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight line-based syntax highlighter for fenced code blocks. Language-specific
 * keyword sets plus universal string/comment/number patterns. No external dependencies.
 */
public final class CodeHighlighter {

    public record HighlightStyle(Color keyword, Color string, Color comment,
                                 Color number, Color type) {
    }

    private static final Set<String> JAVA = Set.of(
            "public", "private", "protected", "class", "interface", "enum", "record",
            "extends", "implements", "static", "final", "void", "int", "long", "double",
            "float", "boolean", "char", "byte", "short", "new", "return", "if", "else",
            "for", "while", "do", "switch", "case", "default", "break", "continue",
            "try", "catch", "finally", "throw", "throws", "import", "package", "this",
            "super", "instanceof", "null", "true", "false", "var", "abstract",
            "synchronized", "volatile", "transient", "native", "assert", "strictfp",
            "sealed", "permits", "non-sealed", "yield", "lambda", "module", "requires",
            "exports", "opens", "provides", "uses", "with", "to", "transitive");

    private static final Set<String> PYTHON = Set.of(
            "def", "class", "return", "if", "elif", "else", "for", "while", "import",
            "from", "as", "with", "try", "except", "finally", "raise", "pass", "break",
            "continue", "lambda", "yield", "global", "nonlocal", "assert", "del", "in",
            "not", "and", "or", "is", "None", "True", "False", "async", "await", "self");

    private static final Set<String> JAVASCRIPT = Set.of(
            "function", "const", "let", "var", "return", "if", "else", "for", "while",
            "do", "switch", "case", "break", "continue", "class", "extends", "new",
            "this", "super", "import", "export", "from", "default", "async", "await",
            "try", "catch", "finally", "throw", "typeof", "instanceof", "null",
            "undefined", "true", "false", "of", "in", "delete", "void", "yield",
            "static", "get", "set", "interface", "type", "enum", "implements",
            "package", "private", "public", "protected", "readonly");

    private static final Set<String> TYPESCRIPT = union(JAVASCRIPT, Set.of(
            "interface", "type", "enum", "implements", "namespace", "declare",
            "readonly", "public", "private", "protected", "abstract", "any", "unknown",
            "never", "string", "number", "boolean", "keyof", "infer", "extends",
            "satisfies"));

    private static final Set<String> SHELL = Set.of(
            "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case",
            "esac", "function", "return", "exit", "echo", "export", "local", "set",
            "unset", "shift", "source", "cd", "mv", "cp", "rm", "mkdir", "ls", "cat",
            "grep", "sed", "awk", "in");

    private static final Set<String> SQL = Set.of(
            "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET",
            "DELETE", "CREATE", "TABLE", "ALTER", "DROP", "INDEX", "VIEW", "JOIN",
            "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "ON", "GROUP", "BY", "ORDER",
            "HAVING", "LIMIT", "OFFSET", "AND", "OR", "NOT", "NULL", "PRIMARY", "KEY",
            "FOREIGN", "REFERENCES", "DEFAULT", "UNIQUE", "AS", "DISTINCT", "UNION",
            "CASE", "WHEN", "THEN", "ELSE", "END", "BEGIN", "COMMIT", "ROLLBACK",
            "CONSTRAINT", "AUTOINCREMENT", "INTEGER", "TEXT", "REAL", "BLOB");

    private static final Set<String> XML = Set.of(
            "xml", "version", "encoding", "DOCTYPE", "ELEMENT", "ATTLIST", "ENTITY");

    private static final Set<String> C_LIKE = Set.of(
            "int", "char", "float", "double", "long", "short", "unsigned", "signed",
            "void", "struct", "union", "enum", "typedef", "static", "const", "extern",
            "register", "volatile", "inline", "return", "if", "else", "for", "while",
            "do", "switch", "case", "default", "break", "continue", "goto", "sizeof",
            "null", "true", "false", "class", "public", "private", "protected",
            "namespace", "using", "template", "typename", "virtual", "override",
            "new", "delete", "this", "auto", "constexpr", "include", "define");

    private static final Set<String> YAML = Set.of(
            "true", "false", "null", "yes", "no", "on", "off");

    private static final Set<String> GO = Set.of(
            "func", "package", "import", "var", "const", "type", "struct", "interface",
            "map", "chan", "go", "defer", "select", "range", "return", "if", "else",
            "for", "switch", "case", "default", "break", "continue", "fallthrough",
            "nil", "true", "false", "make", "new", "len", "cap", "append", "copy",
            "panic", "recover", "error", "string", "int", "bool");

    private static final Set<String> RUST = Set.of(
            "fn", "let", "mut", "const", "static", "struct", "enum", "trait", "impl",
            "pub", "use", "mod", "crate", "self", "super", "match", "if", "else",
            "for", "while", "loop", "in", "return", "break", "continue", "move",
            "ref", "dyn", "where", "async", "await", "true", "false", "Some", "None",
            "Ok", "Err", "Result", "Option", "Vec", "String", "Box", "type");

    private static final Set<String> JSON_KEYWORDS = Set.of(
            "true", "false", "null");

    private static final Pattern STRING_PATTERN = Pattern.compile(
            "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'");
    private static final Pattern COMMENT_PATTERN = Pattern.compile(
            "//[^\n]*|#[^\n]*|--[^\n]*|/\\*[\\s\\S]*?\\*/");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d[\\d_.a-fA-FxX]*\\b");
    private static final Pattern TYPE_PATTERN = Pattern.compile("\\b[A-Z][A-Za-z0-9_]*\\b");

    private CodeHighlighter() {
    }

    @SafeVarargs
    private static Set<String> union(Set<String>... sets) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        for (Set<String> set : sets) {
            result.addAll(set);
        }
        return result;
    }

    private static Set<String> keywordsFor(String language) {
        if (language == null) {
            return Set.of();
        }
        return switch (language.toLowerCase()) {
            case "java", "kotlin", "scala", "groovy" -> JAVA;
            case "py", "python" -> PYTHON;
            case "js", "javascript", "jsx", "mjs", "cjs" -> JAVASCRIPT;
            case "ts", "typescript", "tsx" -> TYPESCRIPT;
            case "sh", "bash", "zsh", "shell", "console" -> SHELL;
            case "sql" -> SQL;
            case "xml", "html", "xhtml", "svg", "pom" -> XML;
            case "c", "cpp", "cc", "h", "hpp", "cs", "swift", "objc", "php" -> C_LIKE;
            case "yaml", "yml" -> YAML;
            case "go", "golang" -> GO;
            case "rs", "rust" -> RUST;
            case "json", "jsonc", "json5" -> JSON_KEYWORDS;
            default -> Set.of();
        };
    }

    /**
     * Computes style ranges for one source line, appending to {@code target} at
     * {@code lineStart} offset.
     */
    public static void highlightLine(String line, String language, int lineStart,
                                     List<StyleRange> target, HighlightStyle style) {
        Set<String> keywords = keywordsFor(language);
        highlightWith(line, lineStart, target, style, keywords, language);
    }

    private static void highlightWith(String line, int offset, List<StyleRange> target,
                                      HighlightStyle style, Set<String> keywords,
                                      String language) {
        boolean xmlMode = language != null && Set.of("xml", "html", "xhtml", "svg")
                .contains(language.toLowerCase());
        // Comments first (they can contain anything else).
        Matcher comments = COMMENT_PATTERN.matcher(line);
        List<int[]> protectedRanges = new ArrayList<>();
        while (comments.find()) {
            target.add(range(offset + comments.start(), comments.end() - comments.start(),
                    style.comment(), SWT.ITALIC));
            protectedRanges.add(new int[]{comments.start(), comments.end()});
        }
        // Strings.
        Matcher strings = STRING_PATTERN.matcher(line);
        while (strings.find()) {
            if (inProtected(strings.start(), strings.end(), protectedRanges)) {
                continue;
            }
            target.add(range(offset + strings.start(),
                    strings.end() - strings.start(), style.string(), SWT.NORMAL));
            protectedRanges.add(new int[]{strings.start(), strings.end()});
        }
        if (xmlMode) {
            Matcher tags = Pattern.compile("</?[A-Za-z][\\w:.-]*|/?>").matcher(line);
            while (tags.find()) {
                if (inProtected(tags.start(), tags.end(), protectedRanges)) {
                    continue;
                }
                target.add(range(offset + tags.start(), tags.end() - tags.start(),
                        style.keyword(), SWT.NORMAL));
            }
            return;
        }
        if (!keywords.isEmpty()) {
            Matcher words = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*").matcher(line);
            while (words.find()) {
                if (inProtected(words.start(), words.end(), protectedRanges)) {
                    continue;
                }
                if (keywords.contains(words.group())) {
                    target.add(range(offset + words.start(),
                            words.end() - words.start(), style.keyword(), SWT.BOLD));
                }
            }
        }
        Matcher types = TYPE_PATTERN.matcher(line);
        while (types.find()) {
            if (inProtected(types.start(), types.end(), protectedRanges)) {
                continue;
            }
            if (types.end() - types.start() > 2) {
                target.add(range(offset + types.start(),
                        types.end() - types.start(), style.type(), SWT.NORMAL));
            }
        }
        Matcher numbers = NUMBER_PATTERN.matcher(line);
        while (numbers.find()) {
            if (inProtected(numbers.start(), numbers.end(), protectedRanges)) {
                continue;
            }
            target.add(range(offset + numbers.start(),
                    numbers.end() - numbers.start(), style.number(), SWT.NORMAL));
        }
    }

    private static boolean inProtected(int start, int end, List<int[]> ranges) {
        for (int[] range : ranges) {
            if (start < range[1] && end > range[0]) {
                return true;
            }
        }
        return false;
    }

    private static StyleRange range(int start, int length, Color color, int fontStyle) {
        StyleRange range = new StyleRange(start, length, color, null, fontStyle);
        return range;
    }

    /** Default palette, tuned for light workbench themes. */
    public static HighlightStyle defaultPalette(Display display) {
        return new HighlightStyle(
                new Color(display, 127, 0, 85),    // keyword
                new Color(display, 42, 0, 255),    // string
                new Color(display, 63, 127, 95),   // comment
                new Color(display, 125, 125, 125), // number
                new Color(display, 0, 128, 128));  // type
    }
}
