package meghanada.utils;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.google.common.base.Splitter;

import java.util.List;

import static java.util.Objects.isNull;

public class StringUtils {
    private static final StringUtils instance = new StringUtils();

    public static StringUtils getInstance() {
        return instance;
    }

    private StringUtils() {
    }

    public boolean isMatch(String name, String target) {
        boolean matched = false;
        if (isNull(target) || "".equals(target)) {
            matched = true;
        } else {
            if (hasUpperCase(target)) {
                List<String> r = Splitter
                    .onPattern("(?=\\p{Upper})")
                    .splitToList(target);
                StringBuilder sb = new StringBuilder();
                if (r.size() > 0) {
                    for (String s : r) {
                        sb.append(s).append("[^A-Z]*");
                    }
                    sb.append(".*");
                    Pattern p = Pattern.compile(sb.toString());
                    Matcher m = p.matcher(name);
                    matched = m.matches();
                } else {
                    matched = name.contains(target);
                }
            } else {
                matched = name.startsWith(target);
            }
        }
        return matched;
    }

    private boolean hasUpperCase(String target) {
        return !target.equals(target.toLowerCase());
    }
}
