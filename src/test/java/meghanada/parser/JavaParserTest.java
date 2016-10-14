package meghanada.parser;

import com.github.javaparser.ParseException;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import meghanada.GradleTestBase;
import meghanada.parser.source.JavaSource;
import meghanada.parser.source.TypeScope;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.asm.CachedASMReflector;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static meghanada.config.Config.timeIt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class JavaParserTest extends GradleTestBase {

    @org.junit.BeforeClass
    public static void beforeClass() throws Exception {
        GradleTestBase.setupReflector();
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addClasspath(getOutputDir());
        cachedASMReflector.addClasspath(getTestOutputDir());
        cachedASMReflector.createClassIndexes();
    }

    @Test
    public void testParseClass1() throws Exception {
        JavaSource source = timeIt(() -> {
            JavaParser parser = new JavaParser();
            return parser.parse(new File("./src/main/java/meghanada/watcher/FileSystemWatcher.java"));
        });
        String pkg = source.getPkg();
        List<TypeScope> scopes = source.getTypeScopes();

        {
            TypeScope typeScope = scopes.get(0);
            String type = pkg + "." + typeScope.getType();
            assertEquals("meghanada.watcher.FileSystemWatcher$FileEvent", type);
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            assertEquals(4, result.size());
//            for (MemberDescriptor md : result) {
//                String typeString = md.getType();
//                System.out.println(typeString + ":" + md);
//            }
        }

        {
            TypeScope typeScope = scopes.get(1);
            String type = pkg + "." + typeScope.getType();
            assertEquals("meghanada.watcher.FileSystemWatcher$CreateEvent", type);
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            assertEquals(1, result.size());
//            for (MemberDescriptor md : result) {
//                String typeString = md.getType();
//                System.out.println(typeString + ":" + md);
//            }
        }
        {
            TypeScope typeScope = scopes.get(2);
            String type = pkg + "." + typeScope.getType();
            assertEquals("meghanada.watcher.FileSystemWatcher$ModifyEvent", type);
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            assertEquals(1, result.size());
//            for (MemberDescriptor md : result) {
//                String typeString = md.getType();
//                System.out.println(typeString + ":" + md);
//            }
        }
        {
            TypeScope typeScope = scopes.get(3);
            String type = pkg + "." + typeScope.getType();
            assertEquals("meghanada.watcher.FileSystemWatcher$DeleteEvent", type);
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            assertEquals(1, result.size());
//            for (MemberDescriptor md : result) {
//                String typeString = md.getType();
//                System.out.println(typeString + ":" + md);
//            }
        }
        {
            TypeScope typeScope = scopes.get(4);
            String type = pkg + "." + typeScope.getType();
            assertEquals("meghanada.watcher.FileSystemWatcher$WatchKeyHolder", type);

            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            // result.forEach(System.out::println);

//            for (MemberDescriptor md : result) {
//                String typeString = md.getType();
//                System.out.println(typeString + ":" + md);
//            }
            assertEquals(9, result.size());
        }
        {
            TypeScope typeScope = scopes.get(5);
            String type = pkg + "." + typeScope.getType();
            assertEquals("meghanada.watcher.FileSystemWatcher", type);
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            assertEquals(10, result.size());
//            for (MemberDescriptor md : result) {
//                String typeString = md.getType();
//                System.out.println(typeString + ":" + md);
//            }
        }
    }

    @Test
    public void testParseClass2() throws Exception {
        JavaSource source = timeIt(() -> {
            JavaParser parser = new JavaParser();
            return parser.parse(new File("./src/main/java/meghanada/reflect/asm/ASMReflector.java"));
        });

        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = pkg + "." + typeScope.getType();
            assertEquals("meghanada.reflect.asm.ASMReflector", type);
            assertEquals(28, result.size());
        }
    }

    @Test
    public void testParseClass3() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);
        JavaSource source = parser.parse(new File("./src/main/java/meghanada/compiler/SimpleJavaCompiler.java"));
        assertNotNull(source);
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = pkg + "." + typeScope.getType();
            assertEquals("meghanada.compiler.SimpleJavaCompiler", type);
            assertEquals(10, result.size());
        }
    }

    @Test
    public void testParseClass4() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);
        JavaSource source = parser.parse(new File("./src/main/java/meghanada/project/maven/POMInfo.java"));
        assertNotNull(source);
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = pkg + "." + typeScope.getType();
            assertEquals("meghanada.project.maven.POMInfo", type);
            assertEquals(24, result.size());
        }
    }

    @Test
    public void testParseClass5() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);
        JavaSource source = parser.parse(new File("./src/main/java/meghanada/session/Session.java"));
        assertNotNull(source);
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
            final List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = pkg + "." + typeScope.getType();
            assertEquals("meghanada.session.Session", type);
            assertEquals(56, result.size());
        }
    }

    @Test
    public void testParseClass6() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);
        JavaSource source = parser.parse(new File("./src/main/java/meghanada/parser/source/TypeScope.java"));
        assertNotNull(source);
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = pkg + "." + typeScope.getType();
            assertEquals("meghanada.parser.source.TypeScope", type);
            assertEquals(25, result.size());
        }
    }

    @Test
    public void testParseClass7() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);
        JavaSource source = parser.parse(new File("./src/main/java/meghanada/parser/source/Variable.java"));
        assertNotNull(source);
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = pkg + "." + typeScope.getType();
            assertEquals("meghanada.parser.source.Variable", type);
            assertEquals(16, result.size());
        }
    }

    @Test
    public void testParseClass8() throws Exception {
        JavaSource source = timeIt(() -> {
            JavaParser parser = new JavaParser();
            return parser.parse(new File("./src/main/java/meghanada/parser/source/JavaSource.java"));
        });
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = pkg + "." + typeScope.getType();
            assertEquals("meghanada.parser.source.JavaSource", type);
            assertEquals(42, result.size());
        }
    }

    @Test
    public void testParseClass9() throws Exception {
        JavaSource source = timeIt(() -> {
            JavaParser parser = new JavaParser();
            return parser.parse(new File("./src/main/java/meghanada/parser/JavaSymbolAnalyzeVisitor.java"));
        });
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = typeScope.getFQCN();
            assertEquals("meghanada.parser.JavaSymbolAnalyzeVisitor", type);
            assertEquals(37, result.size());
        }

    }

    @Test
    public void testParseClass10() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);
        JavaSource source = parser.parse(new File("./src/main/java/meghanada/reflect/asm/TypeInfo.java"));
        assertNotNull(source);
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = typeScope.getFQCN();
            assertEquals("meghanada.reflect.asm.TypeInfo", type);
            assertEquals(10, result.size());
        }
    }

    @Test
    public void testParseClass11() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);
        JavaSource source = parser.parse(new File("./src/main/java/meghanada/reflect/names/ParameterNameVisitor.java"));
        assertNotNull(source);
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = typeScope.getFQCN();
            assertEquals("meghanada.reflect.names.ParameterNameVisitor", type);
            assertEquals(10, result.size());
        }
    }

    @Test
    public void testParseClass12() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);
        JavaSource source = parser.parse(new File("./src/main/java/meghanada/reflect/names/ParameterNamesIndexer.java"));
        assertNotNull(source);
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = typeScope.getFQCN();
            assertEquals("meghanada.reflect.names.ParameterNamesIndexer", type);
            assertEquals(8, result.size());
        }
    }

    @Test
    public void testParseClass13() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);
        JavaSource source = parser.parse(new File("./src/main/java/meghanada/reflect/ClassIndex.java"));
        assertNotNull(source);
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = typeScope.getFQCN();
            assertEquals("meghanada.reflect.ClassIndex", type);
            assertEquals(25, result.size());
        }
    }

    @Test
    public void testParseClass14() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);
        JavaSource source = parser.parse(new File("./src/main/java/meghanada/project/gradle/GradleProject.java"));
        assertNotNull(source);
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = typeScope.getFQCN();
            assertEquals("meghanada.project.gradle.GradleProject", type);
            assertEquals(9, result.size());
        }
    }

    @Test
    public void testParseClass15() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);
        JavaSource source = parser.parse(new File("./src/main/java/meghanada/project/maven/POMParser.java"));
        assertNotNull(source);
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
//            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
//            String type = typeScope.getFQCN();
//            assertEquals("meghanada.project.maven.POMParser", type);
//            assertEquals(9, result.size());
//            System.out.println(typeScope);
        }
    }

    @Test
    public void testParseClass16() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);
        final JavaSource source = timeIt(() -> parser.parse(new File("./src/main/java/meghanada/server/emacs/EmacsServer.java")));
        assertNotNull(source);
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
//            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
//            String type = typeScope.getFQCN();
//            assertEquals("meghanada.project.maven.POMParser", type);
//            assertEquals(9, result.size());
//            System.out.println(typeScope);
        }
    }

    @Test
    public void testParseClass17() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);
        JavaSource source = parser.parse(new File("src/main/java/meghanada/reflect/asm/ClassSignatureVisitor.java"));
        assertNotNull(source);
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
//            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
//            String type = typeScope.getFQCN();
//            assertEquals("meghanada.project.maven.POMParser", type);
//            assertEquals(9, result.size());
//            System.out.println(typeScope);
        }
    }

    @Test
    public void testParseClass18() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);
        JavaSource source = parser.parse(new File("src/main/java/meghanada/reflect/asm/MethodAnalyzeVisitor.java"));
        assertNotNull(source);
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
//            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
//            String type = typeScope.getFQCN();
//            assertEquals("meghanada.project.maven.POMParser", type);
//            assertEquals(9, result.size());
//            System.out.println(typeScope);
        }
    }

    @Test
    public void testParseClass19() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);
        JavaSource source = parser.parse(new File("src/main/java/meghanada/junit/TestRunner.java"));
        assertNotNull(source);
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
//            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
//            String type = typeScope.getFQCN();
//            assertEquals("meghanada.project.maven.POMParser", type);
//            assertEquals(9, result.size());
//            System.out.println(typeScope);
        }
    }

    @Test
    public void testParseClass20() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);
        JavaSource source = parser.parse(new File("src/main/java/meghanada/utils/ClassNameUtils.java"));
        assertNotNull(source);
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
//            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
//            String type = typeScope.getFQCN();
//            assertEquals("meghanada.project.maven.POMParser", type);
//            assertEquals(9, result.size());
//            System.out.println(typeScope);
        }
    }

    @Test
    public void testParseClass21() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);
        JavaSource source = parser.parse(new File("src/main/java/meghanada/session/SessionEventBus.java"));
        assertNotNull(source);
        String pkg = source.getPkg();
        System.out.println(source.getTypeScopes().size());
        final List<TypeScope> scopes = source.getTypeScopes();
        {
            String fqcn = scopes.get(0).getFQCN();
            assertEquals("meghanada.session.SessionEventBus$IORequest", fqcn);
        }
        {
            String fqcn = scopes.get(1).getFQCN();
            assertEquals("meghanada.session.SessionEventBus$IOListRequest", fqcn);
        }
        {
            String fqcn = scopes.get(2).getFQCN();
            assertEquals("meghanada.session.SessionEventBus$ClassCacheRequest", fqcn);
        }
        {
            String fqcn = scopes.get(3).getFQCN();
            assertEquals("meghanada.session.SessionEventBus$CompileRequest", fqcn);
        }
    }

    @Test
    public void testParseClass22() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);

        final Stopwatch stopwatch = Stopwatch.createStarted();
        JavaSource source = parser.parse(new File("./src/main/java/meghanada/parser/FQCNResolver.java"));
        System.out.println(stopwatch.stop());

        assertNotNull(source);
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = typeScope.getFQCN();
            assertEquals("meghanada.parser.FQCNResolver", type);
            assertEquals(24, result.size());
        }

    }

    @Test
    public void testParseClass23() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);

        final Stopwatch stopwatch = Stopwatch.createStarted();
        JavaSource source = parser.parse(new File("./src/main/java/meghanada/location/LocationSearcher.java"));
        System.out.println(stopwatch.stop());

        assertNotNull(source);
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = typeScope.getFQCN();
            assertEquals("meghanada.location.LocationSearcher", type);
            assertEquals(13, result.size());
        }

    }

    @Test
    public void testParseClass24() throws Exception {
        JavaSource source = timeIt(() -> {
            JavaParser javaParser = new JavaParser();
            return javaParser.parse(new File("./src/main/java/meghanada/completion/JavaCompletion.java"));
        });
        assertNotNull(source);
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = typeScope.getFQCN();
            assertEquals("meghanada.completion.JavaCompletion", type);
            assertEquals(22, result.size());
        }
    }

    @Test
    public void testParseClass25() throws Exception {//
        JavaSource source = timeIt(() -> {
            JavaParser javaParser = new JavaParser();
            return javaParser.parse(new File("./src/test/java/meghanada/Enum1.java"));
        });
        assertNotNull(source);
        String pkg = source.getPkg();
        TypeScope typeScope = source.getTypeScopes().get(0);
        List<MemberDescriptor> result = typeScope.getMemberDescriptors();
        String type = typeScope.getFQCN();
        assertEquals("meghanada.Enum1$KeyType$1", type);
    }

    @Test
    public void testParseClass26() throws Exception {//
        JavaSource source = timeIt(() -> {
            JavaParser javaParser = new JavaParser();
            return javaParser.parse(new File("./src/test/java/meghanada/Gen4.java"));
        });
        assertNotNull(source);
        TypeScope typeScope = source.getTypeScopes().get(0);
        List<MemberDescriptor> result = typeScope.getMemberDescriptors();
        String type = typeScope.getFQCN();
        assertEquals("meghanada.Gen4", type);
        assertEquals(5, result.size());
    }

    @Test
    public void testParseClass27() throws Exception {//
        JavaSource source = timeIt(() -> {
            JavaParser javaParser = new JavaParser();
            return javaParser.parse(new File("./src/test/java/meghanada/CallGen5.java"));
        });
        assertNotNull(source);
        TypeScope typeScope = source.getTypeScopes().get(0);
        List<MemberDescriptor> result = typeScope.getMemberDescriptors();
        String type = typeScope.getFQCN();
        assertEquals("meghanada.CallGen5", type);
        assertEquals(1, result.size());
    }

    @Test
    public void testParseClass28() throws Exception {//
        JavaSource source = timeIt(() -> {
            JavaParser javaParser = new JavaParser();
            return javaParser.parse(new File("./src/test/java/meghanada/Gen6.java"));
        });
        assertNotNull(source);
        TypeScope typeScope = source.getTypeScopes().get(0);
        List<MemberDescriptor> result = typeScope.getMemberDescriptors();
        String type = typeScope.getFQCN();
        assertEquals("meghanada.Gen6", type);
        assertEquals(1, result.size());
    }

    @Test
    public void testParseClass29() throws Exception {//
        JavaSource source = timeIt(() -> {
            JavaParser javaParser = new JavaParser();
            return javaParser.parse(new File("./src/test/java/meghanada/StaticImp1.java"));
        });

        assertNotNull(source);
        TypeScope typeScope = source.getTypeScopes().get(0);
        List<MemberDescriptor> result = typeScope.getMemberDescriptors();
        String type = typeScope.getFQCN();
        assertEquals("meghanada.StaticImp1", type);
        assertEquals(1, result.size());
    }

    @Test
    public void testParseClass30() throws Exception {//
        JavaSource source = timeIt(() -> {
            JavaParser javaParser = new JavaParser();
            return javaParser.parse(new File("./src/test/java/meghanada/Variable1.java"));
        });
        assertNotNull(source);
        TypeScope typeScope = source.getTypeScopes().get(0);
        List<MemberDescriptor> result = typeScope.getMemberDescriptors();
        String type = typeScope.getFQCN();
        assertEquals("meghanada.Variable1", type);
        assertEquals(2, result.size());
    }

    @Test
    public void testParseClass31() throws Exception {//
        JavaSource source = timeIt(() -> {
            JavaParser javaParser = new JavaParser();
            return javaParser.parse(new File("./src/test/java/meghanada/Gen7.java"));
        });
        assertNotNull(source);
        TypeScope typeScope = source.getTypeScopes().get(0);
        List<MemberDescriptor> result = typeScope.getMemberDescriptors();
        String type = typeScope.getFQCN();
        assertEquals("meghanada.Gen7", type);
        assertEquals(1, result.size());
    }

    @Test
    public void testParseClass32() throws Exception {//
        JavaSource source = timeIt(() -> {
            JavaParser javaParser = new JavaParser();
            return javaParser.parse(new File("./src/test/java/meghanada/Gen8.java"));
        });
        assertNotNull(source);
        TypeScope typeScope = source.getTypeScopes().get(0);
        List<MemberDescriptor> result = typeScope.getMemberDescriptors();
        String type = typeScope.getFQCN();
        assertEquals("meghanada.Gen8", type);
        assertEquals(2, result.size());
    }

    @Test
    public void testParseClass33() throws Exception {//
        JavaSource source = timeIt(() -> {
            JavaParser javaParser = new JavaParser();
            return javaParser.parse(new File("./src/test/java/meghanada/Gen9.java"));
        });
        assertNotNull(source);
        TypeScope typeScope = source.getTypeScopes().get(0);
        List<MemberDescriptor> result = typeScope.getMemberDescriptors();
        String type = typeScope.getFQCN();
        assertEquals("meghanada.Gen9$A", type);
        assertEquals(3, result.size());
    }

    @Test
    public void testParseClass34() throws Exception {//
        JavaSource source = timeIt(() -> {
            JavaParser javaParser = new JavaParser();
            return javaParser.parse(new File("./src/test/java/meghanada/Gen10.java"));
        });
        assertNotNull(source);
        TypeScope typeScope = source.getTypeScopes().get(0);
        List<MemberDescriptor> result = typeScope.getMemberDescriptors();
        String type = typeScope.getFQCN();
        assertEquals("meghanada.Gen10", type);
        assertEquals(2, result.size());
    }

    @Test
    public void testParseClass35() throws Exception {//
        JavaSource source = timeIt(() -> {
            JavaParser javaParser = new JavaParser();
            return javaParser.parse(new File("./src/test/java/meghanada/Gen11.java"));
        });
        assertNotNull(source);
        TypeScope typeScope = source.getTypeScopes().get(0);
        List<MemberDescriptor> result = typeScope.getMemberDescriptors();
        String type = typeScope.getFQCN();
        assertEquals("meghanada.Gen11", type);
        assertEquals(2, result.size());
    }

    @Test
    public void testParseClass36() throws Exception {//
        JavaSource source = timeIt(() -> {
            JavaParser javaParser = new JavaParser();
            return javaParser.parse(new File("./src/test/java/meghanada/SelfRef1.java"));
        });
        assertNotNull(source);
        TypeScope typeScope = source.getTypeScopes().get(0);
        List<MemberDescriptor> result = typeScope.getMemberDescriptors();
        String type = typeScope.getFQCN();
        assertEquals("meghanada.SelfRef1$Ref", type);
        assertEquals(3, result.size());
    }

    @Test
    public void testParseClass37() throws Exception {//
        JavaSource source = timeIt(() -> {
            JavaParser javaParser = new JavaParser();
            return javaParser.parse(new File("./src/test/java/meghanada/GenArray1.java"));
        });
        assertNotNull(source);
        TypeScope typeScope = source.getTypeScopes().get(0);
        List<MemberDescriptor> result = typeScope.getMemberDescriptors();
        String type = typeScope.getFQCN();
        assertEquals("meghanada.GenArray1", type);
        assertEquals(2, result.size());
    }

    @Test
    public void testParseAll() throws Exception {
        JavaParser parser = new JavaParser();

        final Stopwatch stopwatch = Stopwatch.createStarted();
        Files.walk(new File("./src/main/java/").toPath())
                .map(Path::toFile)
                .filter(JavaSource::isJavaFile)
                .forEach(file -> {
                    try {
                        System.out.println(file.getCanonicalPath());
                        System.out.println(Strings.repeat("-", 120));
                        parser.parse(file);
                        System.out.println(Strings.repeat("-", 120));
                    } catch (IOException | ParseException e) {
                        e.printStackTrace();
                    }
                });
        System.out.println("1st: " + stopwatch.stop());
    }

    @Test
    public void testParseInterface1() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);
        JavaSource source = parser.parse(new File("./src/main/java/meghanada/reflect/CandidateUnit.java"));
        assertNotNull(source);
        String pkg = source.getPkg();
        System.out.println(source.getTypeScopes());

        List<TypeScope> typeScopes = source.getTypeScopes();
        TypeScope typeScope = typeScopes.get(0);
        List<MemberDescriptor> result = new ArrayList<>();
        for (MemberDescriptor md : typeScope.getMemberDescriptors()) {
            String declaration = md.getDeclaration();
            if (!declaration.contains("private ")) {
                result.add(md);
            }
        }
        String type = pkg + "." + typeScope.getType();
        assertEquals("meghanada.reflect.CandidateUnit$MemberType$FIELD", type);
        assertEquals(0, result.size());

        typeScope = typeScopes.get(typeScopes.size() - 1);
        result = new ArrayList<>();
        for (MemberDescriptor md : typeScope.getMemberDescriptors()) {
            String declaration = md.getDeclaration();
            if (!declaration.contains("private ")) {
                result.add(md);
            }
        }

        type = pkg + "." + typeScope.getType();
        assertEquals("meghanada.reflect.CandidateUnit", type);
        assertEquals(5, result.size());

    }

    @Test
    public void testParseInterface2() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);
        JavaSource source = parser.parse(new File("./src/main/java/meghanada/location/LocationSearchFunction.java"));
        assertNotNull(source);
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
//            List<MemberDescriptor> result = new ArrayList<>();
//            for (MemberDescriptor md : typeScope.getMemberDescriptors()) {
//                String declaration = md.getDeclaration();
//                if (!declaration.matchCallEnd("private ")) {
//                    result.add(md);
//                }
//            }
            String type = pkg + "." + typeScope.getType();
            assertEquals("meghanada.location.LocationSearchFunction", type);
            // assertEquals(6, result.size());
        }
    }

    @Test
    public void testAbstract1() throws Exception {
        JavaSource source = timeIt(() -> {
            JavaParser parser = new JavaParser();
            return parser.parse(new File("./src/main/java/meghanada/project/Project.java"));
        });
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = pkg + "." + typeScope.getType();
            assertEquals("meghanada.project.Project", type);
            assertEquals(65, result.size());
//            for (MemberDescriptor md : result) {
//                String typeString = md.getType();
//                System.out.println(typeString + ":" + md);
//            }
        }
    }

    @Test
    public void testParseFile1() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);
        JavaSource source = parser.parse(new File("src/test/resources/meghanada/Method.java"));
        assertNotNull(source);
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
            String type = typeScope.getFQCN();
            assertEquals("meghanada.Method", type);
        }
    }

    @Test
    public void testParseFile2() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);
        JavaSource source = parser.parse(new File("src/test/resources/meghanada/CodeGen.java"));
        assertNotNull(source);
        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
            String type = typeScope.getFQCN();
            assertEquals("meghanada.CodeGen", type);
        }
    }

    @Test
    public void testParseFile3() throws Exception {
        JavaParser parser = new JavaParser();
        // Config.load().setDebug();
        JavaSource source = parser.parse(new File("src/test/resources/Generics1.java"));

        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = typeScope.getFQCN();
            assertEquals("Generics1", type);
            assertEquals(3, result.size());
        }
    }

    @Test
    public void testParseFile4() throws Exception {
        JavaSource source = timeIt(() -> {
            JavaParser parser = new JavaParser();
            return parser.parse(new File("src/test/resources/Generics2.java"));
        });

        String pkg = source.getPkg();
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = typeScope.getFQCN();
            assertEquals("Generics2", type);
            assertEquals(1, result.size());
        }
    }

    @Test
    public void testParseLambda1() throws Exception {
        JavaSource source = timeIt(() -> {
            JavaParser parser = new JavaParser();
            return parser.parse(new File("src/test/resources/Lambda1.java"));
        });
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = typeScope.getType();
            assertEquals("Lambda1", type);
            assertEquals(1, result.size());
        }
    }

    @Test
    public void testParseLambda2() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);
        final Stopwatch stopwatch = Stopwatch.createStarted();
        JavaSource source = parser.parse(new File("src/test/resources/Lambda2.java"));
        assertNotNull(source);
        System.out.println(stopwatch.stop());
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = typeScope.getType();
            assertEquals("Lambda2", type);
            assertEquals(1, result.size());
        }
    }


    @Test
    public void testParseLambda3() throws Exception {
        JavaParser parser = new JavaParser();
        assertNotNull(parser);
        final Stopwatch stopwatch = Stopwatch.createStarted();
        JavaSource source = parser.parse(new File("src/test/resources/Lambda3.java"));
        assertNotNull(source);
        System.out.println(stopwatch.stop());
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = typeScope.getType();
            assertEquals("Lambda3", type);
            assertEquals(2, result.size());
        }
    }

    @Test
    @Ignore
    public void testParseLambda4() throws Exception {
        JavaSource source = timeIt(() -> {
            JavaParser parser = new JavaParser();
            return parser.parse(new File("src/test/resources/Lambda4.java"));
        });
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = typeScope.getType();
            assertEquals("Lambda4", type);
            assertEquals(2, result.size());
        }
    }

    @Test
    public void testParseLambda5() throws Exception {
        JavaSource source = timeIt(() -> {
            JavaParser parser = new JavaParser();
            return parser.parse(new File("src/test/resources/Lambda5.java"));
        });
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = typeScope.getType();
            assertEquals("Lambda5", type);
            assertEquals(1, result.size());
        }
    }

    @Test
    public void testParseImport1() throws Exception {
        JavaParser parser = new JavaParser();
        final Stopwatch stopwatch = Stopwatch.createStarted();
        JavaSource source = parser.parse(new File("src/test/resources/Import1.java"));
        System.out.println(stopwatch.stop());
        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = typeScope.getType();
            assertEquals("Import1", type);
            assertEquals(1, result.size());
        }
    }

    @Test
    public void testParseSimple1() throws Exception {
        JavaSource source = timeIt(() -> {
            JavaParser parser = new JavaParser();
            return parser.parse(new File("src/test/resources/Simple1.java"));
        });

        for (TypeScope typeScope : source.getTypeScopes()) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            String type = typeScope.getType();
            assertEquals("Simple1", type);
            assertEquals(4, result.size());
        }

    }

}
