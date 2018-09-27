package feign.util;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Extension of a Map that supports multiple values for a given Key.
 * @param <K> type of the map key.
 * @param <V> type of the value to store in the value collection.
 */
public interface MultiValueMap<K, V> extends Map<K, List<V>> {

  /**
   * Add a value under the specified key.
   *
   * @param key of the entry.
   * @param value to add to the entry.
   */
  void add(K key, V value);


  /**
   * Replace a value under the specified key.
   *
   * @param key key of the entry.
   * @param value value to set/replace.
   */
  void set(K key, V value);
}
