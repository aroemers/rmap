## Changelog

### 0.5.0 - Middleware and smaller macros (possibly breaking, updating adviced)

In order to support more possibilities for rmap, the internals have been rewritten such that the core is just the basic functionality, which can be extended by adding middleware. Even the default behaviour is now encoded as middleware. Users of the rmap library can now write their own middleware and influence the behaviour of rmap. See the *Middleware* section in README.md for more on this.

The core macros of this library have been brought back to a minimum. This allows a significant increase in the number of entries one can define when using a literal rmap, before the compiler starts to protest. Therefore, updating to this version of rmap is adviced.

For more info on the updated and new namespaces, see the *API* in README.md section.

**New namespaces:** 

* `rmap.middleware`
* `rmap.middleware.default`
* `rmap.middleware.parallel`
* `rmap.middleware.sharing`
* `rmap.middleware.examples`

**New vars:** 

* `rmap.core/add-middleware`
* `rmap.core/add-middleware-after`
* `rmap.core/current-middlewares`
* `rmap.core/remove-middleware`
* `rmap.core/rmap*`
* `rmap.core/seq-realized`
* `rmap.core/with-unrealized`

**Updated vars:** 

* `rmap.core/rmap` - *Breaking*, second argument is now a sequence of middlewares, instead of a boolean indicating structural sharing mode. Structural sharing mode is now encoded as middleware

* `rmap.core/*unrealized*` - *Breaking*, moved to `rmap.internals/*unrealized*`. Use `rmap.core/with-unrealized` instead. The core also does not support the value `:rmap.core/ignore` anymore. This is easily re-added with middleware or functions (see `seq-realized` for example).


### 0.4.0 - Both structural sharing as per instance realization

The library internals have changed to a simpler and more memory efficient model. This model also allows adding new lazy entries to a recursive map.

In addition to that, this version supports a new realization mode: _structural sharing_ of the lazy values. When enabled, realizing an entry in a recursive map means that all structurally shared recursive maps that still have this entry will have it realized as well. This mode has to be enabled explicitly. More on this can be read in the _API_ in README.md section.

**New vars:** 

* `rmap.core/assoc-lazy`
* `rmap.core/merge-lazy`
* `rmap.core/*unrealized*`.

**Updated vars:** 

* `rmap.core/rmap` - Now supports a third argument: a boolean indicating the new _structural sharing_ mode,

* `rmap.core/seq-evalled` - Does not automatically return the entries in the order the were realized anymore.


### 0.2.0 - Full clojure.core compatibility

The recursive map can now be used with any Clojure core function.
