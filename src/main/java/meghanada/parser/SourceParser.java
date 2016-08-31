package meghanada.parser;

import com.github.javaparser.ParseException;

import java.io.File;
import java.io.IOException;

interface SourceParser {

    JavaSource parse(File file) throws IOException, ParseException;

}
