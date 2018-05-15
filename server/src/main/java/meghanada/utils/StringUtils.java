package meghanada.utils;

import com.google.common.base.Splitter;
import meghanada.config.Config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.isNull;

public class StringUtils {

  private static final String PatternForLowerCaseWord = "[^A-Z]*";
  private static final String PatternForAnything = ".*";
  private static final String PatternForSplitByUpper = "(?=\\p{Upper})";
  private static final StringUtils instance = new StringUtils();
  private static final Map<String, Pattern> cachedPatterns = new HashMap<>(500);
  private static final Map<Integer, Boolean> cachedResult = new HashMap<>(500);

  private boolean useccc;

  void setUseccc(boolean useccc) {
    this.useccc = useccc;
  }

  public static StringUtils getInstance() {
    return instance;
  }

  private StringUtils() {
    try {
      setUseccc(Config.load().useCamelCaseCompletion());
    } catch (Exception e) {
      setUseccc(true);
    }
  }

  public boolean isMatch(String name, String target) {
    boolean matched = false;
    if (org.apache.commons.lang3.StringUtils.isEmpty(target)) {
      matched = true;
    } else if (!useccc) {
      matched = name.toLowerCase().contains(target.toLowerCase());
    } else {
      Integer key = name.hashCode()/2 + target.hashCode()/2;
      Boolean result = cachedResult.get(key);
      if (!isNull(result)) {
        matched = result;
      } else {
        if (hasUpperCase(target)) {
          Pattern pattern = fromCacheOrCompile(target);
          if (isNull(pattern)) {
            matched = name.contains(target);
          } else {
            matched = matchAndUpdateCache(pattern, name, key);
          }
        } else {
          matched = matchWhenAllLowerCaseLetters(name, target);
        }
      }
    }
    return matched;
  }

  private Boolean matchAndUpdateCache(Pattern p, String name, Integer key) {
    Matcher m = p.matcher(name);
    boolean matched = m.matches();
    cachedResult.put(key, matched);
    return matched;
  }

  private boolean hasUpperCase(String target) {
    return !target.equals(target.toLowerCase());
  }

  private boolean matchWhenAllLowerCaseLetters(String name, String target) {
    return name.startsWith(target);
  }

  private Pattern compilePattern(List<String> input) {
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

  private Pattern fromCacheOrCompile(String target) {
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
}
