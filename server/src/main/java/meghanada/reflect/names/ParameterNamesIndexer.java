package meghanada.reflect.names;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import meghanada.cache.GlobalCache;
import meghanada.store.Serializer;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nustaq.serialization.FSTConfiguration;

@SuppressWarnings("CheckReturnValue")
public class ParameterNamesIndexer {

  private static final String[] filterPackage =
      new String[] {
        "sun.",
        "com.oracle",
        "oracle.jrockit",
        "jdk",
        "org.omg",
        "org.ietf.",
        "org.jcp.",
        "netscape"
      };

  private static final FSTConfiguration fstConfiguration =
      FSTConfiguration.createDefaultConfiguration();

  private static final Logger log = LogManager.getLogger(ParameterNamesIndexer.class);

  static {
    fstConfiguration.registerClass(ParameterName.class, MethodParameterNames.class);
  }

  private ParameterNamesIndexer() {}

  public static void main(String[] args) throws Exception {
    ParameterNamesIndexer parameterNamesIndexer = new ParameterNamesIndexer();
    File srcZip = new File(System.getProperty("java.home"), "../src.zip");
    parameterNamesIndexer.createIndex(srcZip);
  }

  private static MethodParameterNames deserialize(File file) throws Exception {
    return Serializer.readObjectFromFile(file, MethodParameterNames.class);
  }

  private static void serialize(MethodParameterNames mpn, File file) throws Exception {
    Serializer.writeObjectToFile(file, mpn);
  }

  private static boolean ignorePackage(String target) {
    for (String pkg : ParameterNamesIndexer.filterPackage) {
      if (target.startsWith(pkg)) {
        return true;
      }
    }
    return false;
  }

  private void createIndex(File src) throws Exception {
    try (final ZipFile zipFile = new ZipFile(src)) {
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
  }

  private void serializeParams(
      final ZipFile zipFile, final ZipEntry zipEntry, final String javaName) throws Exception {

    try (InputStream in = zipFile.getInputStream(zipEntry)) {
      String fqcn = javaName.substring(0, javaName.length() - 5);
      CompilationUnit cu = JavaParser.parse(in, StandardCharsets.UTF_8);
      ParameterNameVisitor visitor = new ParameterNameVisitor(fqcn);
      visitor.visit(cu, this);

      // log.debug("{} classes {}", javaName, visitor.parameterNamesList.size());
      for (final MethodParameterNames mpn : visitor.parameterNamesList) {
        if (mpn.names.size() > 0) {
          // log.debug("{} {}", javaName, mpn.className);
          String pkg = ClassNameUtils.getPackage(fqcn);
          String dirPath = pkg.replace(".", "/");
          String fileName = mpn.className.substring(pkg.length() + 1).replace(".", "$") + ".param";

          File outFile = new File("./resources/params/" + dirPath, fileName);
          boolean result = outFile.getParentFile().mkdirs();

          log.info("start {} size:{}", javaName, mpn.names.size());
          serialize(mpn, outFile);
          MethodParameterNames names = deserialize(outFile);
          assert names.equals(mpn);
          log.info("end   {} size:{}", javaName, mpn.names.size());
        }
      }
    }
    GlobalCache.getInstance().shutdown();
  }
}
