(ns rmap.middleware.sharing
  "The structural sharing mode, encoded in middleware. This should be
  used as a replacement of the default middleware."
  (:require [rmap.internals :as int]
            [rmap.middleware.api :as api]))


(defn- fake-promise
  "Create an already realised \"promise\"."
  [val]
  (reify
    clojure.lang.IDeref
    (deref [_] val)
    clojure.lang.IPending
    (isRealized [_] true)))


(defn sharing-middleware
  "Although associating and disassociating entries in a recursive map
  returns a new instance, this middleware makes that its lazy values
  are structurally shared. This means that when an entry is realized,
  it is realized in every instance that still has this same entry."
  []
  (reify api/Middleware

    (request [_ key cont]
      ;; Get latest data and the entry promise.
      (let [p (get (api/latest-data) key ::novalue)]
        (cond
          ;; If no value is available at all, it is not in the map.
          (= p ::novalue)
          nil

          ;; Entry is already realized, return realized value.
          (realized? p)
          (deref p)

          ;; Otherwise it has not been realized yet. Thus lock on the promise.
          :otherwise
          (locking p
            ;; Within the lock, check realization again.
            (if (realized? p)
              (deref p)
              ;; Check for the *unrealized* binding. If bound, return that value instead.
              (if (bound? (var int/*unrealized*))
                int/*unrealized*
                ;; Call the continuation, leading up to the realization, and update the cache.
                (let [val (cont)]
                  (deliver p val)
                  val)))))))

    (assoc [_ key val]
      (api/update-data assoc key (fake-promise val)))

    (assoc-lazy [_ key]
      (api/update-data assoc key (promise)))

    (dissoc [_ key]
      (api/update-data dissoc key))

    (info [_]
      {:name "sharing"
       :dynamic? false})))


(defmacro sharing-rmap
  "A convenience macro for creating a recursive map with only the
  sharing middleware."
  [sym m]
  `(rmap.core/rmap ~sym ~m [(sharing-middleware)]))
