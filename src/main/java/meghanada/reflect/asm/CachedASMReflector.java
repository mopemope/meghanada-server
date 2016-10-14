package meghanada.reflect.asm;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoCallback;
import com.esotericsoftware.kryo.pool.KryoPool;
import com.google.common.base.Splitter;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.UncheckedExecutionException;
import meghanada.config.Config;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.MethodDescriptor;
import meghanada.reflect.names.MethodParameterNames;
import meghanada.utils.ClassName;
import meghanada.utils.ClassNameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.DeflaterOutputStream;

import static meghanada.utils.FunctionUtils.wrapIOConsumer;


public class CachedASMReflector {

    public static final int CACHE_SIZE = 1024 * 4;
    private static final Logger log = LogManager.getLogger(CachedASMReflector.class);

    private static final Pattern PACKAGE_RE = Pattern.compile("\\.\\*");
    private static CachedASMReflector cachedASMReflector;

    private final Map<String, ClassIndex> globalClassIndex = new ConcurrentHashMap<>(CACHE_SIZE * 8);

    // Key:FQCN Val:JarFile
    private final Map<String, File> classFileMap = new ConcurrentHashMap<>(CACHE_SIZE * 8);

    private final Map<ClassIndex, File> reflectIndex = new ConcurrentHashMap<>(CACHE_SIZE * 8);

    private final List<File> jars = new ArrayList<>(32);
    private final List<File> directories = new ArrayList<>(4);
    private final LoadingCache<String, List<MemberDescriptor>> memberCache;
    private final KryoPool kryoPool;

    private Map<String, String> standardClasses;

    private CachedASMReflector() {
        this.memberCache = CacheBuilder.newBuilder()
                .initialCapacity(64)
                .maximumSize(256)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build(new MemberCacheLoader(this.classFileMap, reflectIndex));

        this.kryoPool = new KryoPool.Builder(() -> {
            final Kryo kryo = new Kryo();
            kryo.register(ClassIndex.class);
            kryo.register(MemberDescriptor.class);
            kryo.register(MethodParameterNames.class);
            return kryo;
        }).softReferences().build();
    }

    public static CachedASMReflector getInstance() {
        if (cachedASMReflector == null) {
            cachedASMReflector = new CachedASMReflector();
        }
        return cachedASMReflector;
    }

    public KryoPool getKryoPool() {
        return kryoPool;
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

        this.jars.stream().parallel().forEach(wrapIOConsumer(file -> {
            if (file.getName().endsWith(".jar") && this.classFileMap.containsValue(file)) {
                //skip cached jar
                return;
            }

            final ASMReflector reflector = ASMReflector.getInstance();
            reflector.getClasses(file)
                    .entrySet()
                    .parallelStream()
                    .forEach(classIndexFileEntry -> {
                        final ClassIndex classIndex1 = classIndexFileEntry.getKey();
                        final File file1 = classIndexFileEntry.getValue();
                        final String fqcn = classIndex1.getRawDeclaration();
                        this.globalClassIndex.put(fqcn, classIndex1);
                        this.classFileMap.put(fqcn, file1);
                        this.reflectIndex.put(classIndex1, file1);
                    });

        }));
        this.directories.stream().parallel().forEach(wrapIOConsumer(file -> {
            if (file.getName().endsWith(".jar") && this.classFileMap.containsValue(file)) {
                //skip cached jar
                return;
            }

            final ASMReflector reflector = ASMReflector.getInstance();
            reflector.getClasses(file)
                    .entrySet()
                    .parallelStream()
                    .forEach(classIndexFileEntry -> {
                        final ClassIndex classIndex1 = classIndexFileEntry.getKey();
                        final File file1 = classIndexFileEntry.getValue();
                        final String fqcn = classIndex1.getRawDeclaration();
                        this.globalClassIndex.put(fqcn, classIndex1);
                        this.classFileMap.put(fqcn, file1);
                        this.reflectIndex.put(classIndex1, file1);
                    });

        }));

    }

    public boolean containsFQCN(String fqcn) {
        return this.classFileMap.containsKey(fqcn);
    }

    public File getClassFile(String fqcn) {
        return this.classFileMap.get(fqcn);
    }

