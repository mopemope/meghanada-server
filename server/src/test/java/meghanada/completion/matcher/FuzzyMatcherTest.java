package meghanada.completion.matcher;

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;

import java.util.Collections;
import meghanada.reflect.ClassIndex;
import org.junit.Test;

public class FuzzyMatcherTest {

  @Test
  public void match1() {
    CompletionMatcher matcher = new FuzzyMatcher("Map");
    ClassIndex c = new ClassIndex("Main", Collections.emptyList(), Collections.emptyList());
    boolean b = matcher.match(c);
    assertFalse(b);
  }

  @Test
  public void match2() {
    CompletionMatcher matcher = new FuzzyMatcher("Map");
    ClassIndex c = new ClassIndex("MallocParser", Collections.emptyList(), Collections.emptyList());
    boolean b = matcher.match(c);
    assertTrue(b);

    c = new ClassIndex("AbstractMap", Collections.emptyList(), Collections.emptyList());
    b = matcher.match(c);
    assertFalse(b);

    matcher = new FuzzyMatcher("Map", true, null);
    b = matcher.match(c);
    assertTrue(b);
  }

  @Test
  public void match3() {
    CompletionMatcher matcher = new FuzzyMatcher("is");
    ClassIndex c = new ClassIndex("isNull", Collections.emptyList(), Collections.emptyList());
    boolean b = matcher.match(c);
    assertFalse(b);
    matcher = new FuzzyMatcher("ia");
    b = matcher.match(c);
    assertFalse(b);
    matcher = new FuzzyMatcher("isN");
    b = matcher.match(c);
    assertTrue(b);
    matcher = new FuzzyMatcher("isn", 1.9);
    b = matcher.match(c);
    assertTrue(b);
  }
}
