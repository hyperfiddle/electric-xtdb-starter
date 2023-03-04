(ns user
  (:require clojure.java.io
            [xtdb.api :as xt]))

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

(def !xtdb)

(defn main [& args]
  (println "Starting XTDB...")
  (alter-var-root #'!xtdb (constantly (start-xtdb!)))
  (comment (.close !xtdb))
  (@shadow-start!) ; serves index.html as well
  (@shadow-watch :dev) ; depends on shadow server
  )

(comment
  (type !xtdb)
  (def db (xt/db !xtdb))
  (xt/q db '{:find [(pull e [:xt/id :user/name])]
             :in [needle]
             :where [[e :user/name name]
                     [(clojure.string/includes? name needle)]]}
    "")
  )