(ns rmap.core)

(defmacro rmap
  "Defines a lazy, recursive map. That is, expressions in the values
  of the map can use the given symbol to access other keys within the
  map. An object of clojure.lang.AFn/ILookup/Seqable is returned.

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
         fn# (fn rmap# [key#]
               (when (contains? keyset# key#)
                 (let [~s (proxy [clojure.lang.AFn clojure.lang.ILookup] []
                            (~'invoke
                              ([k#] (rmap# k#))
                              ([k# d#] (or (rmap# k#) d#)))
                            (~'valAt
                              ([k#] (rmap# k#))
                              ([k# d#] (or (rmap# k#) d#))))
                       val# (or (get @evalled# key#)
                                (locking evalled#
                                  (or (get @evalled# key#)
                                      (get (swap! evalled# assoc key#
                                                  (or (condp = key#
                                                        ~@(for [key-and-expression m
                                                                key-or-expression key-and-expression]
                                                            key-or-expression))
                                                      ::nil))
                                           key#))))]
                   (when (not= val# ::nil) val#))))]
     (proxy [clojure.lang.AFn clojure.lang.ILookup clojure.lang.Seqable] []
       (~'invoke
         ([k#] (fn# k#))
         ([k# d#] (or (fn# k#) d#)))
       (~'valAt
         ([k#] (fn# k#))
         ([k# d#] (or (fn# k#) d#)))
       (~'seq [] (seq (for [k# keyset#] [k# (fn# k#)]))))))
