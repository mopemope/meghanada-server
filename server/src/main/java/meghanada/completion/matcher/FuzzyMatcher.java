package meghanada.completion.matcher;

import static java.util.Objects.nonNull;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import meghanada.analyze.Source;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.ClassIndex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FuzzyMatcher implements CompletionMatcher {

  private static final Logger log = LogManager.getLogger(FuzzyMatcher.class);
  private static final double SCORE_FACTOR = 2.0;

  private final String query;
  private final int threshold;
  private final boolean advanced;
  private Source source;

  public FuzzyMatcher(final String query) {
    this(query, false, null);
  }

  public FuzzyMatcher(final String query, final Double factor) {
    this.query = query;
    this.advanced = false;
    int len = query.length();
    this.threshold = (int) (len * factor);
  }

  public FuzzyMatcher(final String query, final boolean advanced, Source source) {
    this.query = query;
    this.advanced = advanced;
    int len = query.length();
    this.threshold = (int) (len * SCORE_FACTOR);
    this.source = source;
  }

  private static int fuzzyScore(String term, String query, boolean requireFirstMatch) {

    int score = 0;
    int termIndex = 0;
    int previousMatchingCharacterIndex = Integer.MIN_VALUE;
    int tlength = term.length();
    int qlength = query.length();

    for (int queryIndex = 0; queryIndex < qlength; ++queryIndex) {
      char queryChar = query.charAt(queryIndex);
      for (boolean termCharacterMatchFound = false;
          termIndex < tlength && !termCharacterMatchFound;
          ++termIndex) {

        char termChar = term.charAt(termIndex);
        if (requireFirstMatch && termIndex == 0 && queryIndex == 0 && queryChar != termChar) {
          // not match
          return 0;
        }

        if (queryChar == termChar) {
          if (termIndex == queryIndex) {
            ++score;
          }
          if (previousMatchingCharacterIndex + 1 == termIndex) {
            score += 2;
          }
          if (Character.isUpperCase(termChar)) {
            score += 2;
          }

          previousMatchingCharacterIndex = termIndex;
          termCharacterMatchFound = true;
        } else if (Character.isUpperCase(termChar)) {
          if (Character.toLowerCase(termChar) == queryChar) {
            score += 2;
          }
        }
      }
    }
    return score;
  }

  @Override
  public boolean match(CandidateUnit c) {
    String name = c.getName();
    boolean first = true;
    if (c instanceof ClassIndex) {
      ClassIndex index = (ClassIndex) c;
      String indexPackage = index.getPackage();
      // TODO allow special class
    }
    int score = fuzzyScore(name, this.query, first);
    boolean result = score > this.threshold;
    if (!result && advanced) {
      return name.contains(this.query);
    }
    return result;
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

  public Comparator<CandidateUnit> simpleComparator(final String k) {
    return (o1, o2) -> {
      String name1 = o1.getName();
      String name2 = o2.getName();
      Integer i1 = fuzzyScore(name1, k, true);
      Integer i2 = fuzzyScore(name2, k, true);
      return i1.compareTo(i2);
    };
  }

  private Comparator<CandidateUnit> sourceComparator(final Source source, final String k) {
    final Set<String> imps = new HashSet<>(source.getImportedClassMap().values());
    return (c1, c2) -> {
      String n1 = c1.getName();
      String n2 = c2.getName();
      String d1 = c1.getDeclaration();
      String d2 = c2.getDeclaration();

      if (imps.contains(d1) && imps.contains(d2)) {
        return Integer.compare(n1.length(), n2.length());
      }

      if (imps.contains(d1)) {
        return -1;
      }
      if (imps.contains(d2)) {
        return 1;
      }
      Integer i1 = fuzzyScore(n1, k, true);
      Integer i2 = fuzzyScore(n2, k, true);
      return i1.compareTo(i2);
    };
  }
}
