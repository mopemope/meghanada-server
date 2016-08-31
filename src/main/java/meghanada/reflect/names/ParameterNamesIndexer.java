package meghanada.reflect.names;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ParameterNamesIndexer {

    private static final String[] filterPackage = new String[]{
            "sun.",
            "com.sun.",
            "com.oracle",
            "oracle.jrockit",
            "jdk",
            "org.omg",
            "org.ietf.",
            "org.jcp.",
            "netscape"
    };
    private static Logger log = LogManager.getLogger(ParameterNamesIndexer.class);

    private Kryo kryo = new Kryo();

    public ParameterNamesIndexer() {
        kryo.register(MethodParameterNames.class);
    }

    public static void main(String args[]) throws IOException, ParseException {
        ParameterNamesIndexer parameterNamesIndexer = new ParameterNamesIndexer();
        File srcZip = new File(System.getProperty("java.home"), "../src.zip");
        parameterNamesIndexer.createIndex(srcZip);

    }

    private boolean ignorePackage(String target) {
        for (String pkg : ParameterNamesIndexer.filterPackage) {
            if (target.startsWith(pkg)) {
                return true;
            }
        }
        return false;
    }

    void createIndex(File src) throws IOException, ParseException {
        ZipFile zipFile = new ZipFile(src);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry zipEntry = entries.nextElement();
            String fileName = zipEntry.getName();
            String javaName = fileName.replace(File.separator, ".");

            if (fileName.endsWith(".java") && !ignorePackage(javaName)) {
                // log.debug("javaName {}", javaName);
                this.serializeParams(zipFile, zipEntry, javaName);
            }
        }
    }

    private void serializeParams(ZipFile zipFile, ZipEntry zipEntry, String javaName) throws IOException, ParseException {
        // log.debug("file {}", fileName);
        try (InputStream in = zipFile.getInputStream(zipEntry)) {
            String fqcn = javaName.substring(0, javaName.length() - 5);
            CompilationUnit cu = JavaParser.parse(in, "UTF-8");
            ParameterNameVisitor visitor = new ParameterNameVisitor(fqcn);
            visitor.visit(cu, this);

            // log.debug("{} classes {}", javaName, visitor.parameterNamesList.size());
            for (MethodParameterNames mpn : visitor.parameterNamesList) {
                if (mpn.names.size() > 0) {
                    // log.debug("{} {}", javaName, mpn.className);
                    String pkg = ClassNameUtils.getPackage(fqcn);
                    String simpleName = ClassNameUtils.getSimpleName(fqcn);
                    String dirPath = pkg.replace(".", "/");
                    String fileName = mpn.className.substring(pkg.length() + 1).replace(".", "$") + ".param";

                    File outFile = new File("./resources/params/" + dirPath, fileName);
                    boolean result = outFile.getParentFile().mkdirs();
                    try (Output out = new Output(new FileOutputStream(outFile))) {
                        kryo.writeObject(out, mpn);
                        log.debug("output {}", outFile);
                    }

                    try (Input input = new Input(new FileInputStream(outFile))) {
                        MethodParameterNames mpn2 = kryo.readObject(input, MethodParameterNames.class);
                        // log.debug("mpn {}", mpn);
                    }

                }
            }
        }
    }

}