    private String matchFQCN(final ClassIndex classIndex, final String className) {
        // TODO tuning
        final String name = classIndex.getName();
        final String declaration = classIndex.getDeclaration();
        final String result = classIndex.getRawDeclaration();
        if (name.equals(className) || declaration.equals(className)) {
            return result;
        }
        return ClassNameUtils.toInnerClassName(className).map(innerName -> {
            if (name.equals(innerName) || declaration.equals(innerName)) {
                return result;
            }
            final String innerParent = innerName.substring(0, innerName.lastIndexOf("$"));
            final String innerClass = innerName.substring(innerName.lastIndexOf("$") + 1);
            if (name.equals(innerParent)) {
                for (final String superClass : classIndex.supers) {
                    final String searchName = superClass + '$' + ClassNameUtils.removeTypeParameter(innerClass);
                    if (this.globalClassIndex.containsKey(searchName)) {
                        return searchName;
                    }
                }
            }
            return null;
        }).orElse(null);
    }

    public String classNameToFQCN(final String className) {
        // log.debug("classNameToFQCN:{}", className);
        // TODO tuning
        if (this.globalClassIndex.containsKey(className)) {
            final ClassIndex classIndex = this.globalClassIndex.get(className);
            return classIndex.getRawDeclaration();
        }

        return this.globalClassIndex.values().parallelStream()
                .map(classIndex -> matchFQCN(classIndex, className))
                .filter(s -> s != null)
                .findFirst()
                .orElse(null);
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
        this.globalClassIndex.values().parallelStream()
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

    public List<ClassIndex> fuzzySearchClasses(final String keyword, final boolean anno) {
        final int length = keyword.length() + 1;
        return this.globalClassIndex.values()
                .stream()
                .filter(classIndex -> {
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
                .stream()
                .filter(classIndex -> classIndex.getReturnType().startsWith(parent + "$"))
                .map(ClassIndex::clone)
                .collect(Collectors.toList());
    }

    public List<ClassIndex> searchClasses(final String keyword) {
        return this.searchClasses(keyword, true, false);
    }

    public List<ClassIndex> searchAnnotations(final String keyword) {
        return this.searchClasses(keyword, true, true);
    }

    public List<ClassIndex> searchClasses(final String keyword, final boolean partial, final boolean anno) {
        return this.globalClassIndex.values()
                .stream()
                .filter(classIndex -> {
                    if (anno && !classIndex.isAnnotation) {
                        return false;
                    }
                    return this.containsKeyword(keyword, partial, classIndex);
                })
                .map(ClassIndex::clone)
                .collect(Collectors.toList());
    }

    private boolean containsKeyword(final String keyword, final boolean partial, final ClassIndex index) {
        if (partial) {
            final String lower = keyword.toLowerCase();
            final String className = index.getName().toLowerCase();
            return className.contains(lower);
        } else {
            return index.getName().equals(keyword);
        }
    }

    public void invalidate(final String name) {
        this.memberCache.invalidate(name);
    }

    public List<MemberDescriptor> reflect(final String className) {
        final ClassName cn = new ClassName(className);
        // check type parameter
        final String classWithoutTP = cn.getName();
        List<MemberDescriptor> members;
        try {
            members = this.memberCache.get(classWithoutTP);
        } catch (ExecutionException e) {
            throw new UncheckedExecutionException(e);
        }
        members = members.stream()
                .map(MemberDescriptor::clone)
                .collect(Collectors.toList());

        if (cn.hasTypeParameter()) {
            return this.replaceMembers(classWithoutTP, className, members);
        }

        return members;
    }

    private List<MemberDescriptor> replaceMembers(final String classWithoutTP, final String className, final List<MemberDescriptor> members) {

        final ClassIndex classIdx = this.globalClassIndex.get(classWithoutTP);
        if (classIdx != null) {
            return replaceTypeParameters(className, classIdx.getDisplayDeclaration(), members);
        }
        return members;
    }

    private List<MemberDescriptor> replaceTypeParameters(final String className, final String classWithTP, final List<MemberDescriptor> members) {
        final int idx1 = classWithTP.indexOf("<");
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

    private Stream<JarEntry> getJarEntryStream(final JarFile jarFile) {
        return jarFile.stream()
                .filter(jarEntry -> jarEntry.getName().endsWith(".class"))
                .collect(Collectors.toList())
                .stream();
    }

    public void createCache(final File jar, final File outputRoot) throws IOException {
        final String jarName = jar.getName();
        if (!jarName.endsWith(".jar") || jarName.contains("SNAPSHOT")) {
            return;
        }
        final JarFile jarFile = new JarFile(jar);
        final ASMReflector asmReflector = ASMReflector.getInstance();
        getJarEntryStream(jarFile)
                .filter(jarEntry -> jarEntry.getName().endsWith(".class"))
                .forEach(wrapIOConsumer(jarEntry -> {

                    final String entryName = jarEntry.getName();
                    if (!entryName.endsWith(".class")) {
                        return;
                    }
                    final String className = ClassNameUtils.replaceSlash(entryName.substring(0, entryName.length() - 6));
                    if (asmReflector.ignorePackage(className)) {
                        return;
                    }

                    if (existsClassCache(className, outputRoot)) {
                        // log.debug("skip  :{}", className);
                        return;
                    }

                    if (this.globalClassIndex.containsKey(className)) {
                        final ClassIndex ci = globalClassIndex.get(className);
                        ClassName cn = new ClassName(className);
                        final String fqcn = cn.getName();
                        final InheritanceInfo info = asmReflector.getReflectInfo(reflectIndex, fqcn);
                        final List<MemberDescriptor> descriptors = asmReflector.reflectAll(info);
                        this.writeCache(ci, descriptors, outputRoot);
                    }
                }));
    }


    private boolean existsClassCache(final String className, final File root) {
        final File outFile = getClassCacheFile(className, root);
        return outFile.exists();
    }

    private File getClassCacheFile(final String className, final File root) {
        final String javaVersion = Config.load().getJavaVersion();
        final String path = ClassNameUtils.replace(className, ".", "/");
        return new File(root, javaVersion + "/class/" + path + ".dat");
    }

    public void writeCache(final ClassIndex classIndex, final List<MemberDescriptor> members, final File root) throws FileNotFoundException {

        final String javaVersion = Config.load().getJavaVersion();
        final String rawDeclaration = classIndex.getRawDeclaration();
        final String path = ClassNameUtils.replace(rawDeclaration, ".", "/");
        final File outFile1 = new File(root, javaVersion + "/class/" + path + ".dat");
        final File outFile2 = new File(root, javaVersion + "/member/" + path + ".dat");
        outFile1.getParentFile().mkdirs();
        outFile2.getParentFile().mkdirs();

        // ClassIndex
        this.kryoPool.run(new KryoCallback<ClassIndex>() {
            @Override
            public ClassIndex execute(Kryo kryo) {
                try (final Output out = new Output(new FileOutputStream(outFile1))) {
                    kryo.writeObject(out, classIndex);
                } catch (FileNotFoundException e) {
                    throw new UncheckedIOException(e);
                }
                return classIndex;
            }
        });

        // Members
        this.kryoPool.run(new KryoCallback<List<MemberDescriptor>>() {
            @Override
            public List<MemberDescriptor> execute(Kryo kryo) {
                try (final Output output = new Output(new DeflaterOutputStream(new BufferedOutputStream(new FileOutputStream(outFile2), 8192)))) {
                    kryo.writeObject(output, members);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return members;
            }
        });
    }

    private ClassIndex readClassIndexFromCache(final String fqcn, final File root) throws IOException {
        final File in = this.getClassCacheFile(fqcn, root);
        if (in.exists()) {

            return this.kryoPool.run(new KryoCallback<ClassIndex>() {
                @Override
                public ClassIndex execute(final Kryo kryo) {
                    try (final Input input = new Input(new FileInputStream(in))) {
                        return kryo.readObject(input, ClassIndex.class);
                    } catch (FileNotFoundException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        }
        return null;
    }

    public Stream<MemberDescriptor> reflectStream(final String className) {
        return this.reflect(className).stream();
    }

    public Stream<MemberDescriptor> reflectStaticStream(final String className) {
        return this.reflectStream(className)
                .filter(md -> {
                    final String declaration = md.getDeclaration();
                    return declaration.contains("public") && declaration.contains("static");
                });
    }

    public Stream<MemberDescriptor> reflectFieldStream(final String className) {
        return this.reflectFieldStream(className, null);
    }

    public Stream<MemberDescriptor> reflectFieldStream(final String className, final String name) {
        return this.reflect(className)
                .stream()
                .filter(m -> {
                    if (name == null) {
                        return m.matchType(CandidateUnit.MemberType.FIELD);
                    }
                    return m.getName().equals(name) && m.matchType(CandidateUnit.MemberType.FIELD);
                });
    }

    public Stream<MemberDescriptor> reflectMethodStream(final String className, final String name) {
        return this.reflect(className)
                .stream()
                .filter(m -> {
                    if (name == null) {
                        return m.matchType(CandidateUnit.MemberType.METHOD);
                    }
                    return m.getName().equals(name) && m.matchType(CandidateUnit.MemberType.METHOD);
                });
    }

    public Stream<MemberDescriptor> reflectMethodStream(final String className, final String name, final int argLen) {
        return this.reflect(className)
                .stream()
                .filter(m -> {
                    final String mName = m.getName();
                    final List<String> parameters = m.getParameters();
                    return mName.equals(name)
                            && m.matchType(CandidateUnit.MemberType.METHOD)
                            && parameters.size() == argLen;
                });
    }

    public Stream<MemberDescriptor> reflectMethodStream(final String className, final String name, final int argLen, final String sig) {
        return this.reflect(className)
                .stream()
                .filter(m -> {
                    final String mName = m.getName();
                    final List<String> parameters = m.getParameters();
                    if (mName.equals(name)
                            && m.matchType(CandidateUnit.MemberType.METHOD)
                            && parameters.size() == argLen) {

                        final MethodDescriptor md = (MethodDescriptor) m;
                        final String formalType = md.formalType;
                        if (formalType != null) {
                            final int start1 = sig.indexOf("[");
                            final int end1 = sig.lastIndexOf("]");
                            final Iterable<String> split1 = Splitter.on(",").split(sig.substring(start1 + 1, end1));

                            final String mdSig = m.getSig();
                            final int start2 = mdSig.indexOf("[");
                            final int end2 = mdSig.lastIndexOf("]");
                            final Iterable<String> split2 = Splitter.on(",").split(mdSig.substring(start2 + 1, end2));
                            final Set<String> typeParameters = md.getTypeParameters();

                            boolean match = true;
                            final Iterator<String> iterator2 = split2.iterator();
                            for (final Iterator<String> iterator1 = split1.iterator(); iterator1.hasNext(); ) {
                                final String next1 = iterator1.next();
                                final String next2 = iterator2.next();
                                if (next2.startsWith(ClassNameUtils.FORMAL_TYPE_VARIABLE_MARK)) {
                                    final String key = next2.substring(2);
                                    if (typeParameters.contains(key)) {
                                        md.getTypeParameterMap().put(key, next2);
                                        continue;
                                    }
                                    match = false;
                                } else {
                                    if (!next1.equals(next2)) {
                                        match = false;
                                    }
                                }
                            }
                            return match;
                        }

                        final String mdSig = m.getSig();
                        log.trace("compare sig sig={} mdSig={}", sig, mdSig);
                        return sig.equals(mdSig);
                    }
                    return false;
                });
    }

    public Stream<MemberDescriptor> reflectConstructorStream(final String className, final int argLen, final String sig) {
        return this.reflect(className)
                .stream()
                .filter(m -> {
                    final String mName = m.getName();
                    final List<String> parameters = m.getParameters();
                    return m.matchType(CandidateUnit.MemberType.CONSTRUCTOR)
                            && parameters.size() == argLen
                            && sig.equals(mName + "::" + parameters.toString());
                });
    }

    public Stream<String> getSuperClassStream(final String className) {
        return this.containsClassIndex(className)
                .map(classIndex -> classIndex.supers.stream()).orElse(new ArrayList<String>(0).stream());
    }

    public Optional<ClassIndex> containsClassIndex(final String className) {
        return Optional.ofNullable(this.globalClassIndex.get(className));
    }

    public Map<String, String> getStandardClasses() {
        if (this.standardClasses != null) {
            return this.standardClasses;
        }
        this.standardClasses = new ImmutableMap.Builder<String, String>()
                .putAll(this.getPackageClasses("java.lang"))
                .build();
        return this.standardClasses;
    }

}
