package meghanada.formatter;

import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
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
