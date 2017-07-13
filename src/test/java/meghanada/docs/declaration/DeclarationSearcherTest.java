package meghanada.docs.declaration;

import static meghanada.config.Config.debugIt;
import static meghanada.config.Config.timeIt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Optional;
import meghanada.GradleTestBase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class DeclarationSearcherTest extends GradleTestBase {
  private DeclarationSearcher searcher;

  @BeforeClass
  public static void setup() throws Exception {
    GradleTestBase.setupReflector(false);
  }

  @AfterClass
  public static void shutdown() throws Exception {
    GradleTestBase.shutdown();
  }

  private DeclarationSearcher getSearcher() throws Exception {
    if (searcher != null) {
      return searcher;
    }
    searcher = new DeclarationSearcher(getProject());
    return searcher;
  }

  @Test
  public void testFieldDeclaration01() throws Exception {
    File f = new File("./src/main/java/meghanada/server/emacs/EmacsServer.java").getCanonicalFile();
    assert f.exists();

    final DeclarationSearcher searcher = getSearcher();
    final Optional<Declaration> result =
        timeIt(() -> searcher.searchDeclaration(f, 298, 14, "executorService"));
    assertNotNull(result);
    assertTrue(result.isPresent());
    result.ifPresent(
        declaration -> {
          assertEquals("this.executorService", declaration.scopeInfo);
          assertEquals("java.util.concurrent.ExecutorService", declaration.signature);
        });
  }

  @Test
  public void testMethodDeclaration01() throws Exception {
    File f = new File("./src/main/java/meghanada/server/emacs/EmacsServer.java").getCanonicalFile();
    assert f.exists();
    final DeclarationSearcher searcher = getSearcher();
    final Optional<Declaration> result =
        debugIt(() -> searcher.searchDeclaration(f, 53, 45, "getByName"));
    assertNotNull(result);
    assertTrue(result.isPresent());
    result.ifPresent(
        declaration -> {
          assertEquals("InetAddress.getByName", declaration.scopeInfo);
          assertEquals(
              "public static InetAddress getByName(String arg0) throws UnknownHostException",
              declaration.signature);
        });
  }

  @Test
  public void testMethodDeclaration02() throws Exception {
    File f = new File("./src/main/java/meghanada/server/emacs/EmacsServer.java").getCanonicalFile();
    assert f.exists();
    final DeclarationSearcher searcher = getSearcher();
    final Optional<Declaration> result =
        debugIt(() -> searcher.searchDeclaration(f, 347, 33, "submit"));
    assertNotNull(result);
    assertTrue(result.isPresent());
    result.ifPresent(
        declaration -> {
          assertEquals("this.executorService.submit", declaration.scopeInfo);
          assertEquals("public Future<?> submit(Runnable arg0)", declaration.signature);
        });
  }

  @Test
  public void testMethodDeclaration03() throws Exception {
    File f = new File("./src/main/java/meghanada/server/emacs/EmacsServer.java").getCanonicalFile();
    assert f.exists();
    final DeclarationSearcher searcher = getSearcher();
    final Optional<Declaration> result =
        debugIt(() -> searcher.searchDeclaration(f, 350, 24, "BufferedReader"));
    assertNotNull(result);
    assertTrue(result.isPresent());
    result.ifPresent(
        declaration -> {
          assertEquals("BufferedReader", declaration.scopeInfo);
          assertEquals("public BufferedReader(Reader arg0)", declaration.signature);
        });
  }

  @Test
  public void testMethodDeclaration04() throws Exception {
    File f = new File("./src/main/java/meghanada/server/emacs/EmacsServer.java").getCanonicalFile();
    assert f.exists();
    final DeclarationSearcher searcher = getSearcher();
    final Optional<Declaration> result =
        debugIt(() -> searcher.searchDeclaration(f, 355, 53, "getOutputFormatter"));
    assertNotNull(result);
    assertTrue(result.isPresent());
    result.ifPresent(
        declaration -> {
          assertEquals("getOutputFormatter", declaration.scopeInfo);
          assertEquals("private OutputFormatter getOutputFormatter()", declaration.signature);
          assertEquals(2, declaration.argumentIndex);
        });
  }

  @Test
  public void testClassDeclaration01() throws Exception {
    File f = new File("./src/main/java/meghanada/server/emacs/EmacsServer.java").getCanonicalFile();
    assert f.exists();
    final DeclarationSearcher searcher = getSearcher();
    final Optional<Declaration> result =
        debugIt(
            () -> {
              return searcher.searchDeclaration(f, 31, 20, "ServerSocket");
            });
    assertNotNull(result);
    assertTrue(result.isPresent());
    result.ifPresent(
        declaration -> {
          assertEquals("ServerSocket", declaration.scopeInfo);
          assertEquals("java.net.ServerSocket", declaration.signature);
        });
  }

  @Test
  public void testClassDeclaration03() throws Exception {
    File f = new File("./src/main/java/meghanada/server/emacs/EmacsServer.java").getCanonicalFile();
    assert f.exists();
    final DeclarationSearcher searcher = getSearcher();
    final Optional<Declaration> result =
        timeIt(
            () -> {
              return searcher.searchDeclaration(f, 37, 19, "OUTPUT");
            });
    assertNotNull(result);
    assertTrue(result.isPresent());
    result.ifPresent(
        declaration -> {
          assertEquals("OUTPUT", declaration.scopeInfo);
          assertEquals("meghanada.server.emacs.EmacsServer$OUTPUT", declaration.signature);
        });
  }

  @Test
  public void testClassDeclaration02() throws Exception {
    File f = new File("./src/main/java/meghanada/server/emacs/EmacsServer.java").getCanonicalFile();
    assert f.exists();
    final DeclarationSearcher searcher = getSearcher();
    final Optional<Declaration> result =
        timeIt(() -> searcher.searchDeclaration(f, 58, 32, "SEXP"));
    assertNotNull(result);
    assertTrue(result.isPresent());
    result.ifPresent(
        declaration -> {
          assertEquals("OUTPUT.SEXP", declaration.scopeInfo);
          assertEquals("meghanada.server.emacs.EmacsServer$OUTPUT", declaration.signature);
        });
  }

  @Test
  public void testVar01() throws Exception {
    File f = new File("./src/main/java/meghanada/server/emacs/EmacsServer.java").getCanonicalFile();
    assert f.exists();

    final DeclarationSearcher searcher = getSearcher();
    final Optional<Declaration> result =
        timeIt(() -> searcher.searchDeclaration(f, 56, 55, "address"));
    assertNotNull(result);
    assertTrue(result.isPresent());
    result.ifPresent(
        declaration -> {
          assertEquals("address", declaration.scopeInfo);
          assertEquals("java.net.InetAddress", declaration.signature);
          assertEquals(2, declaration.argumentIndex);
        });
  }

  @Test
  public void testVar02() throws Exception {
    File f = new File("./src/main/java/meghanada/server/emacs/EmacsServer.java").getCanonicalFile();
    assert f.exists();

    final DeclarationSearcher searcher = getSearcher();
    final Optional<Declaration> result = timeIt(() -> searcher.searchDeclaration(f, 56, 48, "0"));
    assertNotNull(result);
    assertTrue(result.isPresent());
    result.ifPresent(
        declaration -> {
          assertEquals("0", declaration.scopeInfo);
          assertEquals("java.lang.Integer", declaration.signature);
          assertEquals(1, declaration.argumentIndex);
        });
  }

  @Test
  public void testVar03() throws Exception {
    File f = new File("./src/main/java/meghanada/server/emacs/EmacsServer.java").getCanonicalFile();
    assert f.exists();

    final DeclarationSearcher searcher = getSearcher();
    final Optional<Declaration> result =
        timeIt(() -> searcher.searchDeclaration(f, 354, 34, "handler"));
    assertNotNull(result);
    assertTrue(result.isPresent());
    result.ifPresent(
        declaration -> {
          assertEquals("handler", declaration.scopeInfo);
          assertEquals("meghanada.server.CommandHandler", declaration.signature);
          assertEquals(-1, declaration.argumentIndex);
        });
  }

  @Test
  public void testVar04() throws Exception {
    File f = new File("./src/main/java/meghanada/project/Project.java").getCanonicalFile();
    assert f.exists();

    final DeclarationSearcher searcher = getSearcher();
    final Optional<Declaration> result =
        timeIt(() -> searcher.searchDeclaration(f, 1103, 7, "source"));
    assertNotNull(result);
    assertTrue(result.isPresent());
    result.ifPresent(
        declaration -> {
          assertEquals("source", declaration.scopeInfo);
          assertEquals("meghanada.analyze.Source", declaration.signature);
          // assertEquals(-1, declaration.argumentIndex);
        });
  }

  @Test
  public void testVar05() throws Exception {
    File f = new File("./src/main/java/meghanada/project/Project.java").getCanonicalFile();
    assert f.exists();

    final DeclarationSearcher searcher = getSearcher();
    final Optional<Declaration> result =
        timeIt(() -> searcher.searchDeclaration(f, 1098, 9, "source"));
    assertNotNull(result);
    assertTrue(result.isPresent());
    result.ifPresent(
        declaration -> {
          assertEquals("source", declaration.scopeInfo);
          assertEquals("meghanada.analyze.Source", declaration.signature);
          // assertEquals(-1, declaration.argumentIndex);
        });
  }
}
