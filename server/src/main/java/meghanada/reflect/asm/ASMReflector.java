package meghanada.reflect.asm;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static meghanada.utils.ClassNameUtils.replaceDescriptorsType;
import static meghanada.utils.FunctionUtils.wrapIO;
import static meghanada.utils.FunctionUtils.wrapIOConsumer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import meghanada.config.Config;
import meghanada.module.ModuleHelper;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.MethodDescriptor;
import meghanada.utils.ClassNameUtils;
import meghanada.utils.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

public class ASMReflector {

  private static final String PACKAGE_INFO = "package-info";
  private static final String[] filterPackage =
      new String[] {
        "sun.",
        "com.sun",
        "com.oracle",
        "oracle.jrockit",
        "jdk",
        "org.omg",
        "org.ietf.",
        "org.jcp.",
        "netscape",
        "org.jboss.forge.roaster._shade.org.eclipse.core.internal"
      };
  private static final Logger log = LogManager.getLogger(ASMReflector.class);
  private static ASMReflector asmReflector;
  private final Set<String> allowClass = new HashSet<>(16);
  private Map<String, List<MemberDescriptor>> innerCache = new ConcurrentHashMap<>(16);

  private ASMReflector() {
    Config.load().getAllowClass().forEach(this::addAllowClass);
  }

  public static ASMReflector getInstance() {
    if (isNull(asmReflector)) {
      asmReflector = new ASMReflector();
    }
    return asmReflector;
  }

  static String toPrimitive(char c) {
    switch (c) {
      case 'B':
        return "byte";
      case 'C':
        return "char";
      case 'D':
        return "double";
      case 'F':
        return "float";
      case 'I':
        return "int";
      case 'J':
        return "long";
      case 'S':
        return "short";
      case 'V':
        return "void";
      case 'Z':
        return "boolean";
      default:
        return Character.toString(c);
    }
  }

  static String toModifier(int access, boolean hasDefault) {
    StringBuilder sb = new StringBuilder(7);
    if ((Opcodes.ACC_PRIVATE & access) > 0) {
      sb.append("private ");
    }
    if ((Opcodes.ACC_PUBLIC & access) > 0) {
      sb.append("public ");
    }
    if ((Opcodes.ACC_PROTECTED & access) > 0) {
      sb.append("protected ");
    }
    if ((Opcodes.ACC_STATIC & access) > 0) {
      sb.append("static ");
    }
    if ((Opcodes.ACC_ABSTRACT & access) > 0) {
      sb.append("abstract ");
    }
    if ((Opcodes.ACC_FINAL & access) > 0) {
      sb.append("final ");
    }
    if ((Opcodes.ACC_INTERFACE & access) > 0) {
      sb.append("interface ");
    }
    if ((Opcodes.ACC_NATIVE & access) > 0) {
      sb.append("native ");
    }
    if ((Opcodes.ACC_STRICT & access) > 0) {
      sb.append("strict ");
    }
    if ((Opcodes.ACC_SYNCHRONIZED & access) > 0) {
      sb.append("synchronized ");
    }
    if (hasDefault) {
      sb.append("default ");
    }

    if (sb.length() > 0) {
      sb.deleteCharAt(sb.length() - 1);
    }
    return sb.toString();
  }

  private static void readClassIndex(
      Map<ClassIndex, File> indexes, InputStream in, File file, boolean allowSuper)
      throws IOException {

    ClassReader classReader = new ClassReader(in);
    String className = ClassNameUtils.replaceSlash(classReader.getClassName());

    boolean projectOutput = file.isDirectory();

    int access = classReader.getAccess();

    boolean isPublic = (Opcodes.ACC_PUBLIC & access) == Opcodes.ACC_PUBLIC;
    boolean isProtected = (Opcodes.ACC_PROTECTED & access) == Opcodes.ACC_PROTECTED;

    boolean isInterface = (Opcodes.ACC_INTERFACE & access) == Opcodes.ACC_INTERFACE;
    boolean isAnnotation = (Opcodes.ACC_ANNOTATION & access) == Opcodes.ACC_ANNOTATION;

    boolean isSuper = false;

    if (allowSuper) {
      isSuper = (Opcodes.ACC_SUPER & access) == Opcodes.ACC_SUPER;
    }
    if (projectOutput || (isPublic || isProtected || isSuper)) {
      ClassAnalyzeVisitor classAnalyzeVisitor = new ClassAnalyzeVisitor(className, true, false);
      classReader.accept(classAnalyzeVisitor, 0);
      ClassIndex classIndex = classAnalyzeVisitor.getClassIndex();
      classIndex.setInterface(isInterface);
      classIndex.setAnnotation(isAnnotation);
      indexes.put(classIndex, file);
    }
  }

