package meghanada.completion;

import com.github.javaparser.Range;
import com.google.common.cache.CacheBuilder;
import meghanada.GradleTestBase;
import meghanada.parser.source.MethodCallSymbol;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.session.JavaSourceLoader;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

import static meghanada.config.Config.*;
import static org.junit.Assert.assertEquals;

public class JavaVariableCompletionTest extends GradleTestBase {

    @BeforeClass
    public static void beforeClass() throws Exception {
        GradleTestBase.setupReflector();
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addClasspath(getOutputDir());
        cachedASMReflector.createClassIndexes();
    }

    @Test
    public void localVariable() throws Exception {
        JavaVariableCompletion completion = getCompilation();
        File file = new File("./src/main/java/meghanada/compiler/SimpleJavaCompiler.java");

        LocalVariable lv = timeIt(() -> {
            return completion.localVariable(file, 46);
        });
        System.out.println(lv);
    }

    @Test
    public void createLocalVariable1() throws Exception {
        JavaVariableCompletion completion = getCompilation();
        File file = new File("./src/main/java/meghanada/compiler/SimpleJavaCompiler.java");
        LocalVariable lv = debugIt(() -> {
            final Range range = Range.range(0, 0, 0, 0);
            final Range nameRange = Range.range(0, 0, 0, 0);
            final MethodCallSymbol mcs = new MethodCallSymbol("", "mkdir", range, nameRange, "");
            return completion.createLocalVariable(mcs, "boolean");
        });
        System.out.println(lv);
        assertEquals(lv.getCandidates().size(), 2);
    }

    @Test
    public void createLocalVariable2() throws Exception {
        JavaVariableCompletion completion = getCompilation();
        File file = new File("./src/main/java/meghanada/compiler/SimpleJavaCompiler.java");
        LocalVariable lv = traceIt(() -> {
            final Range range = Range.range(0, 0, 0, 0);
            final Range nameRange = Range.range(0, 0, 0, 0);
            final MethodCallSymbol mcs = new MethodCallSymbol("file", "getFileName", range, nameRange, "");
            return completion.createLocalVariable(mcs, "String");
        });
        System.out.println(lv);
    }

    @Test
    public void createLocalVariable3() throws Exception {
        JavaVariableCompletion completion = getCompilation();
        File file = new File("./src/main/java/meghanada/compiler/SimpleJavaCompiler.java");
        LocalVariable lv = timeIt(() -> {
            final Range range = Range.range(0, 0, 0, 0);
            final Range nameRange = Range.range(0, 0, 0, 0);
            final MethodCallSymbol mcs = new MethodCallSymbol("list", "subList", range, nameRange, "");
            return completion.createLocalVariable(mcs, "java.util.List<String>");
        });
        System.out.println(lv);
    }

    @Test
    public void createLocalVariable4() throws Exception {
        JavaVariableCompletion completion = getCompilation();
        File file = new File("./src/main/java/meghanada/compiler/SimpleJavaCompiler.java");
        LocalVariable lv = timeIt(() -> {
            final Range range = Range.range(0, 0, 0, 0);
            final Range nameRange = Range.range(0, 0, 0, 0);
            final MethodCallSymbol mcs = new MethodCallSymbol("list", "subList", range, nameRange, "");
            return completion.createLocalVariable(mcs, "java.util.List<MemberDescriptor>");
        });
        System.out.println(lv);
    }

    @Test
    public void createLocalVariable5() throws Exception {
        JavaVariableCompletion completion = getCompilation();
        File file = new File("./src/main/java/meghanada/compiler/SimpleJavaCompiler.java");
        LocalVariable lv = timeIt(() -> {
            final Range range = Range.range(0, 0, 0, 0);
            final Range nameRange = Range.range(0, 0, 0, 0);
            final MethodCallSymbol mcs = new MethodCallSymbol("parser", "parse", range, nameRange, "");
            return completion.createLocalVariable(mcs, "org.apache.commons.cli.CommandLine");
        });
        System.out.println(lv);
    }

    private JavaVariableCompletion getCompilation() throws Exception {
        return new JavaVariableCompletion(CacheBuilder.newBuilder()
                .maximumSize(256)
                .build(new JavaSourceLoader()));
    }

}
