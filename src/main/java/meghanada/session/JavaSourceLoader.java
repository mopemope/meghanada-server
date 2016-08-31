package meghanada.session;

import com.github.javaparser.ParseException;
import com.google.common.cache.CacheLoader;
import meghanada.parser.JavaParser;
import meghanada.parser.JavaSource;

import java.io.File;
import java.io.IOException;

public class JavaSourceLoader extends CacheLoader<File, JavaSource> {

    private JavaParser javaParser;

    public JavaSourceLoader() {
    }

    @Override
    public JavaSource load(File file) throws IOException, ParseException {
        if (this.javaParser == null) {
            this.javaParser = new JavaParser();
        }
        return javaParser.parse(file);
    }
}
