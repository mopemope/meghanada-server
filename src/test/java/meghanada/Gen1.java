package meghanada;

import java.util.List;

public class Gen1<K, V> {

    public String name;
    public K key;
    public V value;
    public List<List<? extends V>> values;

    public Gen1(String name, K key, V value) {
        this.name = name;
        this.key = key;
        this.value = value;
    }

}
