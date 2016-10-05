package meghanada;

public class Gen9<K, V> {

    K key;
    V value;

    @Override
    public int hashCode() {
        A<K, V> a = new A<K, V>(key, value);
        return a.key.hashCode() ^ a.value.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return true;
    }

    static class A<K, V> {
        K key;
        V value;

        public A(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }
}
