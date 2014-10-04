(ns rmap.core)

(deftype RMap [keyset evalled val-fn]
  clojure.lang.IFn
  (invoke [this key] (val-fn this key))
  (invoke [this key default] (or (val-fn this key) default))

  clojure.lang.ILookup
  (valAt [this key] (val-fn this key))
  (valAt [this key default] (or (val-fn this key) default))

  clojure.lang.Seqable
  (seq [this] (seq (for [key keyset] [key (val-fn this key)])))

  Object
  (toString [_]
    (let [evalled @evalled]
      (str "{" (->> keyset
                    (map (fn [key]
                           (str key " "
                                (if-let [val (get evalled key)]
                                  (if (= ::nil val) "nil" val)
                                  "??"))) )
                    (interpose ", ")
                    (apply str))
           "}"))))

(defmacro rmap
  "Defines a lazy, recursive map. That is, expressions in the values
  of the map can use the given symbol to access other keys within the
  map. An object of clojure.lang.IFn/ILookup/Seqable is returned.

  For example:
  (let [v 100
        k [1 2 3]
        m (rmap r {:foo 'bar/baz
                   :ns  (namespace (get r :foo))
                   :cnt (+ v (count (:ns r)))
                   k (println \"Only evaluated once, to nil.\")})]
    (:cnt m) ;=> 103
    (get m [1 2 3]) ;=> nil
    (m :nope :default) ;=> :default
    (into {} m)) ;=> {:foo bar/baz, :ns \"bar\", :cnt 103, [1 2 3] nil}"
  [s m]
  `(let [keyset# ~(set (keys m))
         evalled# (atom {})
         fn# (fn rmap# [~s key#]
               (when-let [lock# (get keyset# key#)]
                 (let [val# (or (get @evalled# key#)
                                (locking lock#
                                  (or (get @evalled# key#)
                                      (get (swap! evalled# assoc key#
                                                  (or (condp = key#
                                                        ~@(for [key-and-expression m
                                                                key-or-expression key-and-expression]
                                                            key-or-expression))
                                                      :rmap.core/nil))
                                           key#))))]
                   (when (not= val# :rmap.core/nil) val#))))]
     (rmap.core.RMap. keyset# evalled# fn#)))
