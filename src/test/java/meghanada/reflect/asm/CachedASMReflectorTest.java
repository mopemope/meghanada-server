package meghanada.reflect.asm;

import com.google.common.base.Stopwatch;
import meghanada.GradleTestBase;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.MemberDescriptor;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static meghanada.config.Config.timeIt;
import static meghanada.config.Config.traceIt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CachedASMReflectorTest extends GradleTestBase {

    @org.junit.BeforeClass
    public static void beforeClass() throws Exception {
        GradleTestBase.setupReflector();
    }

    @Ignore
    @Test
    public void testCreateClassIndexes() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        Stopwatch stopwatch = Stopwatch.createStarted();
        cachedASMReflector.createClassIndexes();
        System.out.println(stopwatch.stop());
        // System.out.println(cachedASMReflector.getGlobalClassIndex().size());
        assertTrue(9055 <= cachedASMReflector.getGlobalClassIndex().size());
    }

    @Test
    public void testSearchClasses() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        Stopwatch stopwatch = Stopwatch.createStarted();
        Collection<? extends CandidateUnit> candidateUnits = cachedASMReflector.searchClasses("map");
        System.out.println(stopwatch.stop());
        // candidateUnits.forEach(u -> System.out.println(u.getDeclaration()));
        assertEquals(239, candidateUnits.size());
    }

    @Test
    public void testSearchClasses1() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        Collection<? extends CandidateUnit> candidateUnits = cachedASMReflector.searchClasses("Map", false, false);
        // candidateUnits.forEach(c -> System.out.println(c));
        assertEquals(1, candidateUnits.size());
    }

    @Test
    public void testGetPackageClasses1() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        Map<String, String> map = cachedASMReflector.getPackageClasses("java.lang");
        assertEquals(104, map.size());
        // System.out.println(map);
    }

    @Test
    public void testGetPackageClasses2() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        Map<String, String> map = cachedASMReflector.getPackageClasses("java.util.*");
        assertEquals(128, map.size());
        // System.out.println(map);
    }


    @Test
    public void testReflectJavaLangString() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();

        Stopwatch stopwatch = Stopwatch.createUnstarted();
        {
            String fqcn = "java.lang.String";

            stopwatch.start();
            List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
            System.out.println(stopwatch.stop());
            stopwatch.reset();
            memberDescriptors.forEach(m -> System.out.println(m));
            assertEquals(100, memberDescriptors.size());
        }
        {
            String fqcn = "java.lang.String";
            cachedASMReflector.invalidate(fqcn);
            stopwatch.start();
            List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
            System.out.println(stopwatch.stop());
            memberDescriptors.forEach(m -> System.out.println(m));
            stopwatch.reset();
            assertEquals(100, memberDescriptors.size());
        }
    }

    @Test
    public void testReflectList() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();

        {
            String fqcn = "java.util.List";
            List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
            memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
            assertEquals(41, memberDescriptors.size());
        }
    }

    @Test
    public void testReflectTypeParam1() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();

        {
            String fqcn = "java.util.Map<String, Long>";
            List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
            memberDescriptors.forEach(md -> System.out.println(md.getDisplayDeclaration()));
            assertEquals(34, memberDescriptors.size());
        }
    }

    @Test
    public void testReflectTypeParam2() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();

        {
            String fqcn = "com.google.common.collect.FluentIterable<String>";
            List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
            memberDescriptors.forEach(md -> System.out.println(md.getDisplayDeclaration()));
            assertEquals(48, memberDescriptors.size());
        }
    }

    @Test
    public void testReflectTypeParam3() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();

        {
            String fqcn = "java.util.stream.Stream<String>";
            List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
            memberDescriptors.forEach(md -> System.out.println(md.getDeclaration()));
            assertEquals(58, memberDescriptors.size());
        }
    }

    @Test
    public void testReflectTypeParam4() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();

        {
            String fqcn = "com.google.common.base.Joiner";
            List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
            memberDescriptors.forEach(md -> System.out.println(md.getDisplayDeclaration()));
            assertEquals(30, memberDescriptors.size());
        }
    }

    @Test
    public void testReflect1() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();

        {
            String fqcn = "com.esotericsoftware.kryo.Kryo";
            List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
//            for (MemberDescriptor md : memberDescriptors) {
//                System.out.println(md);
//            }
            memberDescriptors.forEach(md -> System.out.println(md.getDisplayDeclaration()));
            assertEquals(83, memberDescriptors.size());
        }

    }

    @Test
    public void testReflect2() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();

        {
            String fqcn = "java.util.Iterator<Map.Entry<WatchKey, Path>>";
            List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
//            for (MemberDescriptor md : memberDescriptors) {
//                System.out.println(md);
//            }
            memberDescriptors.forEach(memberDescriptor -> System.out.println(memberDescriptor.getDeclaration()));
            assertEquals(15, memberDescriptors.size());
        }

    }

    @Test
    public void testReflect3() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();

        {
            String fqcn = "java.util.jar.JarFile";
            List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
            memberDescriptors.sort((o1, o2) -> {
                final String name1 = o1.getName();
                final String name2 = o2.getName();
                return name1.compareTo(name2);
            });
            memberDescriptors.forEach(md -> {
                System.out.println(md.getDeclaringClass() + " # " + md.getDeclaration());
            });
            assertEquals(37, memberDescriptors.size());
        }

    }

    @Test
    public void testReflect4() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();

        {
            String fqcn = "javax.management.relation.RoleList";
            List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
            memberDescriptors.sort((o1, o2) -> {
                final String name1 = o1.getName();
                final String name2 = o2.getName();
                return name1.compareTo(name2);
            });
            memberDescriptors.forEach(md -> {
                System.out.println(md.getDisplayDeclaration());
                // System.out.println(md.getDeclaringClass() + " # " + md.getDisplayDeclaration());
            });
            assertEquals(59, memberDescriptors.size());
        }

    }

    @Test
    public void testLocalReflect1() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addClasspath(getOutputDir());
        cachedASMReflector.createClassIndexes();

        {
            String fqcn = "meghanada.parser.source.TypeScope";
            List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
            // memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
            assertEquals(74, memberDescriptors.size());
        }

    }

    @Test
    public void testLocalReflect2() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addClasspath(getOutputDir());
        cachedASMReflector.createClassIndexes();

        {
            String fqcn = "meghanada.parser.source.MethodScope";
            List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
            // memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
            assertEquals(57, memberDescriptors.size());
        }

    }

    @Test
    public void testLocalReflect3() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addClasspath(getOutputDir());
        cachedASMReflector.createClassIndexes();

        {
            String fqcn = "meghanada.reflect.MemberDescriptor";
            List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
            memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
            assertEquals(45, memberDescriptors.size());
        }

    }

    @Test
    public void testLocalReflect4() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addClasspath(getOutputDir());
        cachedASMReflector.createClassIndexes();

        {
            String fqcn = "meghanada.parser.source.MethodCallSymbol";
            List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
            memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
            assertEquals(29, memberDescriptors.size());
        }

    }

    @Test
    public void testLocalReflect5() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addClasspath(getOutputDir());
        cachedASMReflector.createClassIndexes();

        {
            String fqcn = "meghanada.reflect.asm.MethodAnalyzeVisitor";
            List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
            memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
            assertEquals(70, memberDescriptors.size());

        }

    }

    @Test
    public void testLocalReflect6() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addClasspath(getOutputDir());
        cachedASMReflector.createClassIndexes();

        {
            String fqcn = "meghanada.utils.ClassNameUtils";
            List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
            memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
            assertEquals(54, memberDescriptors.size());
        }

    }

    @Test
    public void testLocalReflect7() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addClasspath(getOutputDir());
        cachedASMReflector.addClasspath(getTestOutputDir());
        cachedASMReflector.createClassIndexes();

        {
            String fqcn = "meghanada.Gen1<Long, String>";
            List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
            memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
            assertEquals(16, memberDescriptors.size());

        }

    }

    @Test
    public void testLocalReflect8() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addClasspath(getOutputDir());
        cachedASMReflector.addClasspath(getTestOutputDir());
        cachedASMReflector.createClassIndexes();
        String fqcn = "meghanada.Gen3<Long>";
        List<MemberDescriptor> memberDescriptors = timeIt(() -> cachedASMReflector.reflect(fqcn));
        memberDescriptors.forEach(m -> {
            System.out.println(m.getDeclaration());
        });
        assertEquals(13, memberDescriptors.size());
    }

    @Test
    public void testLocalReflect9() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addClasspath(getOutputDir());
        cachedASMReflector.addClasspath(getTestOutputDir());
        cachedASMReflector.createClassIndexes();

        {
            String fqcn = "meghanada.SelfRef1";
            List<MemberDescriptor> memberDescriptors = traceIt(() -> {
                return cachedASMReflector.reflect(fqcn);
            });
            memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
            assertEquals(13, memberDescriptors.size());
        }
        {
            String fqcn = "meghanada.SelfRef1$Ref";
            List<MemberDescriptor> memberDescriptors = timeIt(() -> {
                return cachedASMReflector.reflect(fqcn);
            });
            memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
            assertEquals(21, memberDescriptors.size());
        }
    }

    @Test
    public void testLocalReflect10() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addClasspath(getOutputDir());
        cachedASMReflector.addClasspath(getTestOutputDir());
        cachedASMReflector.createClassIndexes();

        {
            String fqcn = "meghanada.SelfRef2";
            List<MemberDescriptor> memberDescriptors = traceIt(() -> {
                return cachedASMReflector.reflect(fqcn);
            });
            memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
            assertEquals(13, memberDescriptors.size());
        }
        {
            String fqcn = "meghanada.SelfRef2$Ref";
            List<MemberDescriptor> memberDescriptors = timeIt(() -> {
                return cachedASMReflector.reflect(fqcn);
            });
            memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
            assertEquals(21, memberDescriptors.size());
        }
    }

    @Test
    public void testLocalInterface1() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addClasspath(getOutputDir());
        cachedASMReflector.createClassIndexes();

        {
            String fqcn = "meghanada.reflect.CandidateUnit";
            List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
            memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
            assertEquals(16, memberDescriptors.size());
        }

    }

    @Test
    public void testLocalInterface2() throws Exception {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addClasspath(getOutputDir());
        cachedASMReflector.createClassIndexes();

        {
            String fqcn = "com.google.common.eventbus.SubscriberExceptionHandler";
            List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
            memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
            assertEquals(12, memberDescriptors.size());
        }

    }

    @Ignore
    @org.junit.Test
    public void testCreateCache() throws Exception {
        CachedASMReflector reflector = CachedASMReflector.getInstance();
        File root = new File("/tmp/.meghanada/cache");
        root.mkdirs();

        Stopwatch stopwatch = Stopwatch.createStarted();
        reflector.createCache(getRTJar(), root);
        System.out.println("1st: " + stopwatch.stop());
        stopwatch.reset();

        stopwatch.start();
        reflector.createCache(getRTJar(), root);
        System.out.println("2nd: " + stopwatch.stop());
        stopwatch.reset();

        Files.walkFileTree(root.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
