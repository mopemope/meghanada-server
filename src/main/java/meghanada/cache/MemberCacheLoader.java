package meghanada.cache;

import static meghanada.utils.FunctionUtils.wrapIO;
import static meghanada.utils.FunctionUtils.wrapIOConsumer;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.asm.ASMReflector;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.reflect.asm.InheritanceInfo;
import meghanada.utils.ClassName;
import meghanada.utils.ClassNameUtils;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class MemberCacheLoader extends CacheLoader<String, List<MemberDescriptor>>
    implements RemovalListener<String, List<MemberDescriptor>> {

  private static final Logger log = LogManager.getLogger(MemberCacheLoader.class);
  private final Map<String, File> classFileMap;
  private final Map<ClassIndex, File> reflectIndex;

  MemberCacheLoader(Map<String, File> classFileMap, Map<ClassIndex, File> reflectIndex) {
    this.classFileMap = classFileMap;
    this.reflectIndex = reflectIndex;
  }

  private static void writeFileCache(final String fqcn, final List<MemberDescriptor> list) {
    final CachedASMReflector reflector = CachedASMReflector.getInstance();
    reflector
        .containsClassIndex(fqcn)
        .map(
            wrapIO(
                classIndex -> {
                  CachedASMReflector.writeCache(classIndex, list);
                  return true;
                }))
        .orElseGet(
            () -> {
              final String innerFQCN = ClassNameUtils.replaceInnerMark(fqcn);
              reflector
                  .containsClassIndex(innerFQCN)
                  .ifPresent(
                      wrapIOConsumer(
                          classIndex -> CachedASMReflector.writeCache(classIndex, list)));
              return true;
            });
  }

  @SuppressWarnings("unchecked")
  private static List<MemberDescriptor> loadFromCache(final File cacheFile) {
    if (cacheFile.exists()) {
      return GlobalCache.getInstance().readCacheFromFile(cacheFile, ArrayList.class);
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> readCacheChecksum(final File inFile) {
    return GlobalCache.getInstance().readCacheFromFile(inFile, HashMap.class);
  }

  private static List<MemberDescriptor> getCachedMemberDescriptors(
      final String fqcn, final File cacheFilePath, final File file) throws IOException {
    if (file.exists()) {
      final String fileName = file.getName();
      if (file.isFile() && fileName.endsWith(".class")) {
        final String md5sum = FileUtils.getChecksum(file);
        final String filePath = file.getCanonicalPath();
        final List<MemberDescriptor> cachedResult =
            getCachedMemberDescriptors(cacheFilePath, md5sum, filePath);
        if (cachedResult != null) {
          return cachedResult;
        }
      } else if (file.isFile() && fileName.endsWith(".jar") && !fileName.contains("SNAPSHOT")) {
        final List<MemberDescriptor> cachedResult = MemberCacheLoader.loadFromCache(cacheFilePath);
        if (cachedResult != null) {
          return cachedResult;
        }
      } else if (file.isFile() && fileName.endsWith(".jar") && fileName.contains("SNAPSHOT")) {
        // skip
        return null;
      } else {
        // Dir
        final File classFile =
            new File(file, ClassNameUtils.replace(fqcn, ".", File.separator) + ".class");
        if (classFile.exists()) {
          final String md5sum = FileUtils.getChecksum(classFile);
          final String classFilePath = classFile.getCanonicalPath();
          return getCachedMemberDescriptors(cacheFilePath, md5sum, classFilePath);
        } else {
          log.warn("not exists:{}", classFile);
        }
      }
    }
    return null;
  }

  private static List<MemberDescriptor> getCachedMemberDescriptors(
      final File cacheFilePath, final String md5sum, final String filePath) throws IOException {
    final String projectRoot = System.getProperty(Project.PROJECT_ROOT_KEY);
    final File checksumMapFile =
        FileUtils.getProjectDataFile(new File(projectRoot), GlobalCache.COMPILE_CHECKSUM_DATA);
    Map<String, String> checksumMap;
    if (checksumMapFile.exists()) {
      checksumMap = new ConcurrentHashMap<>(MemberCacheLoader.readCacheChecksum(checksumMapFile));
    } else {
      checksumMap = new ConcurrentHashMap<>(64);
    }

    if (checksumMap.containsKey(filePath)) {
      if (checksumMap.get(filePath).equals(md5sum)) {
        // not modified
        final List<MemberDescriptor> cachedResult = MemberCacheLoader.loadFromCache(cacheFilePath);
        if (cachedResult != null) {
          return cachedResult;
        }
      } else {
        checksumMap.put(filePath, md5sum);
      }
    } else {
      checksumMap.put(filePath, md5sum);
    }
    return null;
  }

  @Override
  public List<MemberDescriptor> load(final String className) throws IOException {

    final ClassName cn = new ClassName(className);
    final String fqcn = cn.getName();
    final Config config = Config.load();
    final String dir = config.getProjectSettingDir();
    final File root = new File(dir);
    final String path = FileUtils.toHashedPath(fqcn, GlobalCache.CACHE_EXT);
    final String out = Joiner.on(File.separator).join(GlobalCache.MEMBER_CACHE_DIR, path);
    final File cacheFilePath = new File(root, out);

    final String projectRoot = System.getProperty(Project.PROJECT_ROOT_KEY);
    final File checksumMapFile =
        FileUtils.getProjectDataFile(new File(projectRoot), GlobalCache.COMPILE_CHECKSUM_DATA);

    Map<String, String> checksumMap;
    if (checksumMapFile.exists()) {
      checksumMap = new ConcurrentHashMap<>(MemberCacheLoader.readCacheChecksum(checksumMapFile));
    } else {
      checksumMap = new ConcurrentHashMap<>(64);
    }

    File classFile = this.classFileMap.get(fqcn);
    if (classFile == null) {
      // try inner class
      classFile = this.classFileMap.get(ClassNameUtils.replaceInnerMark(fqcn));
      if (classFile == null) {
        log.debug("Missing FQCN:{}'s file is null", fqcn);
        return Collections.emptyList();
      }
    }

    @SuppressWarnings("unchecked")
    final List<MemberDescriptor> cachedResult =
        MemberCacheLoader.getCachedMemberDescriptors(fqcn, cacheFilePath, classFile);
    if (cachedResult != null) {
      return cachedResult;
    }
    final String initName = ClassNameUtils.getSimpleName(fqcn);

    final Stopwatch stopwatch = Stopwatch.createStarted();
    final ASMReflector asmReflector = ASMReflector.getInstance();
    final InheritanceInfo info = asmReflector.getReflectInfo(reflectIndex, fqcn);
    final List<MemberDescriptor> list = asmReflector.reflectAll(info);

    final List<MemberDescriptor> memberDescriptors =
        list.stream()
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
    GlobalCache.getInstance().asyncWriteCache(checksumMapFile, checksumMap);
    return memberDescriptors;
  }

  @Override
  public void onRemoval(final RemovalNotification<String, List<MemberDescriptor>> notification) {
    final RemovalCause cause = notification.getCause();
    if (cause.equals(RemovalCause.EXPIRED)
        || cause.equals(RemovalCause.SIZE)
        || cause.equals(RemovalCause.REPLACED)) {
      final String key = notification.getKey();
      final List<MemberDescriptor> value = notification.getValue();
      MemberCacheLoader.writeFileCache(key, value);
    }
  }
}
