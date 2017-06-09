package meghanada.store;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.annotation.Nullable;
import meghanada.analyze.LineRange;
import meghanada.analyze.Position;
import meghanada.analyze.Range;
import meghanada.analyze.Scope;
import meghanada.analyze.Source;
import meghanada.analyze.Variable;
import meghanada.project.Project;
import meghanada.project.ProjectDependency;
import meghanada.project.gradle.GradleProject;
import meghanada.project.maven.MavenProject;
import meghanada.project.meghanada.MeghanadaProject;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.MethodParameter;
import meghanada.reflect.names.MethodParameterNames;
import meghanada.reflect.names.ParameterName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nustaq.serialization.FSTConfiguration;
import org.nustaq.serialization.FSTObjectInput;
import org.nustaq.serialization.FSTObjectOutput;

public class Serializer {

  private static final Logger log = LogManager.getLogger(Serializer.class);
  private static FSTConfiguration fst;

  private Serializer() {}

  public static FSTConfiguration getFST() {
    if (fst != null) {
      return fst;
    }
    FSTConfiguration conf = FSTConfiguration.createDefaultConfiguration();
    conf.registerClass(
        Project.class,
        ProjectDependency.class,
        GradleProject.class,
        MavenProject.class,
        MeghanadaProject.class,
        ParameterName.class,
        MethodParameterNames.class,
        Scope.class,
        LineRange.class,
        Position.class,
        Variable.class,
        Range.class,
        Source.class,
        MethodParameter.class,
        ClassIndex.class,
        MemberDescriptor.class);
    fst = conf;
    return fst;
  }

  public static <T> T readObject(InputStream input, Class<T> clazz) throws Exception {
    FSTObjectInput in = getFST().getObjectInput(input);
    Object obj = in.readObject(clazz);
    input.close();
    if (obj == null) {
      return null;
    }
    return clazz.cast(obj);
  }

  public static void writeObject(OutputStream output, Object obj) throws IOException {
    FSTObjectOutput out = getFST().getObjectOutput(output);
    out.writeObject(obj);
    out.flush();
  }

  public static void writeObjectToFile(final File file, final Object obj) {

    final File parentFile = file.getParentFile();

    if (!parentFile.exists() && !parentFile.mkdirs()) {
      log.warn("{} mkdirs fail", parentFile);
    }

    try (FileOutputStream out = new FileOutputStream(file)) {
      writeObject(out, obj);
    } catch (Exception e) {
      log.catching(e);
      if (!file.delete()) {
        log.warn("{} delete fail", file);
      }
    }
  }

  @Nullable
  public static <T> T readObjectFromFile(final File file, final Class<T> clazz) {
    if (!file.exists()) {
      log.warn("file not exists:{}", file);
      return null;
    }

    try {
      try (FileInputStream in = new FileInputStream(file)) {
        return readObject(in, clazz);
      }
    } catch (Exception e) {
      log.catching(e);
      if (file.exists() && !file.delete()) {
        log.warn("{} delete fail", file);
      }
      return null;
    }
  }

  public static byte[] asByte(Object obj) {
    FSTConfiguration fst = getFST();
    return fst.asByteArray(obj);
  }
}
