package meghanada.reflect.asm;

import static java.util.Objects.nonNull;
import static meghanada.utils.FunctionUtils.wrapIOConsumer;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
import jetbrains.exodus.entitystore.EntityId;
import meghanada.cache.GlobalCache;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.store.ProjectDatabaseHelper;
import meghanada.utils.ClassName;
import meghanada.utils.ClassNameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CachedASMReflector {

  private static final int CACHE_SIZE = 1024 * 16;
  private static final Logger log = LogManager.getLogger(CachedASMReflector.class);

  private static final Pattern PACKAGE_RE = Pattern.compile("\\.\\*");
  private static CachedASMReflector cachedASMReflector;

  private final Map<String, ClassIndex> globalClassIndex = new ConcurrentHashMap<>(CACHE_SIZE);

  private final Set<File> jars = new HashSet<>(64);
  private final Set<File> directories = new HashSet<>(8);
  private Map<String, String> standardClasses;

  private CachedASMReflector() {
    final GlobalCache globalCache = GlobalCache.getInstance();
    globalCache.setupMemberCache();
  }

  public static CachedASMReflector getInstance() {
    if (cachedASMReflector == null) {
      cachedASMReflector = new CachedASMReflector();
    }
    return cachedASMReflector;
  }

  private static boolean containsKeyword(String keyword, boolean partial, ClassIndex index) {

    String name = index.getName();
    if (ClassNameUtils.isAnonymousClass(name)) {
      return false;
    }
    if (partial) {
      String lowerKeyword = keyword.toLowerCase();
      String className = name.toLowerCase();
      return className.contains(lowerKeyword);
    } else {
      return name.equals(keyword)
          || name.endsWith('$' + keyword)
          || index.getDeclaration().equals(keyword);
    }
  }

  private static List<MemberDescriptor> replaceTypeParameters(
      String className, String classWithTP, List<MemberDescriptor> members) {

    int idx = classWithTP.indexOf('<');
    if (idx >= 0) {
      List<String> types = ClassNameUtils.parseTypeParameter(classWithTP);
      List<String> realTypes = ClassNameUtils.parseTypeParameter(className);

      for (MemberDescriptor md : members) {
        if (md.hasTypeParameters()) {
          md.clearTypeParameterMap();
          int realSize = realTypes.size();
          for (int i = 0; i < types.size(); i++) {
            String t = types.get(i);
            if (realSize > i) {
              String real = realTypes.get(i);
              md.putTypeParameter(t, real);
            }
          }
        }

        String declaringClass = ClassNameUtils.removeTypeParameter(md.getDeclaringClass());
        if (className.startsWith(declaringClass)) {
          md.setDeclaringClass(className);
        }
      }
    }
    return members;
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
                root -> {
                  String name = root.getName();
                  if (name.endsWith(".jar")
                      && !name.endsWith("SNAPSHOT.jar")
                      && ProjectDatabaseHelper.getLoadJar(root.getPath())) {
                    List<ClassIndex> indexes =
                        ProjectDatabaseHelper.getClassIndexes(root.getPath());

                    for (ClassIndex index : indexes) {
                      index.loaded = true;
                      String fqcn = index.getRawDeclaration();
                      this.globalClassIndex.put(fqcn, index);
                    }
                  } else {
                    final ASMReflector reflector = ASMReflector.getInstance();
                    reflector
                        .getClasses(root)
                        .entrySet()
                        .parallelStream()
                        .forEach(entry -> addClassIndex(entry.getKey(), entry.getValue()));
                    if (name.endsWith(".jar") && !name.endsWith("SNAPSHOT.jar")) {
                      ProjectDatabaseHelper.saveLoadJar(root.getPath());
                    }
                  }
                }));

    this.updateClassIndexFromDirectory();
    this.saveAllClassIndexes();
  }

  private void saveAllClassIndexes() {
    List<ClassIndex> jarIndexes = new ArrayList<>();
    List<ClassIndex> otherIndexes = new ArrayList<>();
    globalClassIndex
        .values()
        .forEach(
            index -> {
              if (!index.getFilePath().endsWith(".jar")) {
                otherIndexes.add(index);
              } else {
                if (!index.loaded) {
                  jarIndexes.add(index);
                }
              }
            });

    ProjectDatabaseHelper.saveClassIndexes(jarIndexes, false);
    ProjectDatabaseHelper.saveClassIndexes(otherIndexes, true);
  }

  private void updateClassIndexes() {
    List<ClassIndex> otherIndexes =
        globalClassIndex
            .values()
            .stream()
            .filter(classIndex -> !classIndex.getFilePath().endsWith(".jar"))
            .collect(Collectors.toList());

    ProjectDatabaseHelper.saveClassIndexes(otherIndexes, true);
  }

  private void addClassIndex(ClassIndex newIndex, File file) {

    final String fqcn = newIndex.getRawDeclaration();
    ClassIndex old = this.globalClassIndex.get(fqcn);

    if (nonNull(old)) {
      EntityId entityId = old.getEntityId();
      if (nonNull(entityId)) {
        // inheriting entityID
        newIndex.setEntityID(entityId);
      }
    }
    try {
      newIndex.setFilePath(file.getCanonicalPath());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    this.globalClassIndex.put(fqcn, newIndex);
  }

  public void createClassIndexes(final Collection<File> addJars) {
    addJars
        .parallelStream()
        .forEach(
            wrapIOConsumer(
                jar -> {
                  if (this.jars.contains(jar)) {
                    return;
                  }
                  final ASMReflector reflector = ASMReflector.getInstance();
                  reflector
                      .getClasses(jar)
                      .entrySet()
                      .parallelStream()
                      .forEach(entry -> addClassIndex(entry.getKey(), entry.getValue()));
                }));
    this.jars.addAll(addJars);
    this.saveAllClassIndexes();
  }

  public void updateClassIndexFromDirectory() {

    try (Stream<File> stream = this.directories.stream().parallel()) {
      stream.forEach(
          wrapIOConsumer(
              file -> {
                // TODO is loaded ?
                final ASMReflector reflector = ASMReflector.getInstance();
                reflector
                    .getClasses(file)
                    .entrySet()
                    .parallelStream()
                    .forEach(entry -> addClassIndex(entry.getKey(), entry.getValue()));
              }));
    }
    this.updateClassIndexes();
  }

  public boolean containsFQCN(String fqcn) {
    return this.globalClassIndex.containsKey(fqcn);
  }

  public File getClassFile(String fqcn) {
    ClassIndex classIndex = this.globalClassIndex.get(fqcn);
    if (nonNull(classIndex)) {
      String filePath = classIndex.getFilePath();
      if (nonNull(filePath)) {
        return new File(filePath);
      }
    }
    return null;
  }

  public Map<String, String> getPackageClasses(String packageName) {

    if (this.globalClassIndex.isEmpty()) {
      this.createClassIndexes();
    }
    if (packageName.endsWith(".*")) {
      packageName = PACKAGE_RE.matcher(packageName).replaceAll("");
    }

    final Map<String, String> result = new HashMap<>(64);

    for (final ClassIndex ci : this.globalClassIndex.values()) {
      if (ci.getPackage().equals(packageName)) {
        result.putIfAbsent(ci.getName(), ci.getRawDeclaration());
      }
    }

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

    final List<ClassIndex> result = new ArrayList<>(64);
    for (final ClassIndex c : this.globalClassIndex.values()) {
      if (anno && !c.isAnnotation()) {
        continue;
      }
      final String name = c.getName();
      final int score = StringUtils.getFuzzyDistance(name, keyword, Locale.ENGLISH);
      if (score >= length) {
        result.add(cloneClassIndex(c));
      }
    }
    return result;
  }

  public Stream<ClassIndex> fuzzySearchClassesStream(final String keyword, final boolean anno) {
    final int length = keyword.length() + 1;
    return this.globalClassIndex
        .values()
        .parallelStream()
        .filter(
            classIndex -> {
              if (anno && !classIndex.isAnnotation()) {
                return false;
              }
              final String name = classIndex.getName();
              final int score = StringUtils.getFuzzyDistance(name, keyword, Locale.ENGLISH);
              return score >= length;
            })
        .map(this::cloneClassIndex);
  }

  public List<ClassIndex> searchInnerClasses(final String parent) {
    return this.globalClassIndex
        .values()
        .parallelStream()
        .filter(classIndex -> classIndex.getReturnType().startsWith(parent + '$'))
        .map(this::cloneClassIndex)
        .collect(Collectors.toList());
  }

  public List<ClassIndex> searchInnerClasses(Set<String> parents) {
    List<ClassIndex> result = new ArrayList<>(16);
    for (ClassIndex ci : this.globalClassIndex.values()) {
      String returnType = ci.getReturnType();
      for (String parent : parents) {
        if (returnType.startsWith(parent + '$')) {
          result.add(cloneClassIndex(ci));
        }
      }
    }
    return result;
  }

  public List<ClassIndex> searchClasses(final String keyword) {
    return this.searchClasses(keyword, true, false);
  }

  public List<ClassIndex> searchAnnotations(final String keyword) {
    return this.searchClasses(keyword, true, true);
  }

  private ClassIndex cloneClassIndex(ClassIndex c) {
    ClassIndex ci = c.clone();
    if (ci.isInnerClass()) {
      String p = ci.getPackage();
      if (!p.isEmpty()) {
        String declaration = ci.getDisplayDeclaration();
        String clazzName = declaration.substring(p.length() + 1);
        ci.setName(clazzName);
      }
    }
    return ci;
  }

  public List<ClassIndex> searchClasses(String keyword, boolean partial, boolean anno) {
    List<ClassIndex> result = new ArrayList<>(64);
    for (ClassIndex c : this.globalClassIndex.values()) {
      if (keyword.isEmpty()) {
        result.add(cloneClassIndex(c));
      } else {
        if (!(anno && !c.isAnnotation())
            && CachedASMReflector.containsKeyword(keyword, partial, c)) {
          result.add(cloneClassIndex(c));
        }
      }
    }
    return result;
  }

  public Stream<ClassIndex> searchClassesStream(
      final String keyword, final boolean partial, final boolean anno) {
    return this.globalClassIndex
        .values()
        .parallelStream()
        .filter(
            classIndex -> {
              if (keyword.isEmpty()) {
                // match all
                return true;
              }
              return !(anno && !classIndex.isAnnotation())
                  && CachedASMReflector.containsKeyword(keyword, partial, classIndex);
            })
        .map(this::cloneClassIndex);
  }

  public Stream<ClassIndex> allClassStream() {
    return this.globalClassIndex.values().parallelStream();
  }

  public Stream<MemberDescriptor> allMethodStream() {
    return this.globalClassIndex
        .values()
        .parallelStream()
        .flatMap(
            cl -> {
              final String declaration = cl.getDeclaration();
              return reflect(declaration)
                  .parallelStream()
                  .filter(m -> m.matchType(CandidateUnit.MemberType.METHOD));
            });
  }

  public List<MemberDescriptor> reflect(final String className) {
    final ClassName cn = new ClassName(className);
    // check type parameter
    final String classWithoutTP = cn.getName();
    final GlobalCache globalCache = GlobalCache.getInstance();
    try {
      final List<MemberDescriptor> members = new ArrayList<>(16);
      List<MemberDescriptor> list = globalCache.getMemberDescriptors(classWithoutTP);
      for (final MemberDescriptor md : list) {
        members.add(md.clone());
      }
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

  public Collection<MemberDescriptor> reflectMethods(final String className, final String name) {
    final List<MemberDescriptor> result = new ArrayList<>(16);
    for (final MemberDescriptor m : this.reflect(className)) {
      if (m.getName().equals(name) && m.matchType(CandidateUnit.MemberType.METHOD)) {
        result.add(m);
      }
    }
    return result;
  }

  public Stream<MemberDescriptor> reflectConstructorStream(final String className) {
    return this.reflect(className)
        .stream()
        .filter(m -> m.matchType(CandidateUnit.MemberType.CONSTRUCTOR));
  }

  public Collection<MemberDescriptor> reflectConstructors(final String className) {
    final List<MemberDescriptor> result = new ArrayList<>(16);
    for (final MemberDescriptor m : this.reflect(className)) {
      if (m.matchType(CandidateUnit.MemberType.CONSTRUCTOR)) {
        result.add(m);
      }
    }
    return result;
  }

  public Stream<String> getSuperClassStream(final String className) {
    return this.getSuperClass(className).stream();
  }

  private Collection<String> getSuperClassInternal(final String className) {

    Set<String> result = new LinkedHashSet<>(4);
    String fqcn = ClassNameUtils.removeTypeParameter(className);
    this.containsClassIndex(fqcn)
        .ifPresent(
            ci -> {
              List<String> supers = ci.getSupers();
              if (supers.isEmpty()) {
                return;
              }

              if (supers.size() == 1 && supers.get(0).equals(ClassNameUtils.OBJECT_CLASS)) {
                return;
              }

              for (String superClazz : supers) {
                if (!superClazz.equals(ClassNameUtils.OBJECT_CLASS)) {
                  final String clazz = ClassNameUtils.removeTypeMark(superClazz);
                  result.add(clazz);
                  result.addAll(getSuperClassInternal(superClazz));
                }
              }
            });

    return result;
  }

  public Collection<String> getSuperClass(final String className) {
    Collection<String> result = getSuperClassInternal(className);
    result.add(ClassNameUtils.OBJECT_CLASS);
    return result;
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
}
