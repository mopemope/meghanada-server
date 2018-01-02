package meghanada.utils;

import com.google.common.base.Joiner;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import meghanada.reflect.asm.CachedASMReflector;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ClassNameUtils {

  public static final String OBJECT_CLASS = "java.lang.Object";

  public static final String CLASS_TYPE_VARIABLE_MARK = "%%";
  public static final String FORMAL_TYPE_VARIABLE_MARK = "##";

  public static final String INNER_MARK = "$";
  public static final String CAPTURE_OF = "capture of ";
  public static final String ARRAY = "[]";
  private static final String VA_ARGS = "...";
  private static final String SLASH = "/";
  private static final String DOT_SEPARATOR = ".";
  private static final Map<String, String> removeCaptureMap;
  private static final Map<String, String> removeTypeValMap;
  private static final Map<String, String> removeWildcardMap;
  private static final Logger log = LogManager.getLogger(ClassNameUtils.class);
  private static final String[] primitives =
      new String[] {"byte", "char", "double", "float", "int", "long", "short", "void", "boolean"};
  private static final String[] box =
      new String[] {
        "java.lang.Byte",
        "java.lang.Character",
        "java.lang.Double",
        "java.lang.Float",
        "java.lang.Integer",
        "java.lang.Long",
        "java.lang.Short",
        "void",
        "java.lang.Boolean"
      };

  private static final Map<String, String> boxMap = new HashMap<>(16);
  private static final Map<String, Set<String>> implicitTypeCastMap = new HashMap<>(6);

  static {
    for (int i = 0; i < primitives.length; i++) {
      boxMap.put(primitives[i], box[i]);
    }

    removeCaptureMap = new HashMap<>(3);
    removeCaptureMap.put(CAPTURE_OF + "? super ", "");
    removeCaptureMap.put(CAPTURE_OF + "? extends ", "");
    removeCaptureMap.put("  ", " ");

    removeTypeValMap = new HashMap<>(2);
    removeTypeValMap.put(CLASS_TYPE_VARIABLE_MARK, "");
    removeTypeValMap.put(FORMAL_TYPE_VARIABLE_MARK, "");

    removeWildcardMap = new HashMap<>(2);
    removeWildcardMap.put("? super ", "");
    removeWildcardMap.put("? extends ", "");

    // char -> int, long, floa, double
    Set<String> c = new HashSet<String>(4);
    c.add("java.lang.Integer");
    c.add("java.lang.Long");
    c.add("java.lang.Float");
    c.add("java.lang.Double");
    implicitTypeCastMap.put("java.lang.Character", c);

    // byte -> short, int, long, float, double
    Set<String> b = new HashSet<String>(5);
    b.add("java.lang.Short");
    b.add("java.lang.Integer");
    b.add("java.lang.Long");
    b.add("java.lang.Float");
    b.add("java.lang.Double");
    implicitTypeCastMap.put("java.lang.Byte", b);

    // short -> int, long, float, double
    Set<String> s = new HashSet<String>(4);
    s.add("java.lang.Integer");
    s.add("java.lang.Long");
    s.add("java.lang.Float");
    s.add("java.lang.Double");
    implicitTypeCastMap.put("java.lang.Short", s);

    // int -> long, float, double
    Set<String> i = new HashSet<String>(3);
    i.add("java.lang.Long");
    i.add("java.lang.Float");
    i.add("java.lang.Double");
    implicitTypeCastMap.put("java.lang.Integer", i);

    // long -> float, double
    Set<String> l = new HashSet<String>(2);
    l.add("java.lang.Float");
    l.add("java.lang.Double");
    implicitTypeCastMap.put("java.lang.Long", i);

    // float -> double
    Set<String> f = new HashSet<String>(1);
    f.add("java.lang.Double");
    implicitTypeCastMap.put("java.lang.Float", f);
  }

  private ClassNameUtils() {}

  private static String autoBoxing(final String className) {
    if (boxMap.containsKey(className)) {
      return boxMap.get(className);
    }
    return className;
  }

  public static boolean isPrimitive(final String className) {
    for (final String p : primitives) {
      if (p.equals(className)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isArray(final String name) {
    return name.endsWith(ARRAY);
  }

  public static String replaceFromMap(final String string, final Map<String, String> replacements) {
    StringBuilder sb = new StringBuilder(string);
    for (Map.Entry<String, String> entry : replacements.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();

      final int start = sb.indexOf(key, 0);
      replaceString(sb, key, value, start);
    }
    return sb.toString();
  }

  public static String replace(final String string, final String target, final String replacement) {
    final StringBuilder sb = new StringBuilder(string);
    final int start = sb.indexOf(target, 0);
    replaceString(sb, target, replacement, start);
    return sb.toString();
  }

  public static String replaceDot2FileSep(final String string) {
    final StringBuilder sb = new StringBuilder(ClassNameUtils.removeTypeAndArray(string));
    final int start = sb.indexOf(DOT_SEPARATOR, 0);
    replaceString(sb, DOT_SEPARATOR, File.separator, start);
    return sb.toString();
  }

  public static String replaceSlash(String string) {
    final StringBuilder sb = new StringBuilder(string);
    final int start = sb.indexOf(SLASH, 0);
    replaceString(sb, SLASH, ClassNameUtils.DOT_SEPARATOR, start);
    return sb.toString();
  }

  public static String getParentClass(final String name) {
    final ClassName className = new ClassName(name);
    final String simpleName = className.getName();
    final int i = simpleName.indexOf('$');
    if (i > 0) {
      return simpleName.substring(0, i);
    }
    return simpleName;
  }

  public static String replaceInnerMark(final String string) {
    if (!string.contains(INNER_MARK)) {
      return string;
    }
    final StringBuilder sb = new StringBuilder(string);
    final int start = sb.indexOf(INNER_MARK, 0);
    replaceString(sb, INNER_MARK, ClassNameUtils.DOT_SEPARATOR, start);
    return sb.toString();
  }

  public static String replaceDotToInnerMark(String string, boolean last) {
    final StringBuilder sb = new StringBuilder(string);
    final int start = last ? sb.lastIndexOf(DOT_SEPARATOR, 0) : sb.indexOf(DOT_SEPARATOR, 0);
    replaceString(sb, DOT_SEPARATOR, ClassNameUtils.INNER_MARK, start);
    return sb.toString();
  }

  private static void replaceString(
      final StringBuilder sb, final String key, final String value, int start) {
    while (start > -1) {
      final int end = start + key.length();
      final int nextSearchStart = start + value.length();
      sb.replace(start, end, value);
      start = sb.indexOf(key, nextSearchStart);
    }
  }

  public static String removeTypeMark(final String val) {
    return replaceFromMap(val, removeTypeValMap);
  }

  public static Optional<String> toInnerClassName(final String name) {
    if (name.contains("$")) {
      return Optional.of(name);
    }
    final int sep = name.lastIndexOf('.');
    if (sep > 0) {
      return Optional.of(name.substring(0, sep) + '$' + name.substring(sep + 1));
    }
    return Optional.empty();
  }

  public static boolean hasPackage(String name) {
    int sep = name.lastIndexOf('.');
    return sep > 0;
  }

  public static String getSimpleName(final String fqcn) {
    final int typeIndex = fqcn.indexOf('<');

    String name = fqcn;
    if (typeIndex >= 0) {
      name = name.substring(0, typeIndex);
    }

    int arrayIndex = name.indexOf('[');
    if (arrayIndex >= 0) {
      name = name.substring(0, arrayIndex);
    }

    final int idx = name.lastIndexOf('.');
    if (idx >= 0) {
      return fqcn.substring(idx + 1, fqcn.length());
    }
    return fqcn;
  }

  public static String getPackage(final String fqcn) {
    int idx = fqcn.lastIndexOf('.');
    if (idx >= 0) {
      return fqcn.substring(0, idx);
    }
    return "";
  }

  public static Optional<String> getTypeVariable(final String val) {
    int idx = val.indexOf(CLASS_TYPE_VARIABLE_MARK);
    if (idx >= 0) {
      return Optional.of(val.substring(idx + 2, idx + 3));
    }
    return Optional.empty();
  }

  public static String removeTypeParameter(String name) {
    // check type parameter
    int tpIdx = name.indexOf('<');
    if (tpIdx >= 0) {
      final int last = name.lastIndexOf('>');
      String fst = name.substring(0, tpIdx);
      String sec = name.substring(last + 1, name.length());
      name = fst + sec;
    }
    return name;
  }

  private static String removeArray(String name) {
    // check array parameter
    int idx = name.indexOf('[');
    if (idx >= 0) {
      final int last = name.lastIndexOf(']');
      String fst = name.substring(0, idx);
      String sec = name.substring(last + 1, name.length());
      name = fst + sec;
    }
    return name;
  }

  public static String removeTypeAndArray(String name) {
    return removeTypeParameter(removeArray(name));
  }

  public static List<String> parseTypeParameter(final String str) {
    if (str.isEmpty()) {
      return Collections.emptyList();
    }
    final List<String> result = new ArrayList<>(4);

    final int idx = str.indexOf('<');
    if (idx >= 0) {
      String gen = str.substring(idx + 1, str.length() - 1);
      int indent = 0;
      StringBuilder sb = new StringBuilder(16);
      boolean wild = false;
      int len = gen.length();
      for (int i = 0; i < len; i++) {
        char c = gen.charAt(i);
        switch (c) {
          case '?':
            if (indent == 0) {
              sb.append(CAPTURE_OF);
              sb.append(c);
              wild = true;
            } else {
              sb.append(c);
            }
            continue;
          case '<':
            // if (indent == 0) {
            sb.append(c);
            // }
            indent++;
            continue;
          case '>':
            indent--;
            if (indent < 0) {
              if (sb.length() > 0) {
                result.add(sb.toString());
              }
              return result;
            }
            sb.append(c);
            continue;
          case ' ':
            if (wild) {
              sb.append(c);
            } else if (indent > 0) {
              sb.append(c);
            }
            // skip
            continue;
          case ',':
            if (indent == 0 && sb.length() > 0) {
              wild = false;
              result.add(sb.toString());
              // clear
              sb.setLength(0);
            } else {
              sb.append(c);
              sb.append(' ');
            }
            continue;
          default:
            sb.append(c);
        }
      }
      if (sb.length() > 0) {
        result.add(sb.toString());
      }

      return result;
    }
    return Collections.emptyList();
  }

  static String replaceTypeParameter(final String str, final Map<String, String> replaceMap) {

    final int idx = str.indexOf('<');
    StringBuilder all = new StringBuilder(64);

    if (idx >= 0) {
      String pre = str.substring(0, idx + 1);
      all.append(pre);
      String genericsStr = str.substring(idx + 1, str.length() - 1);
      int indent = 0;
      StringBuilder sb = new StringBuilder(16);
      boolean wild = false;
      int len = genericsStr.length();
      for (int i = 0; i < len; i++) {
        char c = genericsStr.charAt(i);
        switch (c) {
          case '?':
            if (indent == 0) {
              // sb.append(CAPTURE_OF);
              sb.append(c);
              wild = true;
            } else {
              sb.append(c);
            }
            continue;
          case '<':
            // if (indent == 0) {
            sb.append(c);
            // }
            indent++;
            continue;
          case '>':
            indent--;
            // if (indent == 0) {
            sb.append(c);
            // }
            continue;
          case ' ':
            if (wild) {
              sb.append(c);
            }
            // skip
            continue;
          case ',':
            if (indent == 0 && sb.length() > 0) {
              wild = false;

              final String gen = sb.toString();
              replaceTypeStr(replaceMap, all, gen);

              all.append(", ");
              sb.setLength(0);
            } else {
              sb.append(c);
              sb.append(' ');
            }
            continue;
          default:
            sb.append(c);
        }
      }
      if (sb.length() > 0) {
        final String gen = sb.toString();
        replaceTypeStr(replaceMap, all, gen);
      }
      all.append('>');
      return all.toString();
    }
    return str;
  }

  private static void replaceTypeStr(
      Map<String, String> replaceMap, StringBuilder all, String gen) {
    boolean replaced = false;
    for (Map.Entry<String, String> stringStringEntry : replaceMap.entrySet()) {
      if (stringStringEntry.getKey().equals(gen)) {
        // replace
        all.append(stringStringEntry.getValue());
        replaced = true;
        break;
      } else if (gen.contains("super " + stringStringEntry.getKey())) {
        // replace
        final String replaceStr =
            ClassNameUtils.replace(
                gen,
                "super " + stringStringEntry.getKey(),
                "super " + stringStringEntry.getValue());
        all.append(replaceStr);
        replaced = true;
        break;
      } else if (gen.contains("extends " + stringStringEntry.getKey())) {
        // replace
        final String replaceStr =
            ClassNameUtils.replace(
                gen,
                "extends " + stringStringEntry.getKey(),
                "extends " + stringStringEntry.getValue());
        all.append(replaceStr);
        replaced = true;
        break;
      }
    }
    if (!replaced) {
      all.append(gen);
    }
  }

  public static String removeCapture(final String name) {
    if (name.contains(CAPTURE_OF)) {
      return ClassNameUtils.replaceFromMap(name, removeCaptureMap);
    }
    return name;
  }

  public static String removeCaptureAndWildCard(final String name) {
    if (name.contains(CAPTURE_OF)) {
      return ClassNameUtils.replaceFromMap(name, removeCaptureMap);
    }
    return ClassNameUtils.removeWildcard(name);
  }

  static String vaArgsToArray(String name) {
    if (name.endsWith(VA_ARGS)) {
      return ClassNameUtils.replace(name, VA_ARGS, ARRAY);
    }
    return name;
  }

  public static String getAllSimpleName(final String name) {
    final String base = ClassNameUtils.removeTypeParameter(ClassNameUtils.getSimpleName(name));

    StringBuilder sb = new StringBuilder(base);

    final List<String> parameters = ClassNameUtils.parseTypeParameter(name);
    if (parameters.size() > 0) {
      sb.append('<');
      List<String> temp = new ArrayList<>(parameters.size());

      for (final String s : parameters) {
        final String simpleName = ClassNameUtils.getSimpleName(s);
        if (simpleName.indexOf('<') > 0) {
          temp.add(getAllSimpleName(simpleName));
        } else {
          temp.add(simpleName);
        }
      }
      sb.append(Joiner.on(", ").join(temp));
      sb.append('>');
    }

    return sb.toString();
  }

  public static String removeWildcard(final String name) {
    if (name.startsWith("?")) {
      return ClassNameUtils.replaceFromMap(name, removeWildcardMap);
    }
    return name;
  }

  public static boolean compareArgumentType(
      final List<String> arguments, final List<String> parameters) {

    if (!parameters.isEmpty() && parameters.size() > 0 && arguments.size() > parameters.size()) {
      final String last = parameters.get(parameters.size() - 1);
      final boolean isVarargs = last.endsWith(ClassNameUtils.ARRAY);
      if (isVarargs) {
        return compareVarArgumentType(arguments, parameters);
      }
    }
    if (arguments.size() != parameters.size()) {
      return false;
    }
    final Iterator<String> iteratorA = arguments.iterator();
    final CachedASMReflector reflector = CachedASMReflector.getInstance();
    for (final String paramStr : parameters) {
      final String realArgStr = iteratorA.next();
      if (realArgStr == null) {
        return false;
      }
      if (realArgStr.equals("<null>")) {
        // match all
        continue;
      }
      if (ClassNameUtils.isArray(paramStr) != ClassNameUtils.isArray(realArgStr)) {
        return false;
      }
      final ClassName paramClass = new ClassName(paramStr);
      final ClassName argClass = new ClassName(realArgStr);
      final String paramClassName = autoBoxing(paramClass.getName());
      final String argClassName = autoBoxing(argClass.getName());

      if (paramClassName.equals(argClassName)) {
        continue;
      }
      if (implicitTypeCastMap.containsKey(argClassName)) {
        Set<String> types = implicitTypeCastMap.get(argClassName);
        if (types.contains(paramClassName)) {
          continue;
        }
      }
      final boolean result =
          reflector
              .getSuperClassStream(argClassName)
              .anyMatch(
                  s -> {
                    final String cls = new ClassName(s).getName();
                    return cls.equals(paramClassName);
                  });
      if (!result) {
        return false;
      }
    }
    return true;
  }

  private static boolean compareVarArgumentType(List<String> arguments, List<String> parameters) {
    final Iterator<String> iteratorA = arguments.iterator();
    final CachedASMReflector reflector = CachedASMReflector.getInstance();
    final int last = parameters.size() - 1;
    int index = 0;
    for (final String paramStr : parameters) {
      if (index == last) {
        return checkVarargs(iteratorA, paramStr);
      } else {
        final String realArgStr = iteratorA.next();
        if (ClassNameUtils.isArray(paramStr) != ClassNameUtils.isArray(realArgStr)) {
          return false;
        }
        final ClassName paramClass = new ClassName(paramStr);
        final ClassName argClass = new ClassName(realArgStr);
        final String paramClassName = autoBoxing(paramClass.getName());
        final String argClassName = autoBoxing(argClass.getName());

        index++;
        if (paramClassName.equals(argClassName)) {
          continue;
        }

        if (implicitTypeCastMap.containsKey(argClassName)) {
          Set<String> types = implicitTypeCastMap.get(argClassName);
          if (types.contains(paramClassName)) {
            continue;
          }
        }

        final boolean result =
            reflector
                .getSuperClassStream(argClassName)
                .anyMatch(
                    s -> {
                      final String cls = new ClassName(s).getName();
                      return cls.equals(paramClassName);
                    });
        if (!result) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean checkVarargs(final Iterator<String> iteratorA, final String paramStr) {
    // base class
    final ClassName paramClass = new ClassName(paramStr);
    final String paramClassName = autoBoxing(paramClass.getName());

    while (iteratorA.hasNext()) {
      final String realArgStr = iteratorA.next();
      final ClassName argClass = new ClassName(realArgStr);
      final String argClassName = autoBoxing(argClass.getName());
      if (!paramClassName.equals(argClassName)) {
        return false;
      }
    }
    return true;
  }

  public static boolean isAnonymousClass(final String name) {
    if (!name.contains(INNER_MARK)) {
      return false;
    }
    final int i = name.lastIndexOf(INNER_MARK);
    if (i < name.length()) {
      final String s = name.substring(i + 1);
      return NumberUtils.isDigits(s);
    }
    return false;
  }
}
