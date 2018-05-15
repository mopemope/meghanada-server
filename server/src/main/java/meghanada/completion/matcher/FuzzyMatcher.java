package meghanada.completion.matcher;

import static java.util.Objects.nonNull;

import java.util.Comparator;
import java.util.function.Predicate;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.ClassIndex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FuzzyMatcher implements CompletionMatcher {

  private static final Logger log = LogManager.getLogger(FuzzyMatcher.class);
  private String query;
  private int threshold;

  public FuzzyMatcher(String query) {
    this.query = query;
    this.threshold = (int) (query.length() * 2.0);
  }

  @Override
  public boolean match(CandidateUnit c) {
    String name = c.getName();

    boolean first = true;
    if (c instanceof ClassIndex) {
      ClassIndex index = (ClassIndex) c;
      String indexPackage = index.getPackage();
      if (indexPackage.startsWith("java")) {
        //  first = false;
      }
    }
    int score = fuzzyScore(name, this.query, first);
    return score > this.threshold;
  }

  private static int fuzzyScore(String term, String query, boolean requireFirstMatch) {

    if (nonNull(term) && nonNull(query)) {
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
    } else {
      throw new IllegalArgumentException("Strings must not be null");
    }
  }

  @Override
  public Predicate<CandidateUnit> filter() {
    return c -> {
      return match(c);
    };
  }

  @Override
  public Comparator<CandidateUnit> comparator() {
    return (o1, o2) -> {
      String name1 = o1.getName();
      String name2 = o2.getName();
      Integer i1 = fuzzyScore(name1, query, true);
      Integer i2 = fuzzyScore(name2, query, true);
      return i1.compareTo(i2);
    };
  }
}
