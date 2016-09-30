package meghanada.parser;

import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import meghanada.parser.source.JavaSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class JavaParser implements SourceParser {

    private static Logger log = LogManager.getLogger(JavaParser.class);
    public Map<String, String> globalClassSymbol;
    private JavaSymbolAnalyzeVisitor analyzeVisitor;

    public JavaParser() throws IOException {
        this.analyzeVisitor = new JavaSymbolAnalyzeVisitor();
    }

    @Override
    public JavaSource parse(final File file) throws IOException, ParseException {
        if (!JavaSource.isJavaFile(file)) {
            throw new IllegalArgumentException("Support only java file");
        }
        final CompilationUnit cu = com.github.javaparser.JavaParser.parse(file, "UTF-8");
        final File src = file.getCanonicalFile();

        log.debug("start parse:{}", src);
        final JavaSource source = new JavaSource(src);
        this.analyzeVisitor.visit(cu, source);
        log.debug("end   parse:{}", src);
        return source;
    }

}
