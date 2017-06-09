package meghanada.completion;

import static meghanada.config.Config.debugIt;
import static meghanada.config.Config.timeIt;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Optional;
import meghanada.GradleTestBase;
import meghanada.analyze.MethodCall;
import meghanada.analyze.Range;
import org.junit.Test;

public class JavaVariableCompletionTest extends GradleTestBase {

  @Test
  public void localVariable() throws Exception {
    JavaVariableCompletion completion = getCompilation();
    File file = new File("./src/test/java/meghanada/Gen1.java");

    Optional<LocalVariable> olv =
        timeIt(
            () -> {
              return completion.localVariable(file, 20);
            });
    LocalVariable lv = olv.get();
    System.out.println(lv);
    assertEquals("java.lang.String", lv.getReturnFQCN());
    assertEquals(lv.getCandidates().size(), 4);
  }

  @Test
  public void createLocalVariable1() throws Exception {
    JavaVariableCompletion completion = getCompilation();

    Optional<LocalVariable> olv =
        debugIt(
            () -> {
              final Range range = new Range(0, 0, 0, 0);
              final Range nameRange = new Range(0, 0, 0, 0);
              final MethodCall mc = new MethodCall("mkdir", 0, nameRange, range);
              return completion.createLocalVariable(mc, "boolean");
            });
    LocalVariable lv = olv.get();
    System.out.println(lv);
    assertEquals(lv.getCandidates().size(), 2);
  }

  @Test
  public void createLocalVariable2() throws Exception {
    JavaVariableCompletion completion = getCompilation();
    Optional<LocalVariable> olv =
        timeIt(
            () -> {
              final Range range = new Range(0, 0, 0, 0);
              final Range nameRange = new Range(0, 0, 0, 0);
              final MethodCall mc = new MethodCall("file", "getFileName", 0, nameRange, range);
              return completion.createLocalVariable(mc, "String");
            });
    LocalVariable lv = olv.get();
    System.out.println(lv);
    assertEquals(lv.getCandidates().size(), 4);
  }

  @Test
  public void createLocalVariable3() throws Exception {
    JavaVariableCompletion completion = getCompilation();
    Optional<LocalVariable> olv =
        timeIt(
            () -> {
              final Range range = new Range(0, 0, 0, 0);
              final Range nameRange = new Range(0, 0, 0, 0);
              final MethodCall mc = new MethodCall("list", "subList", 0, nameRange, range);
              return completion.createLocalVariable(mc, "java.util.List<String>");
            });
    LocalVariable lv = olv.get();
    System.out.println(lv);
    assertEquals(lv.getCandidates().size(), 5);
  }

  @Test
  public void createLocalVariable4() throws Exception {
    JavaVariableCompletion completion = getCompilation();
    Optional<LocalVariable> olv =
        timeIt(
            () -> {
              final Range range = new Range(0, 0, 0, 0);
              final Range nameRange = new Range(0, 0, 0, 0);
              final MethodCall mc = new MethodCall("list", "subList", 0, nameRange, range);
              return completion.createLocalVariable(mc, "java.util.List<MemberDescriptor>");
            });

    LocalVariable lv = olv.get();
    System.out.println(lv);
    assertEquals(lv.getCandidates().size(), 6);
  }

  @Test
  public void createLocalVariable5() throws Exception {
    JavaVariableCompletion completion = getCompilation();
    Optional<LocalVariable> olv =
        timeIt(
            () -> {
              final Range range = new Range(0, 0, 0, 0);
              final Range nameRange = new Range(0, 0, 0, 0);
              final MethodCall mcs = new MethodCall("parser", "parse", 0, nameRange, range);
              return completion.createLocalVariable(mcs, "org.apache.commons.cli.CommandLine");
            });
    LocalVariable lv = olv.get();
    System.out.println(lv);
    assertEquals(lv.getCandidates().size(), 4);
  }

  private JavaVariableCompletion getCompilation() throws Exception {
    return new JavaVariableCompletion(getProject());
  }
}
