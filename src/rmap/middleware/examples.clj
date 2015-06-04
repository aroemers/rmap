(ns rmap.middleware.examples
  "A namespace with middleware examples."
  (:require [rmap.middleware :as api]))


(defn chatty-middleware
  "Prints a message each time a key is requested. When putting this
  after caching middleware, such as default-middleware, the message is
  only printed when an entry is realized."
  []
  (reify api/Middleware
    (request [_ key cont] (println "Entry" key "requested.") (cont))
    (info [_] {:name "chatty" :dynamic? true})
    (assoc [_ _ _])
    (assoc-lazy [_ _])
    (dissoc [_ _])))


(defn timing-middleware
  "Calls function `(f key millis)` with the requested key and
  milliseconds it took to request the value of that key."
  [f]
  (reify api/Middleware
    (request [_ key cont]
      (let [start (System/currentTimeMillis)
            val (cont)]
        (f key (- (System/currentTimeMillis) start))
        val))
    (info [_] {:name "timing" :dynamic? true})
    (assoc [_ _ _])
    (assoc-lazy [_ _])
    (dissoc [_ _])))


(def ^{:dynamic true :private true} *deps-stack* [])
(def ^{:dynamic true :private true} *deps* nil)
(defn- conv [col val] (conj (vec col) val))

(defn dependencies-middleware
  [f]
  "Calls function `(f {key [deps] ...})` whenever a key is requested.
  The given map is a dependencies map (which can also be used by the
  `parallel-middleware`) resulting from requesting a key. When f is
  nil, the dependencies map is returned as the result of a request.

  Any caching middleware, such as the default middleware, may
  influence the results.

  Example:

  (def r (rmap X {:a 1
                  :b 2
                  :c (+ (:a X) (:b X))}))

  (add-middleware r (dependencies-middleware nil))

  (:c r)
  ;=> {:c [:a :b]}

  (:c r)
  ;=> {}       ; because of caching in default middleware

  (remove-middleware r \"dependencies\")

  (:c r)
  ;=> 3"
  (reify api/Middleware
    (request [_ key cont]
      (let [deps (atom {})
            val (binding [*deps* (or *deps* deps)]
                  (when-let [current (peek *deps-stack*)]
                    (swap! *deps* update-in [current] conv key))
                  (binding [*deps-stack* (conj *deps-stack* key)]
                    (cont)))]
        (if *deps*
          val
          (if f
            (do (f @deps) val)
            @deps))))
    (info [_] {:name "dependencies" :dynamic? true})
    (assoc [_ _ _])
    (assoc-lazy [_ _])
    (dissoc [_ _])))
