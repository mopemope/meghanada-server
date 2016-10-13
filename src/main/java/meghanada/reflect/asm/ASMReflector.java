package meghanada.reflect.asm;

import meghanada.config.Config;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static meghanada.utils.FunctionUtils.wrapIO;
import static meghanada.utils.FunctionUtils.wrapIOConsumer;


class ASMReflector {

    private static final String[] filterPackage = new String[]{
            "sun.",
            "com.sun.",
            "com.oracle",
            "oracle.jrockit",
            "jdk",
            "org.omg",
            "org.ietf.",
            "org.jcp.",
            "netscape"
    };

    private static Logger log = LogManager.getLogger(ASMReflector.class);
    private static ASMReflector asmReflector;
    private Set<String> allowClass = new HashSet<>();

    private ASMReflector() {
        Config.load()
                .getAllowClass()
                .forEach(this::addAllowClass);
    }

    public static ASMReflector getInstance() {
        if (asmReflector == null) {
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
        StringBuilder sb = new StringBuilder();
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

    public void addAllowClass(final String clazz) {
        this.allowClass.add(clazz);
    }

    boolean ignorePackage(final String target) {
        if (this.allowClass.contains(target)) {
            return false;
        }
        for (final String p : ASMReflector.filterPackage) {
            if (target.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    Map<ClassIndex, File> getClasses(final File file) throws IOException {
        final Map<ClassIndex, File> indexes = new ConcurrentHashMap<>(8);

        if (file.isFile() && file.getName().endsWith("jar")) {
            final JarFile jarFile = new JarFile(file);
            this.getJarEntryStream(jarFile).forEach(wrapIOConsumer(jarEntry -> {
                final String entryName = jarEntry.getName();
                if (!entryName.endsWith(".class")) {
                    return;
                }
                final String className = ClassNameUtils.replaceSlash(entryName.substring(0, entryName.length() - 6));
                if (this.ignorePackage(className)) {
                    return;
                }
                try (final InputStream in = jarFile.getInputStream(jarEntry)) {
                    this.readClassIndex(indexes, in, file, false);
                }
            }));

        } else if (file.isFile() && file.getName().endsWith(".class")) {
            final String entryName = file.getName();
            if (!entryName.endsWith(".class")) {
                return indexes;
            }
            final String className = ClassNameUtils.replaceSlash(entryName.substring(0, entryName.length() - 6));
            if (this.ignorePackage(className)) {
                return indexes;
            }
            try (final InputStream in = new FileInputStream(file)) {
                this.readClassIndex(indexes, in, file, true);
            }
        } else if (file.isDirectory()) {
            this.getClassFileStream(file).forEach(wrapIOConsumer(classFile -> {
                final String entryName = classFile.getName();
                if (!entryName.endsWith(".class")) {
                    return;
                }
                final String className = ClassNameUtils.replaceSlash(entryName.substring(0, entryName.length() - 6));
                if (this.ignorePackage(className)) {
                    return;
                }
                try (InputStream in = new FileInputStream(classFile)) {
                    this.readClassIndex(indexes, in, file, true);
                }
            }));
        }
        return indexes;
    }

    private void readClassIndex(final Map<ClassIndex, File> indexes, final InputStream in, final File file, boolean allowSuper) throws IOException {
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
            final ClassAnalyzeVisitor classAnalyzeVisitor = new ClassAnalyzeVisitor(className, true, false);
            classReader.accept(classAnalyzeVisitor, 0);
            final ClassIndex classIndex = classAnalyzeVisitor.getClassIndex();
            classIndex.isInterface = isInterface;
            classIndex.isAnnotation = isAnnotation;
            indexes.put(classIndex, file);
        } else {
            if (isPublic || isProtected || isSuper) {
                final ClassAnalyzeVisitor classAnalyzeVisitor = new ClassAnalyzeVisitor(className, true, false);
                classReader.accept(classAnalyzeVisitor, 0);
                final ClassIndex classIndex = classAnalyzeVisitor.getClassIndex();
                classIndex.isInterface = isInterface;
                classIndex.isAnnotation = isAnnotation;
                indexes.put(classIndex, file);
            }
        }
    }

    List<MemberDescriptor> reflectAll(final InheritanceInfo info) throws IOException {

        final Map<String, List<MemberDescriptor>> collect = info.classFileMap
                .entrySet()
                .stream()
                .map(wrapIO(entry -> this.reflectAll(entry.getKey(), info.targetClass, new ArrayList<>(entry.getValue()))))
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(md -> ClassNameUtils.removeTypeParameter(md.getDeclaringClass()), Collectors.toList()));

        final Map<String, MemberDescriptor> result = new HashMap<>(64);
        info.inherit.forEach(clazz -> {
            final String key = ClassNameUtils.removeTypeParameter(clazz);
            final List<MemberDescriptor> list = collect.get(key);
            if (list != null) {
                list.forEach(md -> {
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

    private List<MemberDescriptor> reflectAll(final File file, final String targetClass, final List<String> targetClasses) throws IOException {
        if (file.isFile() && file.getName().endsWith(".jar")) {

            final JarFile jarFile = new JarFile(file);
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
                final String className = ClassNameUtils.replaceSlash(entryName.substring(0, entryName.length() - 6));
                if (this.ignorePackage(className)) {
                    continue;
                }
                final Iterator<String> classIterator = targetClasses.iterator();

                while (classIterator.hasNext()) {
                    final String nameWithTP = classIterator.next();
                    if (nameWithTP != null) {
                        final boolean isSuper = !targetClass.equals(nameWithTP);
                        final String nameWithoutTP = ClassNameUtils.removeTypeParameter(nameWithTP);

                        if (className.equals(nameWithoutTP)) {
                            try (final InputStream in = jarFile.getInputStream(jarEntry)) {
                                final ClassReader classReader = new ClassReader(in);
                                final List<MemberDescriptor> members = this.getMemberFromJar(file, classReader, nameWithoutTP, nameWithTP);
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
                                final List<MemberDescriptor> members = this.getMemberFromJar(file, classReader, innerClassName, nameWithTP);
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
        } else if (file.isFile() && file.getName().endsWith(".class")) {

            for (String nameWithTP : targetClasses) {
                final boolean isSuper = !targetClass.equals(nameWithTP);
                final String fqcn = ClassNameUtils.removeTypeParameter(nameWithTP);
                final List<MemberDescriptor> members = getMembersFromClassFile(file, file, fqcn, false);
                if (members != null) {
                    // 1 file
                    if (isSuper) {
                        replaceDescriptorsType(nameWithTP, members);
                    }
                    return members;
                }
            }
            return Collections.emptyList();
        } else if (file.isDirectory()) {
            return this.getClassFileStream(file)
                    .map(wrapIO(f -> {

                        final String rootPath = file.getCanonicalPath();
                        final String path = f.getCanonicalPath();
                        final String className = ClassNameUtils.replaceSlash(path.substring(rootPath.length() + 1, path.length() - 6));

                        final Iterator<String> stringIterator = targetClasses.iterator();
                        while (stringIterator.hasNext()) {
                            final String nameWithTP = stringIterator.next();
                            final boolean isSuper = !targetClass.equals(nameWithTP);
                            final String fqcn = ClassNameUtils.removeTypeParameter(nameWithTP);

                            if (!className.equals(fqcn)) {
                                continue;
                            }

                            final List<MemberDescriptor> members = getMembersFromClassFile(file, f, fqcn, false);
                            if (members != null) {

                                if (isSuper) {
                                    replaceDescriptorsType(nameWithTP, members);
                                }
                                // found
                                stringIterator.remove();
                                return members;
                            }
                        }
                        return null;
                    }))
                    .filter(memberDescriptors -> memberDescriptors != null && memberDescriptors.size() > 0)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private void replaceDescriptorsType(final String nameWithTP, final List<MemberDescriptor> members) {
        members.forEach(m -> {
            final Iterator<String> classTypeIterator = ClassNameUtils.parseTypeParameter(nameWithTP).iterator();
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

    private List<MemberDescriptor> reflect(final File file, final String name) throws IOException {
        final String nameWithoutTP = ClassNameUtils.removeTypeParameter(name);
        if (file.isFile() && file.getName().endsWith(".jar")) {
            final JarFile jarFile = new JarFile(file);

            return this.getJarEntryStream(jarFile)
                    .map(wrapIO(jarEntry -> {
                        final String entryName = jarEntry.getName();
                        if (!entryName.endsWith(".class")) {
                            return new ArrayList<MemberDescriptor>(0);
                        }
                        String className = ClassNameUtils.replaceSlash(entryName.substring(0, entryName.length() - 6));
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

        } else if (file.isFile() && file.getName().endsWith(".class")) {
            final List<MemberDescriptor> members = getMembersFromClassFile(file, file, nameWithoutTP);
            if (members != null) {
                return members;
            }
        } else if (file.isDirectory()) {
            return Files.walk(file.toPath())
                    .map(Path::toFile)
                    .filter(f -> f.isFile() && f.getName().endsWith(".class"))
                    .map(wrapIO(f -> getMembersFromClassFile(file, f, nameWithoutTP)))
                    .filter(descriptors -> descriptors != null && descriptors.size() > 0)
                    .findFirst().orElse(Collections.emptyList());
        }
        return Collections.emptyList();
    }

    private List<MemberDescriptor> getMembersFromClassFile(File parent, File file, String fqcn) throws IOException {
        return getMembersFromClassFile(parent, file, fqcn, true);
    }

    private List<MemberDescriptor> getMembersFromClassFile(final File parent, final File file, String fqcn, boolean includeSuper) throws IOException {
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
        List<List<MemberDescriptor>> lists = classIndex.supers
                .stream()
                .parallel()
                .map(wrapIO(s -> reflect(parent, s)))
                .collect(Collectors.toList());
        lists.forEach(units::addAll);
    }

    private List<MemberDescriptor> getMemberFromJar(final File file, final ClassReader classReader, final String nameWithoutTP, final String nameWithTP) {
        return getMemberFromJar(file, classReader, nameWithoutTP, nameWithTP, false);
    }

    private List<MemberDescriptor> getMemberFromJar(final File file, final ClassReader classReader, final String nameWithoutTP, final String nameWithTP, final boolean includeSuper) {
        final ClassAnalyzeVisitor cv = readClassFromJar(classReader, nameWithoutTP, nameWithTP);
        final List<MemberDescriptor> members = cv.getMembers();

        if (includeSuper) {
            this.readSuperMembers(file, cv, members);
        }
        return members;
    }

    private ClassAnalyzeVisitor readClassFromJar(final ClassReader classReader, final String nameWithoutTP, final String nameWithTP) {
        final ClassAnalyzeVisitor classAnalyzeVisitor = new ClassAnalyzeVisitor(nameWithoutTP, nameWithTP, false, false);
        classReader.accept(classAnalyzeVisitor, 0);
        return classAnalyzeVisitor;
    }

    InheritanceInfo getReflectInfo(final Map<ClassIndex, File> index, final String fqcn) {
        final InheritanceInfo info = new InheritanceInfo(fqcn);
        this.getReflectInfo(index, fqcn, info);
        info.inherit = info.inherit.stream().distinct().collect(Collectors.toList());
        return info;
    }

    private InheritanceInfo getReflectInfo(final Map<ClassIndex, File> index, final String name, InheritanceInfo info) {
        for (Map.Entry<ClassIndex, File> entry : index.entrySet()) {
            final ClassIndex classIndex = entry.getKey();
            final File file = entry.getValue();

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
                    final List<String> supers = this.replaceSuperClassTypeParameters(name, classIndex);
                    Collections.reverse(supers);
                    supers.forEach(superClass -> this.getReflectInfo(index, superClass, info));
                    break;
                }
            }
        }
        return info;
    }

    private void addInheritance(final Map<ClassIndex, File> index, final String name, final InheritanceInfo info, final ClassIndex classIndex, final File file) {
        // found
        if (!info.classFileMap.containsKey(file)) {
            info.classFileMap.put(file, new ArrayList<>(8));
        }
        info.inherit.add(name);
        info.classFileMap.get(file).add(name);

        final List<String> supers = this.replaceSuperClassTypeParameters(name, classIndex);

        Collections.reverse(supers);
        supers.forEach(superClass -> this.getReflectInfo(index, superClass, info));
    }

    private List<String> replaceSuperClassTypeParameters(final String name, final ClassIndex classIndex) {
        final List<String> strings = ClassNameUtils.parseTypeParameter(name);
        final Iterator<String> iterator = strings.iterator();
        final Iterator<String> tpIterator = classIndex.typeParameters.iterator();
        final Map<String, String> replace = new HashMap<>(4);
        while (iterator.hasNext()) {
            final String real = iterator.next();
            if (tpIterator.hasNext()) {
                final String tp = tpIterator.next();
                if (real.contains(ClassNameUtils.CLASS_TYPE_VARIABLE_MARK)) {
                    final String removed = ClassNameUtils.replace(real, ClassNameUtils.CLASS_TYPE_VARIABLE_MARK, "");

                    if (!tp.equals(removed)) {
                        replace.put(ClassNameUtils.CLASS_TYPE_VARIABLE_MARK + tp, real);
                    }
                }
            }
        }
        List<String> supers = new ArrayList<>(classIndex.supers);
        if (!replace.isEmpty()) {
            supers = classIndex
                    .supers
                    .stream()
                    .map(s -> ClassNameUtils.replaceFromMap(s, replace))
                    .collect(Collectors.toList());
        }
        return supers;
    }

    private Stream<File> getClassFileStream(final File file) throws IOException {
        return Files.walk(file.toPath())
                .map(Path::toFile)
                .filter(f -> f.isFile() && f.getName().endsWith(".class"))
                .collect(Collectors.toList())
                .stream();
    }

    private Stream<JarEntry> getJarEntryStream(final JarFile jarFile) {
        return jarFile.stream()
                .filter(jarEntry -> jarEntry.getName().endsWith(".class"))
                .collect(Collectors.toList())
                .stream();
    }

}
