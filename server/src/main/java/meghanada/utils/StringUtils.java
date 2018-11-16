package meghanada.utils;

import static java.util.Objects.isNull;

import com.google.common.base.Splitter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {

  private static final String PatternForLowerCaseWord = "[^A-Z]*";
  private static final String PatternForAnything = ".*";
  private static final String PatternForSplitByUpper = "(?=\\p{Upper})";
  private static final Map<String, Pattern> cachedPatterns = new HashMap<>(500);
  private static final String ALGORITHM_SHA_256 = "SHA-256";

  public static String replace(final String string, final String target, final String replacement) {
    final StringBuilder sb = new StringBuilder(string);
    final int start = sb.indexOf(target, 0);
    replaceString(sb, target, replacement, start);
    return sb.toString();
  }

  static void replaceString(
      final StringBuilder sb, final String key, final String value, int start) {
    while (start > -1) {
      final int end = start + key.length();
      final int nextSearchStart = start + value.length();
      sb.replace(start, end, value);
      start = sb.indexOf(key, nextSearchStart);
    }
  }

  public static boolean contains(final String name, final String target) {
    return name.toLowerCase().contains(target.toLowerCase());
  }

  public static boolean isMatchCamelCase(final String name, final String target) {
    boolean matched;
    if (org.apache.commons.lang3.StringUtils.isEmpty(target)) {
      matched = true;
    } else {
      if (hasUpperCase(target)) {
        Pattern pattern = fromCacheOrCompile(target);
        if (isNull(pattern)) {
          matched = name.contains(target);
        } else {
          matched = matchAndUpdateCache(pattern, name);
        }
      } else {
        matched = matchWhenAllLowerCaseLetters(name, target);
      }
    }
    return matched;
  }

  private static Boolean matchAndUpdateCache(Pattern p, String name) {
    Matcher m = p.matcher(name);
    return m.matches();
  }

  private static boolean hasUpperCase(String target) {
    return !target.equals(target.toLowerCase());
  }

  private static boolean matchWhenAllLowerCaseLetters(String name, String target) {
    return name.startsWith(target);
  }

  private static Pattern compilePattern(List<String> input) {
    Pattern p = null;
    if (input.size() > 0) {
      StringBuilder sb = new StringBuilder();
      for (String s : input) {
        sb.append(s).append(PatternForLowerCaseWord);
      }
      sb.append(PatternForAnything);
      p = Pattern.compile(sb.toString());
    }
    return p;
  }

  private static Pattern fromCacheOrCompile(String target) {
    Pattern pattern = cachedPatterns.get(target);
    if (isNull(pattern)) {
      List<String> r = Splitter.onPattern(PatternForSplitByUpper).splitToList(target);
      pattern = compilePattern(r);
      if (!isNull(pattern)) {
        cachedPatterns.put(target, pattern);
      }
    }
    return pattern;
  }

  public static String getChecksum(final String s) throws IOException {
    try {

      final MessageDigest md = MessageDigest.getInstance(ALGORITHM_SHA_256);
      try (final InputStream is = new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
          DigestInputStream dis = new DigestInputStream(is, md)) {

        final byte[] buf = new byte[4096];

        while (dis.read(buf) != -1) {}

        final byte[] digest = md.digest();

        final StringBuilder sb = new StringBuilder(64);
        for (final int b : digest) {
          sb.append(Character.forDigit(b >> 4 & 0xF, 16));
          sb.append(Character.forDigit(b & 0xF, 16));
        }

        return sb.toString();
      }
    } catch (NoSuchAlgorithmException e) {
      throw new IOException(e);
    }
  }
}
