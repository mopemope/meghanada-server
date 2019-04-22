package meghanada.index;

import java.util.ArrayList;
import java.util.List;

public class SearchResults {
  public final List<SearchResult> classes = new ArrayList<>(8);
  public final List<SearchResult> methods = new ArrayList<>(8);
  public final List<SearchResult> symbols = new ArrayList<>(8);
  public final List<SearchResult> usages = new ArrayList<>(8);
  public final List<SearchResult> codes = new ArrayList<>(8);

  public int size() {
    int i = 0;
    i += this.classes.size();
    i += this.methods.size();
    i += this.symbols.size();
    i += this.usages.size();
    i += this.codes.size();
    return i;
  }
}
