package meghanada.completion.matcher;

import java.util.Comparator;
import java.util.function.Predicate;
import meghanada.reflect.CandidateUnit;
import meghanada.utils.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CamelCaseMatcher implements CompletionMatcher {

  private static final Logger log = LogManager.getLogger(CamelCaseMatcher.class);

  private final String query;
  private CompletionMatcher baseMatcher;

  public CamelCaseMatcher(String query) {
    this.query = query;
    this.baseMatcher = new PrefixMatcher(query, true);
  }

  @Override
  public boolean match(CandidateUnit c) {
    String name = c.getName();
    return StringUtils.isMatchCamelCase(name, this.query);
  }

  @Override
  public Predicate<CandidateUnit> filter() {
    return this::match;
  }

  @Override
  public Comparator<CandidateUnit> comparator() {
    return this.baseMatcher.comparator();
  }
}
