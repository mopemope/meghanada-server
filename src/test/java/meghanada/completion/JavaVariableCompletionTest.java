package meghanada.completion;

import com.google.common.cache.CacheBuilder;
import meghanada.GradleTestBase;
import meghanada.analyze.MethodCall;
import meghanada.analyze.Range;
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
        File file = new File("./src/test/java/meghanada/Gen1.java");

        LocalVariable lv = timeIt(() -> {
            return completion.localVariable(file, 20);
        });
        System.out.println(lv);
        assertEquals("java.lang.String", lv.getReturnFQCN());
        assertEquals(lv.getCandidates().size(), 4);
    }

    @Test
    public void createLocalVariable1() throws Exception {
        JavaVariableCompletion completion = getCompilation();

        LocalVariable lv = debugIt(() -> {
            final Range range = new Range(0, 0, 0, 0);
            final Range nameRange = new Range(0, 0, 0, 0);
            final MethodCall mc = new MethodCall("mkdir", 0, nameRange, range);
            return completion.createLocalVariable(mc, "boolean");
        });
        System.out.println(lv);
        assertEquals(lv.getCandidates().size(), 2);
    }

    @Test
    public void createLocalVariable2() throws Exception {
        JavaVariableCompletion completion = getCompilation();
        LocalVariable lv = traceIt(() -> {
            final Range range = new Range(0, 0, 0, 0);
            final Range nameRange = new Range(0, 0, 0, 0);
            final MethodCall mc = new MethodCall("file", "getFileName", 0, nameRange, range);
            return completion.createLocalVariable(mc, "String");
        });
        System.out.println(lv);
        assertEquals(lv.getCandidates().size(), 4);
    }

    @Test
    public void createLocalVariable3() throws Exception {
        JavaVariableCompletion completion = getCompilation();
        LocalVariable lv = timeIt(() -> {
            final Range range = new Range(0, 0, 0, 0);
            final Range nameRange = new Range(0, 0, 0, 0);
            final MethodCall mc = new MethodCall("list", "subList", 0, nameRange, range);
            return completion.createLocalVariable(mc, "java.util.List<String>");
        });
        System.out.println(lv);
        assertEquals(lv.getCandidates().size(), 5);
    }

    @Test
    public void createLocalVariable4() throws Exception {
        JavaVariableCompletion completion = getCompilation();
        LocalVariable lv = timeIt(() -> {
            final Range range = new Range(0, 0, 0, 0);
            final Range nameRange = new Range(0, 0, 0, 0);
            final MethodCall mc = new MethodCall("list", "subList", 0, nameRange, range);
            return completion.createLocalVariable(mc, "java.util.List<MemberDescriptor>");
        });
        System.out.println(lv);
        assertEquals(lv.getCandidates().size(), 6);
    }

    @Test
    public void createLocalVariable5() throws Exception {
        JavaVariableCompletion completion = getCompilation();
        LocalVariable lv = timeIt(() -> {
            final Range range = new Range(0, 0, 0, 0);
            final Range nameRange = new Range(0, 0, 0, 0);
            final MethodCall mcs = new MethodCall("parser", "parse", 0, nameRange, range);
            return completion.createLocalVariable(mcs, "org.apache.commons.cli.CommandLine");
        });
        System.out.println(lv);
        assertEquals(lv.getCandidates().size(), 4);
    }

    private JavaVariableCompletion getCompilation() throws Exception {
        return new JavaVariableCompletion(CacheBuilder.newBuilder()
                .maximumSize(256)
                .build(new JavaSourceLoader(GradleTestBase.getProject())));
    }

}
