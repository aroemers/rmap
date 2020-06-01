[![Clojars Project](https://img.shields.io/clojars/v/functionalbytes/rmap.svg)](https://clojars.org/functionalbytes/rmap)
[![cljdoc badge](https://cljdoc.org/badge/functionalbytes/rmap)](https://cljdoc.org/d/functionalbytes/rmap/CURRENT)
[![Clojure CI](https://github.com/aroemers/rmap/workflows/Clojure%20CI/badge.svg?branch=master)](https://github.com/aroemers/rmap/actions?query=workflow%3A%22Clojure+CI%22)
[![Clojars Project](https://img.shields.io/clojars/dt/functionalbytes/rmap?color=blue)](https://clojars.org/functionalbytes/rmap)
![Map](https://img.shields.io/badge/map-recursive-brightgreen)

# ➰ rmap

A Clojure library for literal recursive maps.

![Banner](banner.png)

## Usage

### The RVal object

We start with a basic building block: a recursive value.
A recursive value is an unevaluated expession, which has access to the associative datastructure - i.e. a map or a vector - it will be evaluated in.
The expression can access this datastructure using the implicit `ref` function.

A recursive value is represented in the form of an RVal object.
You can create an RVal using the `rval` macro.
Is simply takes one or more expressions as its body.
Let's create a simple Clojure map with an RVal object in it and print it:

```clj
(def my-map
  {:foo 1
   :bar (rval (inc (ref :foo)))})

my-map
;=> {:foo 1, :bar ??}
```

As you can see, the `:bar` entry is an RVal and uses the `ref` function to fetch the value mapped to `:foo`.
You can also see that no evaluation has taken place.

There is a complementary macro, called `rmap`.
It lets you create a datastructure from a literal representation, where all values are automatically RVal objects.
For example, the following creates a similar map, except that the `:foo` value is now also an RVal:

```clj
(def my-map
  (rmap {:foo 1
         :bar (inc (ref :foo))}))
;=> #'user/my-map

my-map
;=> {:foo ??, :bar ??}
```

### Valuating

To evaluate one or more RVal objects in a particular context, you can use the `valuate!` function.
It takes an associative datastructure and returns an updated version of it, where all RVal objects are evaluated.
A companion function is `valuate-keys!`.
It does the same, but only evaluates the specified keys (or indices) and their dependencies.

Let's evaluate the map we created earlier:

```clj
(valuate! my-map)
;= {:foo 1, :bar 2}

my-map
;=> {:foo ??, :bar ??}

(valuate-keys! my-map :foo)
;=> {:foo 1, :bar ??}

(valuate! (assoc my-map :foo 1001))
;=> {:foo 1001, :bar 1002}
```

You can see that the entries are evaluated now, yielding the expected results.
Also note that the original map itself has not changed and can be modified, yielding different results.

The valuation functions create a `ref` function under water.
This is used to access and evaluate the entries of the datastructure _by passing itself_ to the RVals.
Recursion! 💥
It caches the results while doing this, so each entry is only evaluated once, even if an entry is requested multiple times by other entries.

The last macro that is provided is `rmap!`.
This is the same as `rmap`, but is instantly valuated.
For example:

```clj
(rmap! {:foo 1
        :bar (inc (ref :foo))
        :baz (inc (ref :bar))})
;=> {:foo 1 :bar 2 :baz 3}
```

Maybe this `rmap!` is all you need for your purposes.
The other macros and functions are provided to give you all the tools you might need.
This way the rmap library aims to be both simple and easy.

_That's it. Enjoy!_ 🚀

## License

Copyright © 2014-2020 Functional Bytes

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
