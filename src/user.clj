(ns user
  (:require [app.core]
            [hyperfiddle.photon-jetty-server :as server]
            [shadow.cljs.devtools.api :as shadow]
            [shadow.cljs.devtools.server :as shadow-server]))

;; shadow serves nrepl and browser assets including index.html
(defonce server (shadow-server/start!))
(defonce watch (shadow/watch :app))
(defonce websocket (server/start-server! {:host "localhost" :port 8080}))

