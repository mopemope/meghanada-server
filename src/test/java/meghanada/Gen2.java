package meghanada;

public class Gen2<V> {

  public String name;
  public V value;

  public Gen2(String name, V value) {
    this.name = name;
    this.value = value;
    Gen2.Entry<Integer> e = new Gen2.Entry<>(1);
  }

  static class Entry<V> {
    public V value2;

    public Entry(V v) {
      this.value2 = v;
    }
  }
}
