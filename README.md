# photon-xtdb-starter

An example [Photon](https://github.com/hyperfiddle/photon) app using XTDB. Adapted from [xtdb-in-a-box](https://github.com/xtdb/xtdb-in-a-box)

## Setup

- clone this repository
- clone photon repository next to this one 

## Running

### From command line

```shell
XTDB_ENABLE_BYTEUTILS_SHA1=true clj
```
Will:
- start an nREPL
- compile cljs
- serve the app at http://localhost:8080

### From your editor (jack-in) 

Make sure you set the `XTDB_ENABLE_BYTEUTILS_SHA1` environnement variable to `true` before jacking-in.

## Loading data

In ns `app.core`, evaluate these at the REPL:

```clojure
(xt/submit-tx xtdb-node [[::xt/put {:xt/id "9" :user/name "alice"}]])
(xt/submit-tx xtdb-node [[::xt/put {:xt/id "10" :user/name "bob"}]])
(xt/submit-tx xtdb-node [[::xt/put {:xt/id "11" :user/name "charlie"}]])
```
