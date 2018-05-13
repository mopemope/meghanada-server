package meghanada.utils;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;

public class ClassNameUtilsTest {

  @Test
  public void parseTypeParameter1() throws Exception {
    String name = "java.util.stream.BaseStream<T, java.util.stream.Stream<T>>";
    List<String> typeParams = ClassNameUtils.parseTypeParameter(name);
    assertEquals("T", typeParams.get(0));
    assertEquals("java.util.stream.Stream<T>", typeParams.get(1));
  }

  @Test
  public void parseTypeParameter2() throws Exception {
    String name = "java.util.Enumeration<? extends ZipEntry>";
    List<String> typeParams = ClassNameUtils.parseTypeParameter(name);
    assertEquals("capture of ? extends ZipEntry", typeParams.get(0));
  }

  @Test
  public void parseTypeParameter3() throws Exception {
    String name = "java.util.Map<? extends String, ? extends Long>";
    List<String> typeParams = ClassNameUtils.parseTypeParameter(name);
    assertEquals("capture of ? extends String", typeParams.get(0));
    assertEquals("capture of ? extends Long", typeParams.get(1));
  }

  @Test
  public void parseTypeParameter4() throws Exception {
    String name =
        "java.util.stream.Stream<java.util.stream.Stream<java.util.List<java.lang.String>>>";
    List<String> typeParams = ClassNameUtils.parseTypeParameter(name);
    assertEquals("java.util.stream.Stream<java.util.List<java.lang.String>>", typeParams.get(0));
  }

  @Test
  public void parseTypeParameter5() throws Exception {
    String name = "java.util.Map<? extends List<String>, ? extends Long>";
    List<String> typeParams = ClassNameUtils.parseTypeParameter(name);
    assertEquals("capture of ? extends List<String>", typeParams.get(0));
    assertEquals("capture of ? extends Long", typeParams.get(1));
  }

  @Test
  public void parseTypeParameter6() throws Exception {
    String name = "java.util.List<java.util.List<? extends String>>";
    List<String> typeParams = ClassNameUtils.parseTypeParameter(name);
    assertEquals("java.util.List<? extends String>", typeParams.get(0));
  }

  @Test
  public void parseTypeParameter7() throws Exception {
    String name = "meghanada.SelfRef1<T>$Ref";
    List<String> typeParams = ClassNameUtils.parseTypeParameter(name);
    assertEquals("T", typeParams.get(0));
  }

  @Test
  public void innerClassName1() throws Exception {
    String name = "java.util.Map.Entry";
    final Optional<String> innerClassName = ClassNameUtils.toInnerClassName(name);
    assertEquals("java.util.Map$Entry", innerClassName.get());
  }

  @Test
  public void innerClassName2() throws Exception {
    String name = "java.util.Map$Entry";
    final Optional<String> innerClassName = ClassNameUtils.toInnerClassName(name);
    assertEquals("java.util.Map$Entry", innerClassName.get());
  }

  @Test
  public void rmCapture1() throws Exception {
    String name = "capture of ? extends ZipEntry";
    final String s = ClassNameUtils.removeCapture(name);
    assertEquals("ZipEntry", s);
  }

  @Test
  public void rmCapture2() throws Exception {
    String name = "java.util.ZipEntry";
    final String s = ClassNameUtils.removeCapture(name);
    assertEquals("java.util.ZipEntry", s);
  }

  @Test
  public void rmCapture3() throws Exception {
    String name = "capture of ?";
    final String s = ClassNameUtils.removeCapture(name);
    assertEquals("capture of ?", s);
  }

  @Test
  public void getSimpleName1() throws Exception {
    String name = "java.util.ZipEntry";
    final String s = ClassNameUtils.getSimpleName(name);
    assertEquals("ZipEntry", s);
  }

  @Test
  public void getSimpleName2() throws Exception {
    String name = "java.util.List<java.lang.String>";
    final String s = ClassNameUtils.getSimpleName(name);
    assertEquals("List<java.lang.String>", s);
  }

  @Test
  public void replaceTypeParameter1() throws Exception {
    String name = "java.util.function.Function<? super String, ? extends R>";
    Map<String, String> map = new HashMap<>();
    map.putIfAbsent("R", "java.util.stream.Stream<String>");
    final String s = ClassNameUtils.replaceTypeParameter(name, map);
    assertEquals(
        "java.util.function.Function<? super String, ? extends java.util.stream.Stream<String>>",
        s);
  }

  @Test
  public void replaceTypeParameter2() throws Exception {
    String name = "java.util.function.Function<? super T, ? extends Stream<? extends R>>";
    Map<String, String> map = new HashMap<>();
    map.putIfAbsent("T", "java.lang.Integer");
    map.putIfAbsent("R", "java.lang.String");
    final String s = ClassNameUtils.replaceTypeParameter(name, map);
    assertEquals(
        "java.util.function.Function<? super java.lang.Integer, ? extends Stream<? extends java.lang.String>>",
        s);
  }