  private static ClassAnalyzeVisitor readClassFromJar(
      ClassReader classReader, String nameWithoutTP, String nameWithTP) {
    ClassAnalyzeVisitor classAnalyzeVisitor =
        new ClassAnalyzeVisitor(nameWithoutTP, nameWithTP, false, false);
    classReader.accept(classAnalyzeVisitor, 0);
    return classAnalyzeVisitor;
  }

  private static List<String> replaceSuperClassTypeParameters(String name, ClassIndex classIndex) {

    List<String> strings = ClassNameUtils.parseTypeParameter(name);
    Iterator<String> iterator = strings.iterator();
    Iterator<String> tpIterator = classIndex.getTypeParameters().iterator();
    Map<String, String> replace = new HashMap<>(4);
    while (iterator.hasNext()) {
      String real = iterator.next();
      if (tpIterator.hasNext()) {
        String tp = tpIterator.next();
        if (real.contains(ClassNameUtils.CLASS_TYPE_VARIABLE_MARK)) {
          String removed = StringUtils.replace(real, ClassNameUtils.CLASS_TYPE_VARIABLE_MARK, "");

          if (!tp.equals(removed)) {
            replace.put(ClassNameUtils.CLASS_TYPE_VARIABLE_MARK + tp, real);
          }
        }
      }
    }
    List<String> supers = new ArrayList<>(classIndex.getSupers());
    if (!replace.isEmpty()) {
      supers =
          classIndex
              .getSupers()
              .stream()
              .map(s -> ClassNameUtils.replaceFromMap(s, replace))
              .collect(Collectors.toList());
    }
    return supers;
  }

