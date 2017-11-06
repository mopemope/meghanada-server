package meghanada.reflect.asm;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import meghanada.config.Config;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.utils.ClassNameUtils;
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

  private ASMReflector() {
    Config.load().getAllowClass().forEach(this::addAllowClass);
  }

  public static ASMReflector getInstance() {
    if (isNull(asmReflector)) {
      asmReflector = new ASMReflector();
    }
    return asmReflector;
  }

  static String toPrimitive(final char c) {
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

  static String toModifier(final int access, final boolean hasDefault) {
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
      final Map<ClassIndex, File> indexes,
      final InputStream in,
      final File file,
      boolean allowSuper)
      throws IOException {

    final ClassReader classReader = new ClassReader(in);
    final String className = ClassNameUtils.replaceSlash(classReader.getClassName());

    final boolean projectOutput = file.isDirectory();

    final int access = classReader.getAccess();

    final boolean isPublic = (Opcodes.ACC_PUBLIC & access) == Opcodes.ACC_PUBLIC;
    final boolean isProtected = (Opcodes.ACC_PROTECTED & access) == Opcodes.ACC_PROTECTED;

    final boolean isInterface = (Opcodes.ACC_INTERFACE & access) == Opcodes.ACC_INTERFACE;
    final boolean isAnnotation = (Opcodes.ACC_ANNOTATION & access) == Opcodes.ACC_ANNOTATION;

    boolean isSuper = false;

    if (allowSuper) {
      isSuper = (Opcodes.ACC_SUPER & access) == Opcodes.ACC_SUPER;
    }
    if (projectOutput) {
      final ClassAnalyzeVisitor classAnalyzeVisitor =
          new ClassAnalyzeVisitor(className, true, false);
      classReader.accept(classAnalyzeVisitor, 0);
      final ClassIndex classIndex = classAnalyzeVisitor.getClassIndex();
      classIndex.setInterface(isInterface);
      classIndex.setAnnotation(isAnnotation);
      indexes.put(classIndex, file);
    } else {
      if (isPublic || isProtected || isSuper) {
        final ClassAnalyzeVisitor classAnalyzeVisitor =
            new ClassAnalyzeVisitor(className, true, false);
        classReader.accept(classAnalyzeVisitor, 0);
        final ClassIndex classIndex = classAnalyzeVisitor.getClassIndex();
        classIndex.setInterface(isInterface);
        classIndex.setAnnotation(isAnnotation);
        indexes.put(classIndex, file);
      }
    }
  }

  private static void replaceDescriptorsType(
      final String nameWithTP, final List<MemberDescriptor> members) {
    members.forEach(
        m -> {
          final Iterator<String> classTypeIterator =
              ClassNameUtils.parseTypeParameter(nameWithTP).iterator();
          for (String tp : m.getTypeParameters()) {
            if (classTypeIterator.hasNext()) {
              final String ct = classTypeIterator.next();

              log.trace("type nameWithoutTP: {} classTP: {} reflectTP: {}", nameWithTP, ct, tp);
              if (!ct.startsWith(ClassNameUtils.CLASS_TYPE_VARIABLE_MARK)) {
                m.putTypeParameter(tp, ct);
              }
            }
          }
        });
  }

  private static ClassAnalyzeVisitor readClassFromJar(
      final ClassReader classReader, final String nameWithoutTP, final String nameWithTP) {
    final ClassAnalyzeVisitor classAnalyzeVisitor =
        new ClassAnalyzeVisitor(nameWithoutTP, nameWithTP, false, false);
    classReader.accept(classAnalyzeVisitor, 0);
    return classAnalyzeVisitor;
  }

  private static List<String> replaceSuperClassTypeParameters(
      final String name, final ClassIndex classIndex) {

    final List<String> strings = ClassNameUtils.parseTypeParameter(name);
    final Iterator<String> iterator = strings.iterator();
    final Iterator<String> tpIterator = classIndex.getTypeParameters().iterator();
    final Map<String, String> replace = new HashMap<>(4);
    while (iterator.hasNext()) {
      final String real = iterator.next();
      if (tpIterator.hasNext()) {
        final String tp = tpIterator.next();
        if (real.contains(ClassNameUtils.CLASS_TYPE_VARIABLE_MARK)) {
          final String removed =
              ClassNameUtils.replace(real, ClassNameUtils.CLASS_TYPE_VARIABLE_MARK, "");

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

  private void addAllowClass(final String clazz) {
    this.allowClass.add(clazz);
  }

  private boolean ignorePackage(final String target) {
    if (target.endsWith(PACKAGE_INFO)) {
      return true;
    }
    for (final String keyword : this.allowClass) {
      if (target.startsWith(keyword)) {
        return false;
      }
    }
    for (final String p : ASMReflector.filterPackage) {
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
          final String fqcn = index.getRawDeclaration();
          try {
            index.setFilePath(f.getCanonicalPath());
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
          result.put(fqcn, index);
        });

    return result;
  }

  Map<ClassIndex, File> getClasses(final File file) throws IOException {

    final Map<ClassIndex, File> indexes = new ConcurrentHashMap<>(32);

    if (file.isFile() && file.getName().endsWith("jar")) {
      try (final JarFile jarFile = new JarFile(file);
          final Stream<JarEntry> jarStream = jarFile.stream().parallel();
          final Stream<JarEntry> stream =
              jarStream
                  .filter(jarEntry -> jarEntry.getName().endsWith(".class"))
                  .collect(Collectors.toList())
                  .parallelStream()) {

        stream.forEach(
            wrapIOConsumer(
                jarEntry -> {
                  final String entryName = jarEntry.getName();
                  if (!entryName.endsWith(".class")) {
                    return;
                  }
                  final String className =
                      ClassNameUtils.replaceSlash(entryName.substring(0, entryName.length() - 6));
                  if (this.ignorePackage(className)) {
                    return;
                  }
                  try (final InputStream in = jarFile.getInputStream(jarEntry)) {
                    ASMReflector.readClassIndex(indexes, in, file, false);
                  }
                }));
      }

    } else if (file.isFile() && file.getName().endsWith(".class")) {
      final String entryName = file.getName();
      if (!entryName.endsWith(".class")) {
        return indexes;
      }
      final String className =
          ClassNameUtils.replaceSlash(entryName.substring(0, entryName.length() - 6));
      if (this.ignorePackage(className)) {
        return indexes;
      }
      try (final InputStream in = new FileInputStream(file)) {
        ASMReflector.readClassIndex(indexes, in, file, true);
      }

    } else if (file.isDirectory()) {
      try (final Stream<Path> pathStream = Files.walk(file.toPath());
          final Stream<File> stream =
              pathStream
                  .map(Path::toFile)
                  .filter(f -> f.isFile() && f.getName().endsWith(".class"))
                  .collect(Collectors.toList())
                  .stream()) {

        stream.forEach(
            wrapIOConsumer(
                classFile -> {
                  final String entryName = classFile.getName();
                  if (!entryName.endsWith(".class")) {
                    return;
                  }
                  final String className =
                      ClassNameUtils.replaceSlash(entryName.substring(0, entryName.length() - 6));
                  if (this.ignorePackage(className)) {
                    return;
                  }
                  try (final InputStream in = new FileInputStream(classFile)) {
                    ASMReflector.readClassIndex(indexes, in, file, true);
                  }
                }));
      }
    }
    return indexes;
  }

  public List<MemberDescriptor> reflectAll(final InheritanceInfo info) {

    final Map<String, List<MemberDescriptor>> collect =
        info.classFileMap
            .entrySet()
            .stream()
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

    final Map<String, MemberDescriptor> result = new HashMap<>(64);
    info.inherit.forEach(
        clazz -> {
          final String key = ClassNameUtils.removeTypeParameter(clazz);
          final List<MemberDescriptor> list = collect.get(key);
          if (nonNull(list)) {
            list.forEach(
                md -> {
                  if (md.matchType(CandidateUnit.MemberType.METHOD)) {
                    final String nameKey = md.getName() + "::" + md.getParameters().toString();
                    result.putIfAbsent(nameKey, md);
                  } else if (md.matchType(CandidateUnit.MemberType.CONSTRUCTOR)) {
                    if (md.getDeclaringClass().equals(info.targetClass)) {
                      final String declaration = md.getDeclaration();
                      result.putIfAbsent(declaration, md);
                    }
                  } else {
                    result.putIfAbsent(md.getName(), md);
                  }
                });
          }
        });
    return new ArrayList<>(result.values());
  }

  private List<MemberDescriptor> reflectAll(
      final File file, final String targetClass, final List<String> targetClasses)
      throws IOException {

    if (file.isFile() && file.getName().endsWith(".jar")) {

      try (final JarFile jarFile = new JarFile(file)) {
        final Enumeration<JarEntry> entries = jarFile.entries();
        final List<MemberDescriptor> results = new ArrayList<>(64);
        while (entries.hasMoreElements()) {
          if (targetClasses.isEmpty()) {
            break;
          }
          final JarEntry jarEntry = entries.nextElement();
          final String entryName = jarEntry.getName();
          if (!entryName.endsWith(".class")) {
            continue;
          }
          final String className =
              ClassNameUtils.replaceSlash(entryName.substring(0, entryName.length() - 6));
          if (this.ignorePackage(className)) {
            continue;
          }
          final Iterator<String> classIterator = targetClasses.iterator();

          while (classIterator.hasNext()) {
            final String nameWithTP = classIterator.next();
            if (nonNull(nameWithTP)) {
              final boolean isSuper = !targetClass.equals(nameWithTP);
              final String nameWithoutTP = ClassNameUtils.removeTypeParameter(nameWithTP);

              if (className.equals(nameWithoutTP)) {
                try (final InputStream in = jarFile.getInputStream(jarEntry)) {
                  final ClassReader classReader = new ClassReader(in);
                  final List<MemberDescriptor> members =
                      this.getMemberFromJar(file, classReader, nameWithoutTP, nameWithTP);
                  if (isSuper) {
                    replaceDescriptorsType(nameWithTP, members);
                  }
                  results.addAll(members);
                  classIterator.remove();
                  break;
                }
              }

              final String innerClassName = ClassNameUtils.replaceInnerMark(className);
              if (innerClassName.equals(nameWithoutTP)) {
                try (final InputStream in = jarFile.getInputStream(jarEntry)) {
                  final ClassReader classReader = new ClassReader(in);
                  final List<MemberDescriptor> members =
                      this.getMemberFromJar(file, classReader, innerClassName, nameWithTP);
                  if (isSuper) {
                    replaceDescriptorsType(nameWithTP, members);
                  }
                  results.addAll(members);
                  classIterator.remove();
                  break;
                }
              }
            }
          }
        }
        return results;
      }
    } else if (file.isFile() && file.getName().endsWith(".class")) {

      for (String nameWithTP : targetClasses) {
        final boolean isSuper = !targetClass.equals(nameWithTP);
        final String fqcn = ClassNameUtils.removeTypeParameter(nameWithTP);
        final List<MemberDescriptor> members = getMembersFromClassFile(file, file, fqcn, false);
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
      try (final Stream<Path> pathStream = Files.walk(file.toPath());
          final Stream<File> stream =
              pathStream
                  .map(Path::toFile)
                  .filter(f -> f.isFile() && f.getName().endsWith(".class"))
                  .collect(Collectors.toList())
                  .stream()) {

        return stream
            .map(
                wrapIO(
                    f -> {
                      final String rootPath = file.getCanonicalPath();
                      final String path = f.getCanonicalPath();
                      final String className =
                          ClassNameUtils.replaceSlash(
                              path.substring(rootPath.length() + 1, path.length() - 6));

                      final Iterator<String> stringIterator = targetClasses.iterator();

                      while (stringIterator.hasNext()) {
                        final String nameWithTP = stringIterator.next();
                        final boolean isSuper = !targetClass.equals(nameWithTP);
                        final String fqcn = ClassNameUtils.removeTypeParameter(nameWithTP);

                        if (!className.equals(fqcn)) {
                          continue;
                        }

                        final List<MemberDescriptor> members =
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

  private List<MemberDescriptor> reflect(final File file, final String name) throws IOException {
    final String nameWithoutTP = ClassNameUtils.removeTypeParameter(name);
    if (file.isFile() && file.getName().endsWith(".jar")) {
      try (final JarFile jarFile = new JarFile(file);
          final Stream<JarEntry> jarStream = jarFile.stream().parallel();
          final Stream<JarEntry> stream =
              jarStream
                  .filter(jarEntry -> jarEntry.getName().endsWith(".class"))
                  .collect(Collectors.toList())
                  .parallelStream()) {

        return stream
            .map(
                wrapIO(
                    jarEntry -> {
                      final String entryName = jarEntry.getName();
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
                        try (final InputStream in = jarFile.getInputStream(jarEntry)) {
                          final ClassReader classReader = new ClassReader(in);
                          return getMemberFromJar(file, classReader, nameWithoutTP, name);
                        }
                      }

                      // To bin name
                      className = ClassNameUtils.replaceInnerMark(className);
                      if (className.equals(nameWithoutTP)) {
                        try (final InputStream in = jarFile.getInputStream(jarEntry)) {
                          final ClassReader classReader = new ClassReader(in);
                          return getMemberFromJar(file, classReader, nameWithoutTP, name);
                        }
                      }

                      return new ArrayList<MemberDescriptor>(0);
                    }))
            .filter(list -> list.size() > 0)
            .findFirst()
            .orElse(Collections.emptyList());
      }
    } else if (file.isFile() && file.getName().endsWith(".class")) {
      final List<MemberDescriptor> members = getMembersFromClassFile(file, file, nameWithoutTP);
      if (nonNull(members)) {
        return members;
      }
    } else if (file.isDirectory()) {
      try (final Stream<Path> stream = Files.walk(file.toPath())) {
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
      final File parent, final File file, String fqcn, boolean includeSuper) throws IOException {
    try (final InputStream in = new FileInputStream(file)) {
      final ClassReader classReader = new ClassReader(in);
      final String className = ClassNameUtils.replaceSlash(classReader.getClassName());
      if (className.equals(fqcn)) {
        final ClassAnalyzeVisitor cv = new ClassAnalyzeVisitor(className, className, false, true);
        classReader.accept(cv, 0);
        final List<MemberDescriptor> members = cv.getMembers();

        if (includeSuper) {
          readSuperMembers(parent, cv, members);
        }

        return members;
      }
    }
    return null;
  }

  private void readSuperMembers(File parent, ClassAnalyzeVisitor cv, List<MemberDescriptor> units) {
    final ClassIndex classIndex = cv.getClassIndex();
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
      final File file,
      final ClassReader classReader,
      final String nameWithoutTP,
      final String nameWithTP) {
    return getMemberFromJar(file, classReader, nameWithoutTP, nameWithTP, false);
  }

  private List<MemberDescriptor> getMemberFromJar(
      final File file,
      final ClassReader classReader,
      final String nameWithoutTP,
      final String nameWithTP,
      final boolean includeSuper) {
    final ClassAnalyzeVisitor cv = readClassFromJar(classReader, nameWithoutTP, nameWithTP);
    final List<MemberDescriptor> members = cv.getMembers();

    if (includeSuper) {
      this.readSuperMembers(file, cv, members);
    }
    return members;
  }

  public InheritanceInfo getReflectInfo(final Map<String, ClassIndex> index, final String fqcn) {
    final InheritanceInfo info = new InheritanceInfo(fqcn);
    final InheritanceInfo reflectInfo = this.searchReflectInfo(index, fqcn, info);
    reflectInfo.inherit = reflectInfo.inherit.stream().distinct().collect(Collectors.toList());
    return reflectInfo;
  }

  private InheritanceInfo searchReflectInfo(
      final Map<String, ClassIndex> index, final String name, final InheritanceInfo info) {

    for (Map.Entry<String, ClassIndex> entry : index.entrySet()) {
      final ClassIndex classIndex = entry.getValue();
      final File file = new File(classIndex.getFilePath());

      final String searchName = ClassNameUtils.removeTypeParameter(name);
      final String target = classIndex.toString();
      if (target.equals(searchName)) {
        this.addInheritance(index, name, info, classIndex, file);
        break;
      }
      //
      final Optional<String> opt = ClassNameUtils.toInnerClassName(name);
      if (opt.isPresent()) {
        final String inner = opt.get();

        if (target.equals(inner)) {

          if (!info.classFileMap.containsKey(file)) {
            info.classFileMap.put(file, new ArrayList<>(8));
          }

          info.inherit.add(name);
          info.classFileMap.get(file).add(name);
          final List<String> supers =
              ASMReflector.replaceSuperClassTypeParameters(name, classIndex);

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
      final Map<String, ClassIndex> index,
      final String name,
      final InheritanceInfo info,
      final ClassIndex classIndex,
      final File file) {
    // found
    if (!info.classFileMap.containsKey(file)) {
      info.classFileMap.put(file, new ArrayList<>(8));
    }
    info.inherit.add(name);
    info.classFileMap.get(file).add(name);

    final List<String> supers = ASMReflector.replaceSuperClassTypeParameters(name, classIndex);

    Collections.reverse(supers);
    supers.forEach(
        superClass -> {
          InheritanceInfo ignore = this.searchReflectInfo(index, superClass, info);
        });
  }
}