  @Test
  public void replaceTypeParameter3() throws Exception {
    String name = "java.util.function.Function<? super T, ? extends Stream<? extends R>>";
    Map<String, String> map = new HashMap<>();
    map.putIfAbsent("T", "java.lang.Integer");
    final String s = ClassNameUtils.replaceTypeParameter(name, map);
    assertEquals(
        "java.util.function.Function<? super java.lang.Integer, ? extends Stream<? extends R>>", s);
  }

  @Test
  public void removeTypeParameter1() throws Exception {
    String name = "java.util.Map<K, V>";
    final String s = ClassNameUtils.removeTypeParameter(name);
    assertEquals("java.util.Map", s);
  }

  @Test
  public void removeTypeParameter2() throws Exception {
    String name = "java.util.Map<K, V>$Entry";
    final String s = ClassNameUtils.removeTypeParameter(name);
    assertEquals("java.util.Map$Entry", s);
  }

  @Test
  public void removeTypeArrayParameter1() throws Exception {
    String name = "java.util.Map<K, V>[]";
    final String s = ClassNameUtils.removeTypeAndArray(name);
    assertEquals("java.util.Map", s);
  }

  @Test
  public void removeTypeArrayParameter2() throws Exception {
    String name = "java.util.Map<K, V>$Entry[]";
    final String s = ClassNameUtils.removeTypeAndArray(name);
    assertEquals("java.util.Map$Entry", s);
  }

  @Test
  public void removeTypeArrayParameter3() throws Exception {
    String name = "java.util.Map<Object[], Void>";
    final String s = ClassNameUtils.removeTypeAndArray(name);
    assertEquals("java.util.Map", s);
  }

  @Test
  public void replaceDot() throws Exception {
    String name = "java.util.function.Function<? super T, ? extends Stream<? extends R>>";
    final String s = ClassNameUtils.replaceDot2FileSep(name);
    assertEquals(
        "java"
            + File.separator
            + "util"
            + File.separator
            + "function"
            + File.separator
            + "Function",
        s);
  }

  @Test
  public void getAllSimpleName1() throws Exception {
    String name = "java.util.function.Function<java.utils.List, java.util.Map>";
    final String s = ClassNameUtils.getAllSimpleName(name);
    assertEquals("Function<List, Map>", s);
  }

  @Test
  public void getAllSimpleName2() throws Exception {
    String name = "java.util.function.Function<java.utils.List<java.lang.List<java.io.File>>>";
    final String s = ClassNameUtils.getAllSimpleName(name);
    assertEquals("Function<List<List<File>>>", s);
  }

  @Test
  public void getParentName() throws Exception {
    String name = "java.utils.Map$Entry";
    final String s = ClassNameUtils.getParentClass(name);
    assertEquals("java.utils.Map", s);
  }

  @Test
  public void isAnonymousClass1() throws Exception {
    final String name = "java.utils.Map$1";
    final boolean b = ClassNameUtils.isAnonymousClass(name);
    assertEquals(true, b);
  }

  @Test
  public void isAnonymousClass2() throws Exception {
    final String name = "java.utils.Map$1$1";
    final boolean b = ClassNameUtils.isAnonymousClass(name);
    assertEquals(true, b);
  }

  @Test
  public void isAnonymousClass3() throws Exception {
    final String name = "java.utils.Map1";
    final boolean b = ClassNameUtils.isAnonymousClass(name);
    assertEquals(false, b);
  }

  @Test
  public void compareArgumentType1() {
    List<String> arg1 = new ArrayList<>();
    arg1.add("boolean");
    arg1.add("java.lang.String");
    List<String> arg2 = new ArrayList<>();
    arg2.add("boolean");
    arg2.add("java.lang.String");
    boolean b = ClassNameUtils.compareArgumentType(arg1, arg2, false);
    assertEquals(true, b);
  }

  @Test
  public void compareArgumentType2() {
    List<String> arg1 = new ArrayList<>();
    arg1.add("boolean");
    arg1.add("java.lang.String");
    List<String> arg2 = new ArrayList<>();
    arg2.add("boolean");
    arg2.add("java.lang.String[]");
    boolean b = ClassNameUtils.compareArgumentType(arg1, arg2, false);
    assertEquals(false, b);
  }

  @Test
  public void compareArgumentType3() {
    List<String> arg1 = new ArrayList<>();
    arg1.add("boolean");
    arg1.add("java.lang.String");
    List<String> arg2 = new ArrayList<>();
    arg2.add("boolean");
    arg2.add("java.lang.String[]");
    boolean b = ClassNameUtils.compareArgumentType(arg1, arg2, true);
    assertEquals(true, b);
  }
}
