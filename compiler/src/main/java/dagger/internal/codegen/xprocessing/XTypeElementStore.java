package dagger.internal.codegen.xprocessing;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.lang.model.element.TypeElement;

/**
 * Utility class to cache type element wrappers.
 */
final class XTypeElementStore {

  private final Function<String, TypeElement> findElement;
  private final Function<TypeElement, String> getQName;
  private final Function<TypeElement, XTypeElement> wrap;


  // instead of something like a Guava cache, we use a map of weak references here because our
  // main goal is avoiding to re-parse type elements as we go up & down in the hierarchy while
  // not necessarily wanting to preserve type elements after we are done with them. Doing that
  // could possibly hold a lot more information than we desire.
  private final Map<String, WeakReference<XTypeElement>> typeCache = new HashMap<>();

  XTypeElementStore(
      Function<String, TypeElement> findElement,
      Function<TypeElement, String> getQName,
      Function<TypeElement, XTypeElement> wrap) {
    this.findElement = findElement;
    this.getQName = getQName;
    this.wrap = wrap;
  }

  XTypeElement get(TypeElement backingType) {
    String qName = getQName.apply(backingType);
    if (qName == null) {
      // just wrap without caching, likely an error or local type in kotlin
      return wrap.apply(backingType);
    }
    XTypeElement it = get(qName);
    if (it != null) {
      return it;
    }
    XTypeElement wrapped = wrap.apply(backingType);
    return cache(qName, wrapped);
  }

  XTypeElement get(String qName) {
    WeakReference<XTypeElement> ref = typeCache.get(qName);
    if (ref != null) {
      XTypeElement it = ref.get();
      if (it != null) {
        return it;
      }
    }
    TypeElement it = findElement.apply(qName);
    if (it == null) {
      return null;
    }
    XTypeElement result = wrap.apply(it);
    return cache(qName, result);
  }

  private XTypeElement cache(String qName, XTypeElement element) {
    typeCache.put(qName, new WeakReference<>(element));
    return element;
  }

  void clear() {
    typeCache.clear();
  }
}

