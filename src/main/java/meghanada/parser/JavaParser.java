package meghanada.parser;

import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import meghanada.reflect.asm.CachedASMReflector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class JavaParser implements SourceParser {

    private static Logger log = LogManager.getLogger(JavaParser.class);
    Map<String, String> globalClassSymbol;
    private JavaSymbolAnalyzeVisitor analyzeVisitor;

    public JavaParser() throws IOException {
        CachedASMReflector reflector = CachedASMReflector.getInstance();
        this.globalClassSymbol = reflector.getPackageClasses("java.lang");
        this.analyzeVisitor = new JavaSymbolAnalyzeVisitor(globalClassSymbol);
    }

    @Override
    public JavaSource parse(File file) throws IOException, ParseException {
        if (!JavaSource.isJavaFile(file)) {
            throw new IllegalArgumentException("Support only java file");
        }
        final CompilationUnit cu = com.github.javaparser.JavaParser.parse(file, "UTF-8");
        final File src = file.getCanonicalFile();

        log.debug("start parse:{}", src);
        JavaSource source = new JavaSource(src, this);
        this.analyzeVisitor.visit(cu, source);
        log.debug("end   parse:{}", src);
        return source;
    }

}
