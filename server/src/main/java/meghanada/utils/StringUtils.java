package meghanada.utils;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import com.google.common.base.Splitter;

import java.util.List;

import static java.util.Objects.isNull;

public class StringUtils {
  private static final StringUtils instance = new StringUtils();
  private static final Map<String, Pattern> cachedPatterns = new HashMap<>(500);

  public static StringUtils getInstance() {
    return instance;
  }

  private StringUtils() {
  }

  public boolean isMatch(String name, String target) {
    boolean matched = false;
    if (org.apache.commons.lang3.StringUtils.isEmpty(target)) {
      matched = true;
    } else {
      if (hasUpperCase(target)) {
        Pattern p = cachedPatterns.get(target);
        if (isNull(p)) {
          List<String> r = Splitter
            .onPattern("(?=\\p{Upper})")
            .splitToList(target);
          StringBuilder sb = new StringBuilder();
          if (r.size() > 0) {
            for (String s : r) {
              sb.append(s).append("[^A-Z]*");
            }
            sb.append(".*");
            p = Pattern.compile(sb.toString());
            cachedPatterns.put(target, p);
          }
        }
        if (isNull(p)) {
          matched = name.contains(target);
        } else {
          Matcher m = p.matcher(name);
          matched = m.matches();
        }
      } else {
        matched = matchWhenAllLowerCaseLetters(name, target);
      }
    }
    return matched;
  }

  private boolean hasUpperCase(String target) {
    return !target.equals(target.toLowerCase());
  }

  private boolean matchWhenAllLowerCaseLetters(String name, String target) {
    return name.startsWith(target);
  }
}
