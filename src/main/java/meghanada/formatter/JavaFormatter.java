package meghanada.formatter;

import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;

import java.util.Properties;
import java.util.regex.Pattern;

public class JavaFormatter {

    private static final Pattern NEW_LINE = Pattern.compile("\n");

    private JavaFormatter() {
    }

    public static Properties getDefaultProperties() {
        final Properties properties = new Properties();
        properties.setProperty(DefaultCodeFormatterConstants.FORMATTER_BLANK_LINES_AFTER_IMPORTS, "1");
        properties.setProperty(DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT, "200");
        properties.setProperty(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, "space");
        properties.setProperty(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");
        properties.setProperty(DefaultCodeFormatterConstants.FORMATTER_JOIN_WRAPPED_LINES, "false");
        properties.setProperty(DefaultCodeFormatterConstants.FORMATTER_ALIGNMENT_FOR_RESOURCES_IN_TRY, "18");
        return properties;
    }

    public static String format(final String content) {
        return format(getDefaultProperties(), content);
    }

    public static String format(final Properties prop, final String content) {
        final CodeFormatter codeFormatter = ToolFactory.createCodeFormatter(prop);
        final IDocument document = new Document(content);
        try {
            final TextEdit textEdit = codeFormatter.format(CodeFormatter.K_COMPILATION_UNIT | CodeFormatter.F_INCLUDE_COMMENTS,
                    content,
                    0,
                    content.length(),
                    0,
                    null);
            if (textEdit != null) {
                textEdit.apply(document);
            } else {
                return content;
            }
        } catch (final BadLocationException e) {
            throw new RuntimeException(e);
        }

        return ensureCorrectNewLines(document.get());
    }

    private static String ensureCorrectNewLines(final String content) {
        final String newLine = System.getProperty("line.separator");

        if (content.contains("\n") && !content.contains(newLine)) {
            return NEW_LINE.matcher(content).replaceAll(newLine);
        }
        return content;
    }
}
