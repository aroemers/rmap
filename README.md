# rmap

[![Join the chat at https://gitter.im/aroemers/rmap](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/aroemers/rmap?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

[![Build Status](https://snap-ci.com/aroemers/rmap/branch/master/build_image)](https://snap-ci.com/aroemers/rmap/branch/master)

A Clojure library designed to define literal lazy, recursive maps.

```clojure
(def m
  (rmap X
    {:what "awesome!"
     :clj (str "Clojure is " (:what X))})
(:clj m)
;=> "Clojure is awesome!"
```

## Changelog

### 0.4.0 - Both structural sharing as per instance realization

The library internals have changed to a simpler and more memory efficient model. This model also allows adding new lazy entries to a recursive map. 

In addition to that, this version supports a new realization mode: _structural sharing_ of the lazy values. When enabled, realizing an entry in a recursive map means that all structurally shared recursive maps that still have this entry will have it realized as well. This mode has to be enabled explicitly. More on this can be read in the _API_ section. 

**New API:** `assoc-lazy`, `merge-lazy` and `*unrealized*`.

**Updated API:** `rmap` and `seq-evalled`.

### 0.2.0 - Full clojure.core compatibility

The recursive map can now be used with any Clojure core function.


## Usage

### Adding to your project

Add this to your leiningen dependencies (**latest stable build**, the rest of this README may already be in the future!!1)

```clojure
[functionalbytes/rmap "0.4.0"]
```

or as a maven dependency

```xml
<dependency>
    <groupId>functionalbytes</groupId>
    <artifactId>rmap</artifactId>
    <version>0.4.0</version>
</dependency>
```

and make sure Clojars is available as one of your repositories.


### The API

#### `(rmap X {...})`

The main macro in this library is called `rmap`. It takes two (or three) arguments: a symbol which can be used to access the recursive map from within the value expressions, and the map itself. It closes over locals and arbritary keys can be used. An immutable object of type `RMap` is returned, which implements all of the necessary interfaces to act like a standard map, such as `IPersistentMap`, `IPersistentCollection`, and `IFn`. This means it can be used with all of the core functions, as a function itself (taking one or two arguments), and with keyword lookups.

Whenever an entry is realized, it is done so in the context of the given recursive map instance. A recursive map has two realization modes.

#### Per instance realization

By default, whenever a lazy entry is realized, it is only realized for that particular instance. Only descendants (like when `assoc`ing) will have the realized entry, if it has been realized at that time. This means that "parent" instances will still have the unrealized entry. 

#### Structural sharing realization

Although associating and disassociating entries returns a new recursive map instance, its lazy values can structurally shared. This is enabled by adding a truthful argument to the `rmap` macro. Enabling this means that when an entry is realized, it is realized in every instance that still has this same entry. The context however in which an entry is realized may differ. For instance, have a look at these examples:

```clojure
(let [x (rmap r {:a 1, :b (:a r)} true)]
      y (assoc x :a 2)]
   x        ;=> {:a ??, :b ??}
   y        ;=> {:a 2, :b ??}

   ;; execute :b in the context of x
   (:b x)   ;=> 1

   x        ;=> {:a 1, :b 1}
   y        ;=> {:a 2, :b 1})  ; here :b is also 1


(let [x (rmap r {:a 1, :b (:a r)} true)]
      y (assoc x :a 2)]
   x        ;=> {:a ??, :b ??}
   y        ;=> {:a 2, :b ??}

   ;; execute :b in the context of y
   (:b y)   ;=> 2

   x        ;=> {:a 1, :b 2}   ; here :b is also 2
   y        ;=> {:a 2, :b 2})
```

This mode can be useful, for being sure that a lazy entry is really only evaluated once. But be careful. If another thread realizes an entry in a different (possibly unknown) context, the value of that entry might not be what you expect in your own context. This can be a feature, or downright annoying. 

#### `(assoc-lazy rmap X key form)`

The API defines one other macro, called `assoc-lazy`. This returns a new recursive map with the given form added to the given instance, without the form being realized. The form can use the symbol `X` to refer to other entries in the recursive map it is evaluated in.

#### `(merge-lazy rmap1 rmap2 ...)`

This function merges two or more recursive maps, without realizing any unrealized entries. For example:

```clojure
(def x (rmap r {:foo 'bar/baz :ns (namespace (:foo r))}))
(def y (rmap r {:foo 'eve/baz :extra 'thing}))

(def z (merge-lazy x y))

z         ;=> {:extra ??, :ns ??, :foo ??}
(:ns z)   ;=> "eve"
```

#### `*unrealized*`

A dynamic variable is available as well, called `*unrealized*`. By default it is unbound. When it is bound, its value is used for unrealized entries whenever such entry is requested. That entry will stay unrealized. Many core library functions evaluate all entries inside a map, such as `seq` and `=`. Binding the variable to a value prevents this.

When the variable is bound to `:rmap.core/ignore`, the entry is ignored by the `seq` implementaion of `RMap`. More on lazyness and realization of entries with regard to Clojure's core functions can be found in the section _Core function on the recursive map_.

Also, asking for a specific unrealized value with `*unrealized*` bound to a value, is just silly:

```clojure
(let [x (rmap r {:foo 42})]
  (binding [*unrealized* :bar]
    (:foo x)))   ;=> :bar
```

#### `(seq-evalled rmap)`

As calling `seq` on a recursive map normally evaluates all the entries, this function only returns a seq of the currently evaluated entries. This has become a convenience function for backwards compatibility, as it has the same effect as `(binding [*unrealized* :rmap.core/ignore] (seq <rmap>))`. However, it does not return the sequence of entries in the order they were evaluated anymore.

### Example use

An example showing some of its usage (in the default _per instance_ realization mode):

```clojure
(let [v 100
      k [1 2 3]
      m (rmap r {:foo 'bar/baz                                ; simple value
                 :ns  (namespace (get r :foo))                ; single recursion
                 :cnt (+ v (count (:ns r)))                   ; using local binding, double recursion
                 k (println "Only evaluated once, to nil.")   ; arbritary key, nil value
                 :b (inc (:a r))})                            ; recursion to (not yet) existing entry

      n (assoc m :foo 'eve/baz)                               ; update :foo with non-lazy value

      o (assoc-lazy n :a 41)]                                 ; add key with lazy value

  ;; nothing realized
  m  ;=> {:foo ??, :ns ??, :cnt ??, [1 2 3] ?? :b ??}

  ;; non-lazy value added
  n  ;=> {:foo ??, :ns ??, :cnt ??, [1 2 3] ?? :b ?? :foo eve/baz}

  ;; lazy value added
  o  ;=> {:foo ??, :ns ??, :cnt ??, [1 2 3] ?? :b ?? :foo eve/baz :a ??}

  ;; realizing :foo -> :ns -> :cnt
  (:cnt m)           ;=> 103

  ;; realizing arbritary key
  (get m [1 2 3] 'NOT-SHOWN)  ;=> nil

  ;; fallback to a default
  (m :nope :default) ;=> :default

  ;; realize entry inside a recursive map that now has the recursive entry
  (:b o)             ;=> 42

  ;; get a map of what was realized until now in o
  ;; in "structural sharing" mode, the entries :foo, :ns, :cnt and [1 2 3] would also have been realized
  (binding [*unrealized* :rmap.core/ignore)]
    (into {} o))     ;=> {:b 42, :a 41}

  ;; get a map of everything realized
  (into {} o)        ;=> {:foo eve/baz, :ns "eve", :cnt 103, [1 2 3] nil, :b 42, :a 41}
```

### Immutability and state

All the functions on the recursive map return new objects, so it can be regarded as immutable. Still, keep in mind that a recursive map does have state, as it lazily realizes its values.


### Core functions on the recursive map

This subsection discusses some of the core Clojure functions, and how they work on the recursive maps. Although this is far from exhaustive, it should give a general idea of how to deal with (and possibly keep) laziness.

#### `seq`

This realizes all entries in the recursive map. This is useful for situations like converting the recursive map to a normal map, using `(into {} <rmap>)`. It may be less appropriate for situations like where you want to know all the keys in the recursive map, without realizing any unevaluated values. The standard `keys` functions uses `seq`. Therefore, binding `*unrealized*` to `:rmap.core/ignore` prevents this, and will return only the keys of realized entries. Binding the `*unrealized*` to something else, will return the keys of all entries, without realizing unrealized entries.

#### `keys`, `into`, etc

Uses `seq` in its implementation. See the subsection above.

#### `assoc`, `conj`, `without`, etc

Returns a new recursive map, with the given entry or entries added, overwritten or removed. You can use the laziness of recursive maps to introduce entries that are required by still unrealized entries. For example, note how the `:b` entry uses an `:a` entry, which is added later:

```clojure
(-> (rmap r {:b (:a r)})
    (conj [:a 42])
    :b)
;=> 42
```

#### `count`

Returns the total number of entries in the recursive map, realized or not. Use the `(count (binding [*unrealized* :rmap.core/ignore] (seq <rmap>)))` to count the number of realized entries.

#### `empty`

Returns an empty `RMap`.

#### `=`, `.equals`, `hash`, etc

Comparing or calculating a hash of a recursive map means that it will be realized in full, before the comparison or calculation is performed. Binding `*unrealized*` prevents this.

#### `merge`

When a recursive map is given as the second or later argument to `merge`, it is fully realized. When given as the first argument, it is unaffected (except for structurally shared entries of course, if that mode is enabled). To merge two or more recursive maps, while not realizing the unrealized forms, use the `merge-lazy` function as explained above.


## License

Copyright © 2014-2015 Functional Bytes

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
