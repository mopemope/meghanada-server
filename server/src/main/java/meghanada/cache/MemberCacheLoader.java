package meghanada.cache;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static meghanada.utils.FunctionUtils.wrapIO;
import static meghanada.utils.FunctionUtils.wrapIOConsumer;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import meghanada.config.Config;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.asm.ASMReflector;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.reflect.asm.InheritanceInfo;
import meghanada.store.ProjectDatabaseHelper;
import meghanada.telemetry.TelemetryUtils;
import meghanada.utils.ClassName;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class MemberCacheLoader extends CacheLoader<String, List<MemberDescriptor>>
    implements RemovalListener<String, List<MemberDescriptor>> {

  private static final Logger log = LogManager.getLogger(MemberCacheLoader.class);

  MemberCacheLoader() {}

  @SuppressWarnings("try")
  private static Optional<List<MemberDescriptor>> getCachedMemberDescriptors(String fqcn) {
    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("MemberCacheLoader.getCachedMemberDescriptors")) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("fqcn", fqcn).build("args"));
      return ProjectDatabaseHelper.getMemberDescriptors(fqcn);
    }
  }

  private static File getClassFile(String fqcn) {

    Map<String, ClassIndex> globalClassIndex =
        CachedASMReflector.getInstance().getGlobalClassIndex();
    ClassIndex classIndex = globalClassIndex.get(fqcn);
    if (nonNull(classIndex)) {
      String filePath = classIndex.getFilePath();
      if (nonNull(filePath)) {
        return new File(filePath);
      }
    }
    return null;
  }

  @SuppressWarnings("try")
  private static void storeMembers(final String fqcn, final List<MemberDescriptor> list) {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("MemberCacheLoader.storeMembers")) {

      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("fqcn", fqcn)
              .put("list.size", list.size())
              .build("args"));

      final CachedASMReflector reflector = CachedASMReflector.getInstance();
      reflector
          .containsClassIndex(fqcn)
          .map(
              wrapIO(
                  index ->
                      ProjectDatabaseHelper.saveMemberDescriptors(index.getRawDeclaration(), list)))
          .orElseGet(
              () -> {
                final String innerFQCN = ClassNameUtils.replaceInnerMark(fqcn);
                reflector
                    .containsClassIndex(innerFQCN)
                    .ifPresent(
                        wrapIOConsumer(
                            index -> {
                              boolean b =
                                  ProjectDatabaseHelper.saveMemberDescriptors(
                                      index.getRawDeclaration(), list);
                            }));
                return true;
              });
    }
  }

  @Override
  @SuppressWarnings("try")
  public List<MemberDescriptor> load(final String className) throws IOException {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("MemberCacheLoader.load")) {

      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("className", className).build("args"));

      final ClassName cn = new ClassName(className);
      final String fqcn = cn.getName();

      final String projectRoot = Config.getProjectRoot();
      File classFile = MemberCacheLoader.getClassFile(fqcn);
      if (isNull(classFile)) {
        // try inner class
        classFile = MemberCacheLoader.getClassFile(ClassNameUtils.replaceInnerMark(fqcn));
        if (isNull(classFile)) {
          log.debug("Missing FQCN:{}'s file is null", fqcn);
          return Collections.emptyList();
        }
      }

      String classFilePath = classFile.getPath();
      boolean isMyProject = classFilePath.startsWith(projectRoot);

      if (!isMyProject) {
        final List<MemberDescriptor> members = loadFromReflector(fqcn);
        if (!members.isEmpty()) {
          storeMembers(fqcn, members);
          return members;
        }
      }

      Optional<List<MemberDescriptor>> cachedResult =
          MemberCacheLoader.getCachedMemberDescriptors(fqcn);
      if (cachedResult.isPresent()) {
        return cachedResult.get();
      }

      final List<MemberDescriptor> members = loadFromReflector(fqcn);
      storeMembers(fqcn, members);
      return members;
    }
  }

  @SuppressWarnings("try")
  private static List<MemberDescriptor> loadFromReflector(String fqcn) {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("MemberCacheLoader.loadFromReflector")) {

      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("fqcn", fqcn).build("args"));

      final String initName = ClassNameUtils.getSimpleName(fqcn);
      final ASMReflector asmReflector = ASMReflector.getInstance();
      Map<String, ClassIndex> index = CachedASMReflector.getInstance().getGlobalClassIndex();

      final InheritanceInfo info = asmReflector.getReflectInfo(index, fqcn);
      final List<MemberDescriptor> result = asmReflector.reflectAll(info);

      return result.stream()
          .filter(
              md -> {
                if (md.matchType(CandidateUnit.MemberType.CONSTRUCTOR)) {
                  final String name = ClassNameUtils.getSimpleName(md.getName());
                  return name.equals(initName);
                }
                return true;
              })
          .collect(Collectors.toList());
    }
  }

  @Override
  public void onRemoval(final RemovalNotification<String, List<MemberDescriptor>> notification) {
    final RemovalCause cause = notification.getCause();
    if (cause.equals(RemovalCause.EXPLICIT)) {
      final String key = notification.getKey();
      boolean b = ProjectDatabaseHelper.deleteMemberDescriptors(key);
    }
  }
}
