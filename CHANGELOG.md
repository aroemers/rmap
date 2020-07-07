## Changelog

### 2.2.0 - Post-processing function now receives map entry.

#### Updated

- **BREAKING** The post-processing argument to `valuate!` (and by extension `rmap!`) now receives a `clojure.lang.MapEntry` as its argument, instead of only the value.
  This way you have access to the key currently being valuated.


### 2.1.1 - Add actual ref function

#### Added

- An actual `ref` function has been added, such that it is clearer when it can be used:
  using it outside the evaluation of a recursive value will throw an unbound exception.
  In other words, you can only use it inside the `rval` or `rmap` macro.
- The `rmap` macro now takes existing (non-literal) maps and vectors.

#### Deprecated

- Now that the `rmap` macro also supports the role of `->rmap`, the latter is deprecated.

#### Removed

- The `ref` function is not implicitly available anymore.
  You will have to use the actual `rmap.core/ref` function.
  Updating your require to refer to the new `ref` function will make it work backwards compatible.
  Do note that the `ref` function is only bound during evaluation of an rval.
  If you want to use the `ref` function at a later point, you should bind it locally.


### 2.1.0 - Add #rmap/ref tagged literal

#### Added

- The tagged literal `#rmap/ref` is now supported, for fully data driven recursive maps.
- The function `->rmap` is added, to transforms an existing map to a recursive map.
- The `valuate!` function now takes an optional post-evaluation wrapper function.


### 2.0.0 - BREAKING, new API with new semantics

Completely revamped library after six years.
See README for details.

For the changelog on former versions, see older commits.
