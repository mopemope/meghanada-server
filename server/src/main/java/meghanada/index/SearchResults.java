package meghanada.index;

import java.util.ArrayList;
import java.util.List;

public class SearchResults {
  public List<SearchResult> classes = new ArrayList<>();
  public List<SearchResult> methods = new ArrayList<>();
  public List<SearchResult> symbols = new ArrayList<>();
  public List<SearchResult> codes = new ArrayList<>();

  public int size() {
    int i = 0;
    i += this.classes.size();
    i += this.methods.size();
    i += this.symbols.size();
    i += this.codes.size();
    return i;
  }
}
