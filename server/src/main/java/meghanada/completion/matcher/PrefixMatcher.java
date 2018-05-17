package meghanada.completion.matcher;

import static java.util.Objects.nonNull;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import meghanada.analyze.Source;
import meghanada.reflect.CandidateUnit;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrefixMatcher implements CompletionMatcher {

  private static final Logger log = LogManager.getLogger(PrefixMatcher.class);

  private final String query;
  private final boolean partial;
  private Source source;

  public PrefixMatcher(String query, boolean partial) {
    this(query, partial, null);
  }

  public PrefixMatcher(String query, boolean partial, Source source) {
    this.query = query;
    this.partial = partial;
    this.source = source;
  }

  private static boolean containsKeyword(
      final String keyword, final boolean partial, final CandidateUnit c) {
    String name = c.getName();
    if (ClassNameUtils.isAnonymousClass(name)) {
      return false;
    }
    if (partial) {
      return name.startsWith(keyword);
    } else {
      return name.equals(keyword)
          || name.endsWith('$' + keyword)
          || c.getDeclaration().equals(keyword);
    }
  }

  @Override
  public boolean match(CandidateUnit c) {
    return containsKeyword(this.query, this.partial, c);
  }

  @Override
  public Predicate<CandidateUnit> filter() {
    return this::match;
  }

  @Override
  public Comparator<CandidateUnit> comparator() {
    if (nonNull(this.source)) {
      return sourceComparator(this.source, this.query);
    }
    return simpleComparator(this.query);
  }

  private Comparator<CandidateUnit> sourceComparator(final Source source, final String k) {
    final Set<String> imps = new HashSet<>(source.getImportedClassMap().values());
    return (c1, c2) -> {
      String n1 = c1.getName();
      String n2 = c2.getName();
      String d1 = c1.getDeclaration();
      String d2 = c2.getDeclaration();

      if (n1.startsWith(k) && n2.startsWith(k)) {
        if (imps.contains(d1) && imps.contains(d2)) {
          return Integer.compare(n1.length(), n2.length());
        }

        if (imps.contains(d1)) {
          return -1;
        }
        if (imps.contains(d2)) {
          return 1;
        }

        return Integer.compare(n1.length(), n2.length());
      }

      if (n1.startsWith(k)) {
        return -1;
      }
      if (n2.startsWith(k)) {
        return 1;
      }
      return n1.compareTo(n2);
    };
  }

  private Comparator<CandidateUnit> simpleComparator(final String k) {
    return (c1, c2) -> {
      String o1 = c1.getName();
      String o2 = c2.getName();

      if (o1.startsWith(k) && o2.startsWith(k)) {
        return Integer.compare(o1.length(), o2.length());
      }
      if (o1.startsWith(k)) {
        return -1;
      }
      if (o2.startsWith(k)) {
        return 1;
      }
      return o1.compareTo(o2);
    };
  }
}
