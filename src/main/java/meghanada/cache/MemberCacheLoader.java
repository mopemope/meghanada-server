package meghanada.cache;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static meghanada.utils.FunctionUtils.wrapIO;
import static meghanada.utils.FunctionUtils.wrapIOConsumer;

import com.google.common.base.Stopwatch;
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
import meghanada.project.Project;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.asm.ASMReflector;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.reflect.asm.InheritanceInfo;
import meghanada.store.ProjectDatabaseHelper;
import meghanada.utils.ClassName;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class MemberCacheLoader extends CacheLoader<String, List<MemberDescriptor>>
    implements RemovalListener<String, List<MemberDescriptor>> {

  private static final Logger log = LogManager.getLogger(MemberCacheLoader.class);

  MemberCacheLoader() {}

  private static List<MemberDescriptor> getCachedMemberDescriptors(String fqcn) {
    Optional<List<MemberDescriptor>> result = ProjectDatabaseHelper.getMemberDescriptors(fqcn);
    return result.orElse(null);
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

  private void storeMembers(final String fqcn, final List<MemberDescriptor> list) {

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
                          index ->
                              ProjectDatabaseHelper.saveMemberDescriptors(
                                  index.getRawDeclaration(), list)));
              return true;
            });
  }

  @Override
  public List<MemberDescriptor> load(final String className) throws IOException {

    final ClassName cn = new ClassName(className);
    final String fqcn = cn.getName();

    final String projectRoot = System.getProperty(Project.PROJECT_ROOT_KEY);
    File classFile = MemberCacheLoader.getClassFile(fqcn);
    if (isNull(classFile)) {
      // try inner class
      classFile = MemberCacheLoader.getClassFile(ClassNameUtils.replaceInnerMark(fqcn));
      if (isNull(classFile)) {
        log.debug("Missing FQCN:{}'s file is null", fqcn);
        return Collections.emptyList();
      }
    }

    @SuppressWarnings("unchecked")
    final List<MemberDescriptor> cachedResult = MemberCacheLoader.getCachedMemberDescriptors(fqcn);
    if (nonNull(cachedResult)) {
      return cachedResult;
    }
    final String initName = ClassNameUtils.getSimpleName(fqcn);

    final Stopwatch stopwatch = Stopwatch.createStarted();

    final ASMReflector asmReflector = ASMReflector.getInstance();
    Map<String, ClassIndex> index = CachedASMReflector.getInstance().getGlobalClassIndex();

    final InheritanceInfo info = asmReflector.getReflectInfo(index, fqcn);
    final List<MemberDescriptor> result = asmReflector.reflectAll(info);

    final List<MemberDescriptor> members =
        result
            .stream()
            .filter(
                md -> {
                  if (md.matchType(CandidateUnit.MemberType.CONSTRUCTOR)) {
                    final String name = ClassNameUtils.getSimpleName(md.getName());
                    return name.equals(initName);
                  }
                  return true;
                })
            .collect(Collectors.toList());

    log.trace("load fqcn:{} elapsed:{}", fqcn, stopwatch.stop());

    storeMembers(fqcn, members);
    return members;
  }

  @Override
  public void onRemoval(final RemovalNotification<String, List<MemberDescriptor>> notification) {
    final RemovalCause cause = notification.getCause();
    if (cause.equals(RemovalCause.EXPIRED)
        || cause.equals(RemovalCause.SIZE)
        || cause.equals(RemovalCause.REPLACED)) {
      final String key = notification.getKey();
      final List<MemberDescriptor> value = notification.getValue();
      storeMembers(key, value);
    }
  }
}
