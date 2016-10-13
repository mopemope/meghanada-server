package meghanada.reflect.asm;

import com.google.common.base.Stopwatch;
import meghanada.GradleTestBase;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.MethodDescriptor;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static meghanada.config.Config.debugIt;
import static meghanada.config.Config.timeIt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ASMReflectorTest extends GradleTestBase {

    @Test
    public void testGetInstance() throws Exception {
        ASMReflector asmReflector = ASMReflector.getInstance();
        assertNotNull(asmReflector);
    }

    @Test
    public void testGetClasses1() throws Exception {
        File jar = getJar("junit");
        ASMReflector asmReflector = ASMReflector.getInstance();
        Map<ClassIndex, File> classIndex = timeIt(() -> asmReflector.getClasses(jar));
        assertEquals(189, classIndex.size());
//        classIndex.forEach((classIndex1, file) -> System.out.println(classIndex1));
    }

    @Test
    public void testGetClasses2() throws Exception {
        File jar = getRTJar();
        ASMReflector asmReflector = ASMReflector.getInstance();
        Map<ClassIndex, File> classIndex = timeIt(() -> asmReflector.getClasses(jar));
        assertEquals(4105, classIndex.size());
//        classIndex.forEach((classIndex1, file) -> System.out.println(classIndex1));
    }

    @Test
    public void testReflectInner1() throws Exception {
        ASMReflector asmReflector = ASMReflector.getInstance();
        Stopwatch stopwatch = Stopwatch.createUnstarted();
        {
            String fqcn = "java.util.Map$Entry";
            File jar = getRTJar();
            Map<ClassIndex, File> index = asmReflector.getClasses(jar);
            final InheritanceInfo info = asmReflector.getReflectInfo(index, fqcn);
            stopwatch.start();
            System.out.println(info);
            List<MemberDescriptor> memberDescriptors = asmReflector.reflectAll(info);
            System.out.println(stopwatch.stop());
            assertEquals(18, memberDescriptors.size());
            stopwatch.reset();
        }
    }

    @Test
    public void testReflectInner2() throws Exception {
        ASMReflector asmReflector = ASMReflector.getInstance();
        Stopwatch stopwatch = Stopwatch.createUnstarted();
        {
            String fqcn = "java.util.Map.Entry";
            File jar = getRTJar();
            Map<ClassIndex, File> index = asmReflector.getClasses(jar);
            final InheritanceInfo info = asmReflector.getReflectInfo(index, fqcn);

            System.out.println(info);

            stopwatch.start();
            List<MemberDescriptor> memberDescriptors = asmReflector.reflectAll(info);
            System.out.println(stopwatch.stop());

            memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
            assertEquals(18, memberDescriptors.size());
            stopwatch.reset();
        }

    }

    @Test
    public void testReflectWithGenerics1() throws Exception {
        ASMReflector asmReflector = ASMReflector.getInstance();
        Stopwatch stopwatch = Stopwatch.createUnstarted();
        {
            String fqcn = "java.util.Map";
            File jar = getRTJar();
            Map<ClassIndex, File> index = asmReflector.getClasses(jar);
            final InheritanceInfo info = asmReflector.getReflectInfo(index, fqcn);

            stopwatch.start();
            List<MemberDescriptor> memberDescriptors = asmReflector.reflectAll(info);
            System.out.println(stopwatch.stop());
            memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
            assertEquals(34, memberDescriptors.size());
            stopwatch.reset();

            memberDescriptors.stream()
                    .filter(memberDescriptor -> memberDescriptor.getName().equals("entrySet"))
                    .forEach(memberDescriptor -> {
                        if (memberDescriptor instanceof MethodDescriptor) {
                            MethodDescriptor methodDescriptor = (MethodDescriptor) memberDescriptor;
                            methodDescriptor.getTypeParameterMap().put("K", "String");
                            methodDescriptor.getTypeParameterMap().put("V", "Long");
                            System.out.println(memberDescriptor.getReturnType());
                            assertEquals("java.util.Set<java.util.Map.Entry<String, Long>>", memberDescriptor.getReturnType());
                        }
                    });
        }
    }

    @Test
    public void testReflectWithGenerics2() throws Exception {
        ASMReflector asmReflector = ASMReflector.getInstance();
        Stopwatch stopwatch = Stopwatch.createUnstarted();
        {
            String fqcn = "java.util.Enumeration<? extends ZipEntry>";
            File jar = getRTJar();
            Map<ClassIndex, File> index = asmReflector.getClasses(jar);
            final InheritanceInfo info = asmReflector.getReflectInfo(index, fqcn);

            stopwatch.start();
            List<MemberDescriptor> memberDescriptors = asmReflector.reflectAll(info);
            System.out.println(stopwatch.stop());
            memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
            assertEquals(13, memberDescriptors.size());
            stopwatch.reset();

        }
    }

    @Test
    public void testReflectWithGenerics3() throws Exception {
        ASMReflector asmReflector = ASMReflector.getInstance();
        Stopwatch stopwatch = Stopwatch.createUnstarted();
        {
            String fqcn = "java.util.Map<? extends String, ? extends Long>";
            File jar = getRTJar();
            Map<ClassIndex, File> index = asmReflector.getClasses(jar);
            final InheritanceInfo info = asmReflector.getReflectInfo(index, fqcn);

            stopwatch.start();
            List<MemberDescriptor> memberDescriptors = asmReflector.reflectAll(info);
            System.out.println(stopwatch.stop());
            memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
            assertEquals(34, memberDescriptors.size());
            stopwatch.reset();

        }
    }

    @Test
    public void testReflectWithGenerics4() throws Exception {
        ASMReflector asmReflector = ASMReflector.getInstance();
        Stopwatch stopwatch = Stopwatch.createUnstarted();
        {
            String fqcn = "com.google.common.cache.CacheBuilder<Object, Object>";
            File jar = getJar("guava");
            Map<ClassIndex, File> index = asmReflector.getClasses(jar);
            final InheritanceInfo info = asmReflector.getReflectInfo(index, fqcn);

            stopwatch.start();
            List<MemberDescriptor> memberDescriptors = asmReflector.reflectAll(info);
            System.out.println(stopwatch.stop());
            memberDescriptors.forEach(m -> {
                System.out.println(m.getDeclaration());
                // System.out.println("Return: " + m.getRawReturnType());
            });
            assertEquals(61, memberDescriptors.size());
            stopwatch.reset();
        }
    }

    @Test
    public void testReflectAll1() throws Exception {
        ASMReflector asmReflector = ASMReflector.getInstance();
        Stopwatch stopwatch = Stopwatch.createUnstarted();
        {
            File jar = getRTJar();
            Map<ClassIndex, File> index = asmReflector.getClasses(jar);

            String fqcn = "java.util.stream.Stream<java.util.List<java.lang.String>>";
            final InheritanceInfo info = asmReflector.getReflectInfo(index, fqcn);

            stopwatch.start();
            List<MemberDescriptor> memberDescriptors1 = asmReflector.reflectAll(info);
            System.out.println(stopwatch.stop());
            System.out.println(memberDescriptors1.size());
            memberDescriptors1.forEach(md -> {
                System.out.println(md.getDeclaration());
            });
            stopwatch.reset();
            stopwatch.start();
            List<MemberDescriptor> memberDescriptors2 = asmReflector.reflectAll(info);
            System.out.println(stopwatch.stop());
            System.out.println(memberDescriptors2.size());

        }
    }

    @Test
    public void testReflectAll2() throws Exception {
        ASMReflector asmReflector = ASMReflector.getInstance();
        Stopwatch stopwatch = Stopwatch.createUnstarted();
        {
            File jar = getRTJar();
            Map<ClassIndex, File> index = asmReflector.getClasses(jar);

            String fqcn = "java.lang.String";
            final InheritanceInfo info = asmReflector.getReflectInfo(index, fqcn);
            System.out.println(info);
            stopwatch.start();
            List<MemberDescriptor> md = asmReflector.reflectAll(info);
            System.out.println(stopwatch.stop());
            assertEquals(100, md.size());
        }
    }

    @Test
    public void testReflectAll3() throws Exception {
        ASMReflector asmReflector = ASMReflector.getInstance();
        {
            File jar = getRTJar();
            Map<ClassIndex, File> index = asmReflector.getClasses(jar);

            String fqcn = "java.util.List";
            final InheritanceInfo info = asmReflector.getReflectInfo(index, fqcn);
            System.out.println(info);
            List<MemberDescriptor> memberDescriptors1 = debugIt(() -> {
                return asmReflector.reflectAll(info);
            });
            memberDescriptors1.sort((o1, o2) -> o1.getName().compareTo(o2.getName()));
            memberDescriptors1.forEach(memberDescriptor -> {
                System.out.println(memberDescriptor.getDisplayDeclaration());
            });
            assertEquals(41, memberDescriptors1.size());
        }
    }

    @Test
    public void testReflectAll4() throws Exception {
        ASMReflector asmReflector = ASMReflector.getInstance();
        Stopwatch stopwatch = Stopwatch.createUnstarted();
        {
            File jar = getRTJar();
            Map<ClassIndex, File> index = asmReflector.getClasses(jar);

            String fqcn = "java.util.function.Predicate";
            final InheritanceInfo info = asmReflector.getReflectInfo(index, fqcn);
            System.out.println(info);
            stopwatch.start();
            List<MemberDescriptor> memberDescriptors = asmReflector.reflectAll(info);
            System.out.println(stopwatch.stop());
            assertEquals(16, memberDescriptors.size());
            memberDescriptors.forEach(memberDescriptor -> System.out.println(memberDescriptor.getDeclaration()));
        }

    }

    @Test
    public void testReflectAll5() throws Exception {
        ASMReflector asmReflector = ASMReflector.getInstance();
        Stopwatch stopwatch = Stopwatch.createUnstarted();
        // Config.load().setDebug();
        {
            File jar = getRTJar();
            Map<ClassIndex, File> index = asmReflector.getClasses(jar);

            // Config.load().setDebug();
            String fqcn = "java.util.stream.Stream<java.util.stream.Stream<java.util.List<java.lang.String>>>";
            final InheritanceInfo info = asmReflector.getReflectInfo(index, fqcn);
            System.out.println(info);
            stopwatch.start();
            List<MemberDescriptor> memberDescriptors1 = asmReflector.reflectAll(info);
            System.out.println(stopwatch.stop());
            System.out.println(memberDescriptors1.size());
            memberDescriptors1.forEach(md -> {
                // System.out.println(md.getDeclaration());
                // System.out.println(md.declaration);
            });

        }
    }

    @Test
    public void testReflectAll6() throws Exception {
        ASMReflector asmReflector = ASMReflector.getInstance();
        Stopwatch stopwatch = Stopwatch.createUnstarted();
        {
            File jar = getRTJar();
            Map<ClassIndex, File> index = asmReflector.getClasses(jar);

            String fqcn = "java.util.jar.JarFile";
            final InheritanceInfo info = asmReflector.getReflectInfo(index, fqcn);
            System.out.println(info);
            stopwatch.start();
            List<MemberDescriptor> memberDescriptors1 = asmReflector.reflectAll(info);
            System.out.println(stopwatch.stop());
            System.out.println(memberDescriptors1.size());
            memberDescriptors1.forEach(md -> {
                System.out.println(md.getDeclaration());
                // System.out.println(md.declaration);
            });

        }
    }

    @Test
    public void testReflectAll7() throws Exception {
        ASMReflector asmReflector = ASMReflector.getInstance();
        Stopwatch stopwatch = Stopwatch.createUnstarted();
        {
            File jar = getJar("guava");
            Map<ClassIndex, File> index = asmReflector.getClasses(jar);

            String fqcn = "com.google.common.base.Joiner";
            final InheritanceInfo info = asmReflector.getReflectInfo(index, fqcn);
            System.out.println(info);
            stopwatch.start();
            List<MemberDescriptor> memberDescriptors1 = asmReflector.reflectAll(info);
            System.out.println(stopwatch.stop());
            System.out.println(memberDescriptors1.size());
            memberDescriptors1.forEach(md -> {
                System.out.println(md.getDeclaration());
                // System.out.println(md.declaration);
            });

        }
    }

    @Test
    public void testReflectAll8() throws Exception {
        final ASMReflector asmReflector = ASMReflector.getInstance();
        final File file = getTestOutputDir();
        final Map<ClassIndex, File> index = asmReflector.getClasses(file);
        final String fqcn = "meghanada.Gen4";
        final List<MemberDescriptor> memberDescriptors = debugIt(() -> {
            final InheritanceInfo info = asmReflector.getReflectInfo(index, fqcn);
            return asmReflector.reflectAll(info);
        });

        memberDescriptors.forEach(md -> {
            System.out.println(md.getDeclaringClass() + " # " + md.getDeclaration());
        });
    }

    @Test
    public void testGetReflectClass1() throws Exception {
        ASMReflector asmReflector = ASMReflector.getInstance();
        Stopwatch stopwatch = Stopwatch.createUnstarted();
        {
            String fqcn = "java.util.Map";
            File jar = getRTJar();
            Map<ClassIndex, File> index = asmReflector.getClasses(jar);

            stopwatch.start();
            final InheritanceInfo info = asmReflector.getReflectInfo(index, fqcn);
            System.out.println(stopwatch.stop());
            System.out.println(info);
        }
    }

    @Test
    public void testGetReflectClass2() throws Exception {
        ASMReflector asmReflector = ASMReflector.getInstance();
        Stopwatch stopwatch = Stopwatch.createUnstarted();
        {
            String fqcn = "java.util.stream.Stream";
            File jar = getRTJar();

            Map<ClassIndex, File> index = asmReflector.getClasses(jar);

            stopwatch.start();
            final InheritanceInfo info1 = asmReflector.getReflectInfo(index, fqcn);
            System.out.println(stopwatch.stop());
            System.out.println(info1);

            stopwatch.reset();
            stopwatch.start();
            final InheritanceInfo info2 = asmReflector.getReflectInfo(index, fqcn);
            System.out.println(stopwatch.stop());
            System.out.println(info2);
        }
    }

    @Test
    public void testReflectInterface1() throws Exception {
        ASMReflector asmReflector = ASMReflector.getInstance();
        Stopwatch stopwatch = Stopwatch.createUnstarted();
        {
            String fqcn = "com.google.common.eventbus.SubscriberExceptionHandler";
            File jar = getJar("guava");
            Map<ClassIndex, File> index = asmReflector.getClasses(jar);
            final InheritanceInfo info = asmReflector.getReflectInfo(index, fqcn);

            stopwatch.start();
            List<MemberDescriptor> memberDescriptors = asmReflector.reflectAll(info);
            System.out.println(stopwatch.stop());
            memberDescriptors.forEach(m -> {
                System.out.println(m.getDeclaration());
                System.out.println("Return: " + m.getRawReturnType());
            });
            assertEquals(1, memberDescriptors.size());
            stopwatch.reset();
        }
    }

    @Test
    public void testReflectAnnotation1() throws Exception {
        ASMReflector asmReflector = ASMReflector.getInstance();
        Stopwatch stopwatch = Stopwatch.createUnstarted();
        {
            String fqcn = "org.junit.Test";
            File jar = getJar("junit");
            stopwatch.start();
            List<MemberDescriptor> memberDescriptors = timeIt(() -> {
                Map<ClassIndex, File> index = asmReflector.getClasses(jar);
                index.keySet().forEach(classIndex -> {
                    if (classIndex.isAnnotation) {
                        System.out.println(classIndex);
                    }
                });
                final InheritanceInfo info = asmReflector.getReflectInfo(index, fqcn);
                return asmReflector.reflectAll(info);
            });
            System.out.println(stopwatch.stop());
            memberDescriptors.forEach(m -> {
                System.out.println(m.getDeclaration());
            });
            assertEquals(2, memberDescriptors.size());
            stopwatch.reset();
        }
    }

}