  static void setFilePath(final ClassIndex index, final File f) {
    try {
      if (ModuleHelper.isJrtFsFile(f)) {
        index.setFilePath(f.getPath());
      } else {
        index.setFilePath(f.getCanonicalPath());
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static boolean isJar(File file) {
    return file.isFile() && file.getName().endsWith("jar");
  }

  static boolean isClass(File file) {
    return file.isFile() && file.getName().endsWith(".class");
  }

  private void addAllowClass(String clazz) {
    this.allowClass.add(clazz);
  }

  private boolean ignorePackage(String target) {
    if (target.endsWith(PACKAGE_INFO)) {
      return true;
    }
    for (String keyword : this.allowClass) {
      if (target.startsWith(keyword)) {
        return false;
      }
    }
    for (String p : ASMReflector.filterPackage) {
      if (target.startsWith(p)) {
        return true;
      }
    }
    return false;
  }

  Map<String, ClassIndex> getClassIndexes(File file) throws IOException {
    Map<ClassIndex, File> classes = this.getClasses(file);
    Map<String, ClassIndex> result = new HashMap<>(classes.size());

    classes.forEach(
        (index, f) -> {
          String fqcn = index.getRawDeclaration();
          setFilePath(index, f);
          result.put(fqcn, index);
        });

    return result;
  }

  Map<ClassIndex, File> getClasses(File file) throws IOException {

    Map<ClassIndex, File> indexes = new ConcurrentHashMap<>(32);
    if (ModuleHelper.isJrtFsFile(file)) {
      ModuleHelper.walkModule(
          path ->
              ModuleHelper.pathToClassData(path)
                  .ifPresent(
                      cd -> {
                        String className = cd.getClassName();
                        String moduleName = cd.getModuleName();
                        if (this.ignorePackage(className)) {
                          return;
                        }
                        try (InputStream in = cd.getInputStream()) {
                          ASMReflector.readClassIndex(indexes, in, file, false);
                        } catch (IOException e) {
                          throw new UncheckedIOException(e);
                        }
                      }));
    } else if (isJar(file)) {
      try (JarFile jarFile = new JarFile(file);
          Stream<JarEntry> jarStream = jarFile.stream().parallel();
          Stream<JarEntry> stream =
              jarStream
                  .filter(jarEntry -> jarEntry.getName().endsWith(".class"))
                  .collect(Collectors.toList())
                  .parallelStream()) {

        stream.forEach(
            wrapIOConsumer(
                jarEntry -> {
                  String entryName = jarEntry.getName();
                  if (!entryName.endsWith(".class")) {
                    return;
                  }
                  String className =
                      ClassNameUtils.replaceSlash(entryName.substring(0, entryName.length() - 6));
                  if (this.ignorePackage(className)) {
                    return;
                  }
                  try (InputStream in = jarFile.getInputStream(jarEntry)) {
                    ASMReflector.readClassIndex(indexes, in, file, false);
                  }
                }));
      }

    } else if (isClass(file)) {
      String entryName = file.getName();
      if (!entryName.endsWith(".class")) {
        return indexes;
      }
      String className =
          ClassNameUtils.replaceSlash(entryName.substring(0, entryName.length() - 6));
      if (this.ignorePackage(className)) {
        return indexes;
      }
      try (InputStream in = new FileInputStream(file)) {
        ASMReflector.readClassIndex(indexes, in, file, true);
      }

    } else if (file.isDirectory()) {
      try (Stream<Path> pathStream = Files.walk(file.toPath());
          Stream<File> stream =
              pathStream
                  .map(Path::toFile)
                  .filter(f -> f.isFile() && f.getName().endsWith(".class"))
                  .collect(Collectors.toList())
                  .stream()) {

        stream.forEach(
            wrapIOConsumer(
                classFile -> {
                  String entryName = classFile.getName();
                  if (!entryName.endsWith(".class")) {
                    return;
                  }
                  String className =
                      ClassNameUtils.replaceSlash(entryName.substring(0, entryName.length() - 6));
                  if (this.ignorePackage(className)) {
                    return;
                  }
                  try (InputStream in = new FileInputStream(classFile)) {
                    ASMReflector.readClassIndex(indexes, in, file, true);
                  }
                }));
      }
    }
    return indexes;
  }

  public List<MemberDescriptor> reflectAll(InheritanceInfo info) {

    Map<String, List<MemberDescriptor>> collect =
        info.classFileMap
            .entrySet()
            .parallelStream()
            .map(
                wrapIO(
                    entry ->
                        this.reflectAll(
                            entry.getKey(), info.targetClass, new ArrayList<>(entry.getValue()))))
            .flatMap(Collection::stream)
            .collect(
                Collectors.groupingBy(
                    md -> ClassNameUtils.removeTypeParameter(md.getDeclaringClass()),
                    Collectors.toList()));

    Map<String, MemberDescriptor> result = new HashMap<>(64);
    Map<String, List<String>> paramMemo = new HashMap<>(64);
    info.inherit.forEach(
        clazz -> {
          String clazzKey = ClassNameUtils.removeTypeParameter(clazz);
          List<MemberDescriptor> list = collect.get(clazzKey);
          if (nonNull(list)) {
            list.forEach(
                desc -> {
                  if (desc.matchType(CandidateUnit.MemberType.METHOD)) {
                    MethodDescriptor mDesc = (MethodDescriptor) desc;
                    String name = mDesc.getName();
                    List<String> parameters = mDesc.getParameters();
                    String pKey = name + "#" + parameters.size();
                    List<String> cached = paramMemo.get(pKey);
                    String nameKey = name + "::" + parameters.toString();
                    if (isNull(cached)) {
                      result.put(nameKey, mDesc);
                      paramMemo.put(pKey, parameters);
                    } else {
                      // TODO varargs ?
                      boolean b = ClassNameUtils.compareArgumentType(cached, parameters, false);
                      //                      boolean b =
                      //                          ClassNameUtils.compareArgumentType(cached,
                      // parameters, mDesc.hasVarargs);
                      if (!b) {
                        result.put(nameKey, desc);
                      }
                    }
                  } else if (desc.matchType(CandidateUnit.MemberType.CONSTRUCTOR)) {
                    if (desc.getDeclaringClass().equals(info.targetClass)) {
                      String declaration = desc.getDeclaration();
                      result.putIfAbsent(declaration, desc);
                    }
                  } else {
                    result.putIfAbsent(desc.getName(), desc);
                  }
                });
          }
        });
    return new ArrayList<>(result.values());
  }

  private List<MemberDescriptor> reflectAll(File file, String topClass, List<String> classes)
      throws IOException {
    List<MemberDescriptor> results = new ArrayList<>(64);
    List<String> targetClasses = loadFromInnerCache(topClass, classes, results);
    if (targetClasses.isEmpty()) {
      return results;
    }
    if (ModuleHelper.isJrtFsFile(file)) {
      ModuleHelper.walkModule(
          path ->
              ModuleHelper.pathToClassData(path)
                  .ifPresent(
                      cd -> {
                        String className = cd.getClassName();
                        if (this.ignorePackage(className)) {
                          return;
                        }

                        Iterator<String> classIterator = targetClasses.iterator();
                        while (classIterator.hasNext()) {
                          String nameWithTP = classIterator.next();
                          if (nonNull(nameWithTP)) {
                            boolean isSuper = !topClass.equals(nameWithTP);
                            String nameWithoutTP = ClassNameUtils.removeTypeParameter(nameWithTP);

                            if (className.equals(nameWithoutTP)) {

                              List<MemberDescriptor> members =
                                  this.cachedMember(
                                      nameWithTP,
                                      () -> {
                                        try (InputStream in = cd.getInputStream()) {
                                          ClassReader classReader = new ClassReader(in);
                                          return getMemberFromJar(
                                              file, classReader, nameWithoutTP, nameWithTP);
                                        } catch (IOException e) {
                                          throw new UncheckedIOException(e);
                                        }
                                      });

                              if (isSuper) {
                                replaceDescriptorsType(nameWithTP, members);
                              }
                              results.addAll(members);
                              classIterator.remove();
                              break;
                            }

                            String innerClassName = ClassNameUtils.replaceInnerMark(className);
                            if (innerClassName.equals(nameWithoutTP)) {
                              List<MemberDescriptor> members =
                                  this.cachedMember(
                                      nameWithTP,
                                      () -> {
                                        try (InputStream in = cd.getInputStream()) {
                                          ClassReader classReader = new ClassReader(in);
                                          return this.getMemberFromJar(
                                              file, classReader, innerClassName, nameWithTP);
                                        } catch (IOException e) {
                                          throw new UncheckedIOException(e);
                                        }
                                      });
                              results.addAll(members);
                              classIterator.remove();
                              break;
                            }
                          }
                        }
                      }));
      return results;

    } else if (isJar(file)) {
      try (JarFile jarFile = new JarFile(file)) {
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
          if (targetClasses.isEmpty()) {
            break;
          }
          JarEntry jarEntry = entries.nextElement();
          String entryName = jarEntry.getName();
          if (!entryName.endsWith(".class")) {
            continue;
          }
          String className =
              ClassNameUtils.replaceSlash(entryName.substring(0, entryName.length() - 6));
          if (this.ignorePackage(className)) {
            continue;
          }
          Iterator<String> classIterator = targetClasses.iterator();

          while (classIterator.hasNext()) {
            String nameWithTP = classIterator.next();
            if (nonNull(nameWithTP)) {
              boolean isSuper = !topClass.equals(nameWithTP);
              String nameWithoutTP = ClassNameUtils.removeTypeParameter(nameWithTP);

              if (className.equals(nameWithoutTP)) {
                {
                  List<MemberDescriptor> members =
                      this.cachedMember(
                          nameWithTP,
                          () -> {
                            try (InputStream in = jarFile.getInputStream(jarEntry)) {
                              ClassReader classReader = new ClassReader(in);
                              return this.getMemberFromJar(
                                  file, classReader, nameWithoutTP, nameWithTP);
                            } catch (IOException e) {
                              throw new UncheckedIOException(e);
                            }
                          });
                  if (isSuper) {
                    replaceDescriptorsType(nameWithTP, members);
                  }
                  results.addAll(members);
                  classIterator.remove();
                }
                break;
              }

              String innerClassName = ClassNameUtils.replaceInnerMark(className);
              if (innerClassName.equals(nameWithoutTP)) {
                {
                  List<MemberDescriptor> members =
                      this.cachedMember(
                          nameWithTP,
                          () -> {
                            try (InputStream in = jarFile.getInputStream(jarEntry)) {
                              ClassReader classReader = new ClassReader(in);
                              return this.getMemberFromJar(
                                  file, classReader, innerClassName, nameWithTP);
                            } catch (IOException e) {
                              throw new UncheckedIOException(e);
                            }
                          });
                  if (isSuper) {
                    replaceDescriptorsType(nameWithTP, members);
                  }
                  results.addAll(members);
                  classIterator.remove();
                }
                break;
              }
            }
          }
        }
        return results;
      }
    } else if (isClass(file)) {

      for (String nameWithTP : targetClasses) {
        boolean isSuper = !topClass.equals(nameWithTP);
        String fqcn = ClassNameUtils.removeTypeParameter(nameWithTP);
        List<MemberDescriptor> members = getMembersFromClassFile(file, file, fqcn, false);
        if (nonNull(members)) {
          // 1 file
          if (isSuper) {
            replaceDescriptorsType(nameWithTP, members);
          }
          return members;
        }
      }
      return Collections.emptyList();

    } else if (file.isDirectory()) {
      try (Stream<Path> pathStream = Files.walk(file.toPath());
          Stream<File> stream =
              pathStream
                  .map(Path::toFile)
                  .filter(f -> f.isFile() && f.getName().endsWith(".class"))
                  .collect(Collectors.toList())
                  .stream()) {

        return stream
            .map(
                wrapIO(
                    f -> {
                      String rootPath = file.getCanonicalPath();
                      String path = f.getCanonicalPath();
                      String className =
                          ClassNameUtils.replaceSlash(
                              path.substring(rootPath.length() + 1, path.length() - 6));

                      Iterator<String> stringIterator = targetClasses.iterator();

                      while (stringIterator.hasNext()) {
                        String nameWithTP = stringIterator.next();
                        boolean isSuper = !topClass.equals(nameWithTP);
                        String fqcn = ClassNameUtils.removeTypeParameter(nameWithTP);

                        if (!className.equals(fqcn)) {
                          continue;
                        }

                        List<MemberDescriptor> members =
                            getMembersFromClassFile(file, f, fqcn, false);
                        if (nonNull(members)) {

                          if (isSuper) {
                            replaceDescriptorsType(nameWithTP, members);
                          }
                          // found
                          stringIterator.remove();
                          return members;
                        }
                      }
                      return Collections.<MemberDescriptor>emptyList();
                    }))
            .filter(memberDescriptors -> nonNull(memberDescriptors) && memberDescriptors.size() > 0)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
      }
    }
    return Collections.emptyList();
  }

  private List<MemberDescriptor> reflect(File file, String name) throws IOException {
    String nameWithoutTP = ClassNameUtils.removeTypeParameter(name);
    if (isJar(file)) {
      try (JarFile jarFile = new JarFile(file);
          Stream<JarEntry> jarStream = jarFile.stream();
          Stream<JarEntry> stream =
              jarStream
                  .filter(jarEntry -> jarEntry.getName().endsWith(".class"))
                  .collect(Collectors.toList())
                  .stream()) {

        return stream
            .map(
                wrapIO(
                    jarEntry -> {
                      String entryName = jarEntry.getName();
                      if (!entryName.endsWith(".class")) {
                        return new ArrayList<MemberDescriptor>(0);
                      }
                      String className =
                          ClassNameUtils.replaceSlash(
                              entryName.substring(0, entryName.length() - 6));
                      if (this.ignorePackage(className)) {
                        return new ArrayList<MemberDescriptor>(0);
                      }
                      if (className.equals(nameWithoutTP)) {
                        try (InputStream in = jarFile.getInputStream(jarEntry)) {
                          ClassReader classReader = new ClassReader(in);
                          return getMemberFromJar(file, classReader, nameWithoutTP, name);
                        }
                      }

                      // To bin name
                      className = ClassNameUtils.replaceInnerMark(className);
                      if (className.equals(nameWithoutTP)) {
                        try (InputStream in = jarFile.getInputStream(jarEntry)) {
                          ClassReader classReader = new ClassReader(in);
                          return getMemberFromJar(file, classReader, nameWithoutTP, name);
                        }
                      }

                      return new ArrayList<MemberDescriptor>(0);
                    }))
            .filter(list -> list.size() > 0)
            .findFirst()
            .orElse(Collections.emptyList());
      }
    } else if (isClass(file)) {
      List<MemberDescriptor> members = getMembersFromClassFile(file, file, nameWithoutTP);
      if (nonNull(members)) {
        return members;
      }
    } else if (file.isDirectory()) {
      try (Stream<Path> stream = Files.walk(file.toPath())) {
        return stream
            .map(Path::toFile)
            .filter(f -> f.isFile() && f.getName().endsWith(".class"))
            .map(wrapIO(f -> getMembersFromClassFile(file, f, nameWithoutTP)))
            .filter(descriptors -> nonNull(descriptors) && descriptors.size() > 0)
            .findFirst()
            .orElse(Collections.emptyList());
      }
    }
    return Collections.emptyList();
  }

  private List<MemberDescriptor> getMembersFromClassFile(File parent, File file, String fqcn)
      throws IOException {
    return getMembersFromClassFile(parent, file, fqcn, true);
  }

  private List<MemberDescriptor> getMembersFromClassFile(
      File parent, File file, String fqcn, boolean includeSuper) throws IOException {
    try (InputStream in = new FileInputStream(file)) {
      ClassReader classReader = new ClassReader(in);
      String className = ClassNameUtils.replaceSlash(classReader.getClassName());
      if (className.equals(fqcn)) {
        ClassAnalyzeVisitor cv = new ClassAnalyzeVisitor(className, className, false, true);
        classReader.accept(cv, 0);
        List<MemberDescriptor> members = cv.getMembers();

        if (includeSuper) {
          readSuperMembers(parent, cv, members);
        }

        return members;
      }
    }
    return null;
  }

  private void readSuperMembers(File parent, ClassAnalyzeVisitor cv, List<MemberDescriptor> units) {
    ClassIndex classIndex = cv.getClassIndex();
    List<List<MemberDescriptor>> lists =
        classIndex
            .getSupers()
            .stream()
            .parallel()
            .map(wrapIO(s -> reflect(parent, s)))
            .collect(Collectors.toList());
    lists.forEach(units::addAll);
  }

  private List<MemberDescriptor> getMemberFromJar(
      File file, ClassReader classReader, String nameWithoutTP, String nameWithTP) {
    return getMemberFromJar(file, classReader, nameWithoutTP, nameWithTP, false);
  }

  private List<MemberDescriptor> getMemberFromJar(
      File file,
      ClassReader classReader,
      String nameWithoutTP,
      String nameWithTP,
      boolean includeSuper) {
    ClassAnalyzeVisitor cv = readClassFromJar(classReader, nameWithoutTP, nameWithTP);
    List<MemberDescriptor> members = cv.getMembers();

    if (includeSuper) {
      this.readSuperMembers(file, cv, members);
    }
    return members;
  }

  public InheritanceInfo getReflectInfo(Map<String, ClassIndex> index, String fqcn) {
    InheritanceInfo info = new InheritanceInfo(fqcn);
    InheritanceInfo reflectInfo = this.searchReflectInfo(index, fqcn, info);
    reflectInfo.inherit = reflectInfo.inherit.stream().distinct().collect(Collectors.toList());
    return reflectInfo;
  }

  private InheritanceInfo searchReflectInfo(
      Map<String, ClassIndex> index, String name, InheritanceInfo info) {

    for (Map.Entry<String, ClassIndex> entry : index.entrySet()) {
      ClassIndex classIndex = entry.getValue();
      File file = new File(classIndex.getFilePath());

      String searchName = ClassNameUtils.removeTypeParameter(name);
      String target = classIndex.toString();
      if (target.equals(searchName)) {
        this.addInheritance(index, name, info, classIndex, file);
        break;
      }
      //
      Optional<String> opt = ClassNameUtils.toInnerClassName(name);
      if (opt.isPresent()) {
        String inner = opt.get();

        if (target.equals(inner)) {

          if (!info.classFileMap.containsKey(file)) {
            info.classFileMap.put(file, new ArrayList<>(8));
          }

          info.inherit.add(name);
          info.classFileMap.get(file).add(name);
          List<String> supers = ASMReflector.replaceSuperClassTypeParameters(name, classIndex);

          Collections.reverse(supers);

          supers.forEach(
              superClass -> {
                InheritanceInfo ignored = this.searchReflectInfo(index, superClass, info);
              });

          break;
        }
      }
    }
    return info;
  }

  private void addInheritance(
      Map<String, ClassIndex> index,
      String name,
      InheritanceInfo info,
      ClassIndex classIndex,
      File file) {
    // found
    if (!info.classFileMap.containsKey(file)) {
      info.classFileMap.put(file, new ArrayList<>(8));
    }
    info.inherit.add(name);
    info.classFileMap.get(file).add(name);

    List<String> supers = ASMReflector.replaceSuperClassTypeParameters(name, classIndex);

    Collections.reverse(supers);
    supers.forEach(
        superClass -> {
          InheritanceInfo ignore = this.searchReflectInfo(index, superClass, info);
        });
  }

  void scanClasses(File file, Scanner scanner) throws IOException {
    if (ModuleHelper.isJrtFsFile(file)) {
      ModuleHelper.walkModule(
          path ->
              ModuleHelper.pathToClassData(path)
                  .ifPresent(
                      cd -> {
                        String className = cd.getClassName();
                        if (this.ignorePackage(className)) {
                          return;
                        }
                        if (!className.endsWith("module-info")) {
                          try (InputStream in = cd.getInputStream()) {
                            scanner.scan(file, className, in);
                          } catch (IOException e) {
                            throw new UncheckedIOException(e);
                          }
                        }
                      }));
    } else if (isJar(file)) {
      try (JarFile jarFile = new JarFile(file);
          Stream<JarEntry> jarStream = jarFile.stream();
          Stream<JarEntry> stream =
              jarStream
                  .filter(jarEntry -> jarEntry.getName().endsWith(".class"))
                  .collect(Collectors.toList())
                  .stream()) {

        stream.forEach(
            jarEntry -> {
              String entryName = jarEntry.getName();
              if (!entryName.endsWith(".class")) {
                return;
              }
              String className =
                  ClassNameUtils.replaceSlash(entryName.substring(0, entryName.length() - 6));
              if (this.ignorePackage(className)) {
                return;
              }
              if (!className.endsWith("module-info")) {
                try (InputStream in = jarFile.getInputStream(jarEntry)) {
                  scanner.scan(file, className, in);
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              }
            });
      }

    } else if (isClass(file)) {
      String entryName = file.getName();
      if (!entryName.endsWith(".class")) {
        return;
      }
      String className =
          ClassNameUtils.replaceSlash(entryName.substring(0, entryName.length() - 6));
      if (this.ignorePackage(className)) {
        return;
      }
      try (InputStream in = new FileInputStream(file)) {
        scanner.scan(file, className, in);
      }

    } else if (file.isDirectory()) {
      try (Stream<Path> pathStream = Files.walk(file.toPath());
          Stream<File> stream =
              pathStream
                  .map(Path::toFile)
                  .filter(f -> f.isFile() && f.getName().endsWith(".class"))
                  .collect(Collectors.toList())
                  .stream()) {

        stream.forEach(
            classFile -> {
              String entryName = classFile.getName();
              if (!entryName.endsWith(".class")) {
                return;
              }
              String className =
                  ClassNameUtils.replaceSlash(entryName.substring(0, entryName.length() - 6));
              if (this.ignorePackage(className)) {
                return;
              }
              if (!className.endsWith("module-info")) {
                try (InputStream in = new FileInputStream(classFile)) {
                  scanner.scan(classFile, className, in);
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              }
            });
      }
    }
  }

  public List<String> loadFromInnerCache(
      String mainClass, List<String> targets, List<MemberDescriptor> results) {
    for (Iterator<String> it = targets.iterator(); it.hasNext(); ) {
      String nameWithTP = it.next();
      List<MemberDescriptor> members = this.innerCache.get(nameWithTP);
      if (nonNull(members)) {
        boolean isSuper = !mainClass.equals(nameWithTP);
        if (isSuper) {
          replaceDescriptorsType(nameWithTP, members);
        }
        results.addAll(members);
        it.remove();
      }
    }
    return targets;
  }

  public List<MemberDescriptor> cachedMember(
      String key, Supplier<List<MemberDescriptor>> supplier) {

    List<MemberDescriptor> list = innerCache.get(key);
    if (nonNull(list)) {
      return list.stream().map(MemberDescriptor::clone).collect(Collectors.toList());
    }
    List<MemberDescriptor> newVal = supplier.get();
    innerCache.put(key, newVal);
    return newVal;
  }

  public void clearInnerCache() {
    // shrink
    this.innerCache = new ConcurrentHashMap<>(16);
  }

  @FunctionalInterface
  public interface Scanner {
    void scan(File file, String name, InputStream in) throws IOException;
  }
}
