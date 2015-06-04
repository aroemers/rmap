(ns rmap.middleware.api
  "This namespace contains the protocol that must be implemented by
  middlewares, and helper functions for inside those middleware
  implementations.

  Middleware can store and retrieve data in the recursive map. This
  data is persistent, meaning storing data in one instance does not
  affect the data in another instance. A new instance, due to an assoc
  or dissoc, does have the data of its ascendent at the time of the
  assoc/dissoc. Middleware has only access to its own data, using the
  `latest-data` and `update-data` functions. If required, middleware
  can access the recursive map it is applied to, by reading
  `*current-map*`.

  Middleware can either be dynamic or non-dynamic. Dynamic middleware
  means that by its nature it can be added and removed to/from a
  recursive map, without causing consistency problems. Non-dynamic
  middleware must be added while creating a recursive map.

  Note that some middlewares need to respect the
  `rmap.internals/*unrealized*` binding."
  (:refer-clojure :exclude (assoc dissoc)))


;;; Dynamic vars, mostly for internal use.

(def ^{:dynamic true
       :doc "When executing a middleware, this is bound to the
            recursive map it is applied to."}
  *current-map*)

(def ^{:dynamic true
       :doc "When executing a middleware, this is bound to its name.
            This is used for middleware data functions `latest-data`
            and `update-data`."}
  *current-middleware*)


;;; The protocol.

(defprotocol Middleware
  "The protocol for middleware implementations."

  (request [this key cont]
    "This function is called whenever an entry in the recursive map is
    requested. This action should either return a value, or the result
    of calling the continuation.")

  (assoc [this key val]
    "This function is called whenever a non-lazy value has just been
    added to the recursive map. Note that this accos has resulted in a
    new instance.")

  (assoc-lazy [this key]
    "This function is called whenever a lazy form has just been added
    to the recursive map. Note that this assoc-lazy has resulted in a
    new instance.")

  (dissoc [this key]
    "This action is used whenever an entry has just been removed from
    the recursive map. Note that this removal resulted in a new
     instance.")

  (info [this]
    "This should return a map with keys :name and :dynamic? Its values
    are the name of the middleware, and a boolean indicating whether
    the middleware is dynamic, respectively."))


;;; The API for inside middleware.

(defn latest-data
  "Returns the latest middleware data. For use inside middleware."
  []
  (get @(.datas *current-map*) *current-middleware*))

(defn update-data
  "Updates the middleware data by applying function `f` on the latest
  data, and the optional `args`. For use inside middleware."
  [f & args]
  (apply swap! (.datas *current-map*) update-in [*current-middleware*] f args))
