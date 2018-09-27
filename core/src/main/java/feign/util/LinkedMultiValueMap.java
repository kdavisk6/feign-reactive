package feign.util;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link MultiValueMap} implementation backed by a {@link LinkedHashMap} with {@link LinkedList}
 * values.  Duplicate values under a single key are allowed.
 *
 * @param <K> type of the map key.
 * @param <V> type of the values to store in the value List.
 */
public class LinkedMultiValueMap<K, V> implements MultiValueMap<K, V>, Serializable {

  private final Map<K, List<V>> target;

  public LinkedMultiValueMap() {
    this.target = new LinkedHashMap<>();
  }

  @Override
  public void add(K key, V value) {
    /* add this to the target list, if one is present */
    this.target.computeIfAbsent(key, k -> new LinkedList<>()).add(value);
  }

  @Override
  public void set(K key, V value) {
    List<V> values = new LinkedList<>();
    values.add(value);
    this.target.replace(key, values);
  }

  @Override
  public int size() {
    return this.target.size();
  }

  @Override
  public boolean isEmpty() {
    return this.target.isEmpty();
  }

  @Override
  public boolean containsKey(Object key) {
    return this.target.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return this.target.containsValue(value);
  }

  @Override
  public List<V> get(Object key) {
    return this.target.get(key);
  }

  @Override
  public List<V> put(K key, List<V> value) {
    return this.target.put(key, value);
  }

  @Override
  public List<V> remove(Object key) {
    return this.target.remove(key);
  }

  @Override
  public void putAll(Map<? extends K, ? extends List<V>> m) {
    this.target.putAll(m);
  }

  @Override
  public void clear() {
    this.target.clear();
  }

  @Override
  public Set<K> keySet() {
    return this.target.keySet();
  }

  @Override
  public Collection<List<V>> values() {
    return this.target.values();
  }

  @Override
  public Set<Entry<K, List<V>>> entrySet() {
    return this.target.entrySet();
  }
}
