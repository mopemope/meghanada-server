package meghanada.reflect.asm;

import static java.util.Objects.nonNull;
import static meghanada.utils.FunctionUtils.wrapIOConsumer;
import static org.apache.lucene.document.Field.Index.NOT_ANALYZED;
import static org.apache.lucene.document.Field.Store.YES;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.exodus.entitystore.EntityId;
import meghanada.cache.GlobalCache;
import meghanada.index.IndexDatabase;
import meghanada.index.SearchIndexable;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.store.ProjectDatabaseHelper;
import meghanada.utils.ClassName;
import meghanada.utils.ClassNameUtils;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.objectweb.asm.ClassReader;

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
    GlobalCache globalCache = GlobalCache.getInstance();
    globalCache.setupMemberCache();
  }

  public static CachedASMReflector getInstance() {
    if (cachedASMReflector == null) {
      cachedASMReflector = new CachedASMReflector();
    }
    return cachedASMReflector;
  }

  public static boolean containsKeyword(
      final String keyword,
      final boolean partial,
      final CandidateUnit c,
      final boolean caseSensitive) {
    String name = c.getName();
    if (ClassNameUtils.isAnonymousClass(name)) {
      return false;
    }
    if (partial) {
      if (caseSensitive) {
        return name.contains(keyword);
      }
      String lowerKeyword = keyword.toLowerCase();
      String className = name.toLowerCase();
      return className.contains(lowerKeyword);
    } else {
      return name.equals(keyword)
          || name.endsWith('$' + keyword)
          || c.getDeclaration().equals(keyword);
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

  public static ClassIndex cloneClassIndex(ClassIndex c) {
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

  public void addClasspath(Collection<File> depends) {
    depends.forEach(this::addClasspath);
  }

  public void addClasspath(File dep) {
    if (!dep.exists() || dep.isDirectory()) {
      this.directories.add(dep);
    } else {
      if (!dep.canRead()) {
        return;
      }
      this.jars.add(dep);
    }
  }

  public Map<String, ClassIndex> getGlobalClassIndex() {
    return globalClassIndex;
  }

  public void createClassIndexes() {
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
                    ASMReflector reflector = ASMReflector.getInstance();
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
    List<ClassIndex> jarIndexes = new ArrayList<>(1024);
    List<ClassIndex> otherIndexes = new ArrayList<>(1024);
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

    String fqcn = newIndex.getRawDeclaration();
    ClassIndex old = this.globalClassIndex.get(fqcn);

    if (nonNull(old)) {
      EntityId entityId = old.getEntityId();
      if (nonNull(entityId)) {
        // inheriting entityID
        newIndex.setEntityID(entityId);
      }
    }
    ASMReflector.setFilePath(newIndex, file);

    this.globalClassIndex.put(fqcn, newIndex);
  }

  public void createClassIndexes(Collection<File> addJars) {
    addJars
        .parallelStream()
        .forEach(
            wrapIOConsumer(
                jar -> {
                  if (this.jars.contains(jar)) {
                    return;
                  }
                  ASMReflector reflector = ASMReflector.getInstance();
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
                ASMReflector reflector = ASMReflector.getInstance();
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

    Map<String, String> result = new HashMap<>(64);

    for (ClassIndex ci : this.globalClassIndex.values()) {
      if (ci.getPackage().equals(packageName)) {
        result.putIfAbsent(ci.getName(), ci.getRawDeclaration());
      }
    }

    return result;
  }

  public List<ClassIndex> searchInnerClasses(String parent) {
    return this.globalClassIndex
        .values()
        .parallelStream()
        .filter(classIndex -> classIndex.getReturnType().startsWith(parent + '$'))
        .map(CachedASMReflector::cloneClassIndex)
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

  public List<ClassIndex> searchClasses(final String keyword, final boolean includeAnnotation) {
    return this.globalClassIndex
        .values()
        .parallelStream()
        .filter(
            c -> {
              if (!includeAnnotation && c.isAnnotation()) {
                return false;
              }
              return containsKeyword(keyword, false, c, false);
            })
        .map(CachedASMReflector::cloneClassIndex)
        .collect(Collectors.toList());
  }

  public Stream<ClassIndex> allClassStream() {
    return this.globalClassIndex.values().parallelStream();
  }

  public List<MemberDescriptor> reflect(String className) {
    ClassName cn = new ClassName(className);
    // check type parameter
    String classWithoutTP = cn.getName();
    GlobalCache globalCache = GlobalCache.getInstance();
    try {
      List<MemberDescriptor> members = new ArrayList<>(16);
      List<MemberDescriptor> list = globalCache.getMemberDescriptors(classWithoutTP);
      for (MemberDescriptor md : list) {
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
      String classWithoutTP, String className, List<MemberDescriptor> members) {

    ClassIndex classIdx = this.globalClassIndex.get(classWithoutTP);
    if (classIdx != null) {
      return replaceTypeParameters(className, classIdx.getDisplayDeclaration(), members);
    }
    return members;
  }

  private Stream<MemberDescriptor> reflectStream(String className) {
    return this.reflect(className).stream();
  }

  public Stream<MemberDescriptor> reflectMethodStream(String className, String name) {
    return this.reflect(className)
        .stream()
        .filter(m -> m.getName().equals(name) && m.matchType(CandidateUnit.MemberType.METHOD));
  }

  public Collection<MemberDescriptor> reflectMethods(String className, String name) {
    List<MemberDescriptor> result = new ArrayList<>(16);
    for (MemberDescriptor m : this.reflect(className)) {
      if (m.getName().equals(name) && m.matchType(CandidateUnit.MemberType.METHOD)) {
        result.add(m);
      }
    }
    return result;
  }

  public Stream<MemberDescriptor> reflectConstructorStream(String className) {
    return this.reflect(className)
        .stream()
        .filter(m -> m.matchType(CandidateUnit.MemberType.CONSTRUCTOR));
  }

  public Collection<MemberDescriptor> reflectConstructors(String className) {
    List<MemberDescriptor> result = new ArrayList<>(16);
    for (MemberDescriptor m : this.reflect(className)) {
      if (m.matchType(CandidateUnit.MemberType.CONSTRUCTOR)) {
        result.add(m);
      }
    }
    return result;
  }

  public Stream<String> getSuperClassStream(String className) {
    return this.getSuperClass(className).stream();
  }

  private Collection<String> getSuperClassInternal(String className) {

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
                  String clazz = ClassNameUtils.removeTypeMark(superClazz);
                  result.add(clazz);
                  result.addAll(getSuperClassInternal(superClazz));
                }
              }
            });

    return result;
  }

  public Collection<String> getSuperClass(String className) {
    Collection<String> result = getSuperClassInternal(className);
    result.add(ClassNameUtils.OBJECT_CLASS);
    return result;
  }

  public Optional<ClassIndex> containsClassIndex(String className) {
    return Optional.ofNullable(this.globalClassIndex.get(className));
  }

  public Map<String, String> getStandardClasses() {
    if (this.standardClasses != null) {
      return this.standardClasses;
    }
    ImmutableMap<String, String> map =
        new ImmutableMap.Builder<String, String>()
            .putAll(this.getPackageClasses("java.lang"))
            .build();
    if (map.isEmpty()) {
      return map;
    }
    this.standardClasses = map;
    return this.standardClasses;
  }

  public void scanAllStaticMembers() {
    IndexDatabase database = IndexDatabase.getInstance();
    try (Stream<File> stream = this.directories.stream().parallel()) {
      stream.forEach(
          file -> {
            ConcurrentLinkedDeque<MemberDescriptor> deque = new ConcurrentLinkedDeque<>();
            try {
              scanMembers(file, deque);
              MemberIndex mi = new MemberIndex(file.getCanonicalPath(), deque);
              database.requestIndex(mi, event -> {});
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          });
    }

    this.jars
        .parallelStream()
        .forEach(
            file -> {
              try {
                final String path = file.getCanonicalPath();
                boolean b = ProjectDatabaseHelper.isIndexedFile(path);
                if (b) {
                  return;
                }
                ConcurrentLinkedDeque<MemberDescriptor> deque = new ConcurrentLinkedDeque<>();
                scanMembers(file, deque);
                final MemberIndex mi = new MemberIndex(file.getCanonicalPath(), deque);
                database.requestIndex(
                    mi,
                    event -> {
                      // store
                      String fileName = file.getName();
                      if (fileName.endsWith(".jar")
                          && !ProjectDatabaseHelper.saveIndexedFile(path)) {
                        log.warn("failed save index. {}", path);
                      }
                    });
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
  }

  private void scanMembers(final File root, final ConcurrentLinkedDeque<MemberDescriptor> deque)
      throws IOException {
    final ASMReflector reflector = ASMReflector.getInstance();
    reflector.scanClasses(
        root,
        (file, name, in) -> {
          ClassReader read = new ClassReader(in);
          String className = name;
          if (root.isDirectory()) {
            // path to package
            Set<File> roots = Collections.singleton(root);
            Optional<String> s = FileUtils.convertPathToClass(roots, file);
            if (s.isPresent()) {
              className = s.get();
            }
          }
          ClassAnalyzeVisitor visitor = new ClassAnalyzeVisitor(className, name, false, false);
          read.accept(visitor, 0);
          List<MemberDescriptor> members =
              visitor
                  .getMembers()
                  .stream()
                  .filter(m -> m.isPublic() && m.isStatic())
                  .collect(Collectors.toList());
          if (!members.isEmpty()) {
            deque.addAll(members);
          }
        });
  }

  public void scan(File root, Consumer<String> c) {
    ASMReflector reflector = ASMReflector.getInstance();
    try {
      reflector.scanClasses(root, (f, name, in) -> c.accept(name));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public Set<File> getJars() {
    return jars;
  }

  private static class MemberIndex implements SearchIndexable {
    private final String path;
    private final Collection<MemberDescriptor> members;

    MemberIndex(String path, Collection<MemberDescriptor> members) {
      this.path = path;
      this.members = members;
    }

    @Override
    public String getIndexGroupId() {
      return this.path;
    }

    @Override
    public List<Document> getDocumentIndices() {
      return this.members
          .parallelStream()
          .map(
              desc -> {
                Document doc = desc.toDocument();
                doc.add(new Field(SearchIndexable.GROUP_ID, getIndexGroupId(), YES, NOT_ANALYZED));
                return doc;
              })
          .collect(Collectors.toList());
    }
  }
}
