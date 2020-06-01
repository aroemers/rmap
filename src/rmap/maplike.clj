(ns rmap.maplike
  "Associative datastructure for working with recursive maps.
  EXPERIMENTAL, CAN BE CHANGED OR REMOVED ANYTIME AT THIS POINT."
  (:require [clojure.pprint :refer [simple-dispatch]]
            [rmap.core :refer [valuate-keys!]]))

(deftype RMap [cache]
  clojure.lang.IFn
  (invoke [this key]
    (.valAt this key nil))
  (invoke [this key not-found]
    (.valAt this key not-found))

  clojure.lang.ILookup
  (valAt [this key]
    (.valAt this key nil))
  (valAt [this key not-found]
    (swap! cache valuate-keys! key)
    (get @cache key not-found))

  clojure.lang.Associative
  (containsKey [this key]
    (contains? @cache key))
  (entryAt [this key]
    (clojure.lang.MapEntry. key (.valAt this key)))

  clojure.lang.IPersistentCollection
  (empty [this]
    (empty @cache))

  clojure.lang.Indexed
  (nth [this i]
    (.valAt this key nil))
  (nth [this i not-found]
    (.valAt this key not-found))

  clojure.lang.Seqable
  (seq [this]
    (seq (if (map? @cache)
           (map #(.entryAt this %) (keys @cache))
           (map #(.valAt this %) (range (count @cache)))))))

(defmethod print-method RMap [rmap ^java.io.Writer writer]
  (.write writer (str "#<RMap: " (pr-str @(.cache rmap)) ">")))

(defmethod simple-dispatch RMap [rmap]
  (print-method rmap *out*))

(defn maplike
  "Takes a normal Clojure map or vector and wraps it in an RMap.
  An RMap is a read-only associative datastructure that evaluates RVal
  objects once requested."
  [m]
  (->RMap (atom m)))
