(ns user ; Must be ".clj" file, Clojure doesn't auto-load user.cljc
  (:require clojure.java.io
            [xtdb.api :as xt]))

; lazy load dev stuff - for faster REPL startup and cleaner dev classpath
(def start-electric-server! (delay @(requiring-resolve 'hyperfiddle.electric-jetty-server/start-server!)))
(def shadow-start! (delay @(requiring-resolve 'shadow.cljs.devtools.server/start!)))
(def shadow-watch (delay @(requiring-resolve 'shadow.cljs.devtools.api/watch)))

(defn start-xtdb! [] ; from XTDB’s getting started: xtdb-in-a-box
  (assert (= "true" (System/getenv "XTDB_ENABLE_BYTEUTILS_SHA1")))
  (letfn [(kv-store [dir] {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                                      :db-dir (clojure.java.io/file dir)
                                      :sync? true}})]
    (xt/start-node
      {:xtdb/tx-log (kv-store "data/dev/tx-log")
       :xtdb/document-store (kv-store "data/dev/doc-store")
       :xtdb/index-store (kv-store "data/dev/index-store")})))

(def electric-server-config
  {:host "0.0.0.0", :port 8080, :resources-path "resources/public"})

(def !xtdb)
(def !electric-server)

; Server-side Electric userland code is lazy loaded by the shadow build.
; WARNING: make sure your REPL and shadow-cljs are sharing the same JVM!

(defn main [& args]
  (println "Starting XTDB...")
  (alter-var-root #'!xtdb (constantly (start-xtdb!)))
  (comment (.close !xtdb))
  #_#_#_(println "Starting Electric compiler...")
  (@shadow-start!) ; serves index.html as well
  (@shadow-watch :dev) ; depends on shadow server
  #_#_(println "Starting Electric server...")
  (alter-var-root #'!electric-server (constantly (@start-electric-server! electric-server-config)))
  (comment (.stop !electric-server)))

(comment
  (main) ; Electric Clojure(JVM) REPL entrypoint
  (hyperfiddle.rcf/enable!) ; turn on RCF after all transitive deps have loaded

  (type !xtdb)
  (def db (xt/db !xtdb))
  (xt/q db '{:find [(pull e [:xt/id :user/name])]
             :in [needle]
             :where [[e :user/name name]
                     [(clojure.string/includes? name needle)]]}
    "")


  (shadow.cljs.devtools.api/repl :dev) ; shadow server hosts the cljs repl
  ; connect a second REPL instance to it
  ; (DO NOT REUSE JVM REPL it will fail weirdly)
  (type 1))