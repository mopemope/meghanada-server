package meghanada.reflect.asm;

import static meghanada.utils.FunctionUtils.wrapIOConsumer;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import meghanada.cache.GlobalCache;
import meghanada.config.Config;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.utils.ClassName;
import meghanada.utils.ClassNameUtils;
import meghanada.utils.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CachedASMReflector {

  private static final int CACHE_SIZE = 1024 * 16;
  private static final Logger log = LogManager.getLogger(CachedASMReflector.class);

  private static final Pattern PACKAGE_RE = Pattern.compile("\\.\\*");
  private static CachedASMReflector cachedASMReflector;

  private final Map<String, ClassIndex> globalClassIndex = new ConcurrentHashMap<>(CACHE_SIZE);

  // Key:FQCN Val:JarFile
  private final Map<String, File> classFileMap = new ConcurrentHashMap<>(CACHE_SIZE);

  private final Map<ClassIndex, File> reflectIndex = new ConcurrentHashMap<>(CACHE_SIZE);

  private final Set<File> jars = new HashSet<>(64);
  private final Set<File> directories = new HashSet<>(8);

  private Map<String, String> standardClasses;

  private CachedASMReflector() {
    final GlobalCache globalCache = GlobalCache.getInstance();
    globalCache.setupMemberCache(this.classFileMap, this.reflectIndex);
  }

  public static CachedASMReflector getInstance() {
    if (cachedASMReflector == null) {
      cachedASMReflector = new CachedASMReflector();
    }
    return cachedASMReflector;
  }

  private static boolean containsKeyword(
      final String keyword, final boolean partial, final ClassIndex index) {
    final String name = index.getName();
    if (ClassNameUtils.isAnonymousClass(name)) {
      return false;
    }
    if (partial) {
      final String lowerKeyword = keyword.toLowerCase();
      final String className = name.toLowerCase();
      return className.contains(lowerKeyword);
    } else {
      return name.equals(keyword)
          || name.endsWith('$' + keyword)
          || index.getDeclaration().equals(keyword);
    }
  }

  private static List<MemberDescriptor> replaceTypeParameters(
      final String className, final String classWithTP, final List<MemberDescriptor> members) {
    final int idx1 = classWithTP.indexOf('<');
    if (idx1 >= 0) {
      final List<String> types = ClassNameUtils.parseTypeParameter(classWithTP);
      final List<String> realTypes = ClassNameUtils.parseTypeParameter(className);
      // log.warn("className {} types {} realTypes {}", className, types, realTypes);
      for (final MemberDescriptor md : members) {
        if (md.hasTypeParameters()) {
          md.clearTypeParameterMap();
          int realSize = realTypes.size();
          for (int i = 0; i < types.size(); i++) {
            final String t = types.get(i);
            if (realSize > i) {
              final String real = realTypes.get(i);
              md.putTypeParameter(t, real);
              // log.debug("put t:{}, real:{}", t, real);
            }
          }
        }

        final String declaringClass = ClassNameUtils.removeTypeParameter(md.getDeclaringClass());
        if (className.startsWith(declaringClass)) {
          md.setDeclaringClass(className);
        }
      }
    }
    return members;
  }

  private static boolean existsClassCache(final String className) {
    final File outFile = getClassCacheFile(className);
    return outFile.exists();
  }

  private static File getClassCacheFile(final String className) {

    final Config config = Config.load();
    final String dir = config.getProjectSettingDir();
    final File root = new File(dir);
    final String path = FileUtils.toHashedPath(className, GlobalCache.CACHE_EXT);
    final String out1 = Joiner.on(File.separator).join(GlobalCache.CLASS_CACHE_DIR, path);
    return new File(root, out1);
  }

  public static void writeCache(final ClassIndex classIndex, final List<MemberDescriptor> members)
      throws FileNotFoundException {
    final Config config = Config.load();
    final String fqcn = classIndex.getRawDeclaration();
    final String dir = config.getProjectSettingDir();
    final File root = new File(dir);
    final String path = FileUtils.toHashedPath(fqcn, GlobalCache.CACHE_EXT);

    final String out1 = Joiner.on(File.separator).join(GlobalCache.CLASS_CACHE_DIR, path);
    final String out2 = Joiner.on(File.separator).join(GlobalCache.MEMBER_CACHE_DIR, path);
    final File file1 = new File(root, out1);
    final File file2 = new File(root, out2);

    if (!file1.getParentFile().exists() && !file1.getParentFile().mkdirs()) {
      log.warn("{} mkdirs fail", file1);
    }
    if (!file2.getParentFile().exists() && !file2.getParentFile().mkdirs()) {
      log.warn("{} mkdirs fail", file2);
    }

    // ClassIndex
    final GlobalCache globalCache = GlobalCache.getInstance();
    globalCache.asyncWriteCache(file1, classIndex);
    globalCache.asyncWriteCache(file2, members);
  }

  private static ClassIndex readClassIndexFromCache(final String fqcn) throws IOException {
    final File in = CachedASMReflector.getClassCacheFile(fqcn);
    if (in.exists()) {
      final GlobalCache globalCache = GlobalCache.getInstance();
      return globalCache.readCacheFromFile(in, ClassIndex.class);
    }
    return null;
  }

  public void addClasspath(final Collection<File> depends) {
    depends.forEach(this::addClasspath);
  }

  public void addClasspath(final File dep) {
    if (dep.isDirectory()) {
      this.directories.add(dep);
    } else {
      this.jars.add(dep);
    }
  }

  public Map<String, ClassIndex> getGlobalClassIndex() {
    return globalClassIndex;
  }

  public void createClassIndexes() {
    log.debug("start createClassIndexes");

    this.jars
        .parallelStream()
        .forEach(
            wrapIOConsumer(
                file -> {
                  if (file.getName().endsWith(".jar") && this.classFileMap.containsValue(file)) {
                    //skip cached jar
                    return;
                  }

                  final ASMReflector reflector = ASMReflector.getInstance();
                  reflector
                      .getClasses(file)
                      .entrySet()
                      .parallelStream()
                      .forEach(
                          classIndexFileEntry -> {
                            final ClassIndex classIndex1 = classIndexFileEntry.getKey();
                            final File file1 = classIndexFileEntry.getValue();
                            final String fqcn = classIndex1.getRawDeclaration();
                            this.globalClassIndex.put(fqcn, classIndex1);
                            this.classFileMap.put(fqcn, file1);
                            this.reflectIndex.put(classIndex1, file1);
                          });
                }));

    this.updateClassIndexFromDirectory();
  }

  public void createClassIndexes(final Collection<File> jars) {
    jars.parallelStream()
        .forEach(
            wrapIOConsumer(
                file -> {
                  if (file.getName().endsWith(".jar") && this.classFileMap.containsValue(file)) {
                    //skip cached jar
                    return;
                  }
                  if (this.jars.contains(file)) {
                    return;
                  }
                  final ASMReflector reflector = ASMReflector.getInstance();
                  reflector
                      .getClasses(file)
                      .entrySet()
                      .parallelStream()
                      .forEach(
                          classIndexFileEntry -> {
                            final ClassIndex classIndex1 = classIndexFileEntry.getKey();
                            final File file1 = classIndexFileEntry.getValue();
                            final String fqcn = classIndex1.getRawDeclaration();
                            this.globalClassIndex.put(fqcn, classIndex1);
                            this.classFileMap.put(fqcn, file1);
                            this.reflectIndex.put(classIndex1, file1);
                          });
                }));
    this.jars.addAll(jars);
  }

  public void updateClassIndexFromDirectory() {
    try (Stream<File> stream = this.directories.stream().parallel()) {
      stream.forEach(
          wrapIOConsumer(
              file -> {
                if (file.getName().endsWith(".jar") && this.classFileMap.containsValue(file)) {
                  //skip cached jar
                  return;
                }
                final ASMReflector reflector = ASMReflector.getInstance();
                reflector
                    .getClasses(file)
                    .entrySet()
                    .parallelStream()
                    .forEach(
                        classIndexFileEntry -> {
                          final ClassIndex classIndex1 = classIndexFileEntry.getKey();
                          final File file1 = classIndexFileEntry.getValue();
                          final String fqcn = classIndex1.getRawDeclaration();
                          this.globalClassIndex.put(fqcn, classIndex1);
                          this.classFileMap.put(fqcn, file1);
                          this.reflectIndex.put(classIndex1, file1);
                        });
              }));
    }
  }

  public boolean containsFQCN(String fqcn) {
    return this.classFileMap.containsKey(fqcn);
  }

  public File getClassFile(String fqcn) {
    return this.classFileMap.get(fqcn);
  }

  public Map<String, String> getPackageClasses(String packageName) {
    // log.debug("getPackageClasses packageName:{}", packageName);
    if (this.globalClassIndex.isEmpty()) {
      this.createClassIndexes();
    }
    if (packageName.endsWith(".*")) {
      packageName = PACKAGE_RE.matcher(packageName).replaceAll("");
    }

    final String pkg = packageName;
    final Map<String, String> result = new ConcurrentHashMap<>(64);
    this.globalClassIndex
        .values()
        .parallelStream()
        .filter(ci -> ci.getPackage().equals(pkg))
        .forEach(ci -> result.putIfAbsent(ci.getName(), ci.getRawDeclaration()));
    return result;
  }

  public List<ClassIndex> fuzzySearchClasses(final String keyword) {
    return this.fuzzySearchClasses(keyword, false);
  }

  public List<ClassIndex> fuzzySearchAnnotations(final String keyword) {
    return this.fuzzySearchClasses(keyword, true);
  }

  private List<ClassIndex> fuzzySearchClasses(final String keyword, final boolean anno) {
    final int length = keyword.length() + 1;
    return this.globalClassIndex
        .values()
        .stream()
        .filter(
            classIndex -> {
              if (anno && !classIndex.isAnnotation) {
                return false;
              }
              final String name = classIndex.getName();
              final int score = StringUtils.getFuzzyDistance(name, keyword, Locale.ENGLISH);
              return score >= length;
            })
        .map(ClassIndex::clone)
        .collect(Collectors.toList());
  }

  public List<ClassIndex> searchInnerClasses(final String parent) {
    return this.globalClassIndex
        .values()
        .parallelStream()
        .filter(classIndex -> classIndex.getReturnType().startsWith(parent + '$'))
        .map(ClassIndex::clone)
        .collect(Collectors.toList());
  }

  public List<ClassIndex> searchInnerClasses(final Set<String> parents) {
    return this.globalClassIndex
        .values()
        .parallelStream()
        .filter(
            classIndex -> {
              final String returnType = classIndex.getReturnType();
              for (final String parent : parents) {
                if (returnType.startsWith(parent + '$')) {
                  return true;
                }
              }
              return false;
            })
        .map(ClassIndex::clone)
        .collect(Collectors.toList());
  }

  public List<ClassIndex> searchClasses(final String keyword) {
    return this.searchClasses(keyword, true, false);
  }

  public List<ClassIndex> searchAnnotations(final String keyword) {
    return this.searchClasses(keyword, true, true);
  }

  public List<ClassIndex> searchClasses(
      final String keyword, final boolean partial, final boolean anno) {
    return this.globalClassIndex
        .values()
        .stream()
        .filter(
            classIndex ->
                !(anno && !classIndex.isAnnotation)
                    && CachedASMReflector.containsKeyword(keyword, partial, classIndex))
        .map(ClassIndex::clone)
        .collect(Collectors.toList());
  }

  public List<MemberDescriptor> reflect(final String className) {
    final ClassName cn = new ClassName(className);
    // check type parameter
    final String classWithoutTP = cn.getName();
    final GlobalCache globalCache = GlobalCache.getInstance();
    try {
      final List<MemberDescriptor> members =
          globalCache
              .getMemberDescriptors(classWithoutTP)
              .stream()
              .map(MemberDescriptor::clone)
              .collect(Collectors.toList());

      if (cn.hasTypeParameter()) {
        return this.replaceMembers(classWithoutTP, className, members);
      }
      return members;
    } catch (ExecutionException e) {
      throw new UncheckedExecutionException(e);
    }
  }

  private List<MemberDescriptor> replaceMembers(
      final String classWithoutTP, final String className, final List<MemberDescriptor> members) {

    final ClassIndex classIdx = this.globalClassIndex.get(classWithoutTP);
    if (classIdx != null) {
      return replaceTypeParameters(className, classIdx.getDisplayDeclaration(), members);
    }
    return members;
  }

  private Stream<MemberDescriptor> reflectStream(final String className) {
    return this.reflect(className).stream();
  }

  public Stream<MemberDescriptor> reflectMethodStream(final String className, final String name) {
    return this.reflect(className)
        .stream()
        .filter(m -> m.getName().equals(name) && m.matchType(CandidateUnit.MemberType.METHOD));
  }

  public Stream<MemberDescriptor> reflectConstructorStream(final String className) {
    return this.reflect(className)
        .stream()
        .filter(m -> m.matchType(CandidateUnit.MemberType.CONSTRUCTOR));
  }

  public Stream<String> getSuperClassStream(final String className) {
    return this.containsClassIndex(className)
        .map(classIndex -> classIndex.supers.stream())
        .orElse(new ArrayList<String>(0).stream());
  }

  public Optional<ClassIndex> containsClassIndex(final String className) {
    return Optional.ofNullable(this.globalClassIndex.get(className));
  }

  public Map<String, String> getStandardClasses() {
    if (this.standardClasses != null) {
      return this.standardClasses;
    }
    final ImmutableMap<String, String> map =
        new ImmutableMap.Builder<String, String>()
            .putAll(this.getPackageClasses("java.lang"))
            .build();
    if (map.isEmpty()) {
      return map;
    }
    this.standardClasses = map;
    return this.standardClasses;
  }

  public void resetClassFileMap() {
    this.classFileMap.clear();
  }
}
