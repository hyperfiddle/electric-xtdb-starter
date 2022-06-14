(ns app.core
  (:require #?(:clj [clojure.java.io :as io])
            #?(:clj [xtdb.api :as xt]
               :cljs [xtdb.api :as-alias xt])
            [hyperfiddle.photon :as p]
            [hyperfiddle.photon-dom :as dom]
            [missionary.core :as m]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str])
  (:import (hyperfiddle.photon Pending))
  #?(:cljs (:require-macros app.core)))

#?(:clj (assert (= "true" (System/getenv "XTDB_ENABLE_BYTEUTILS_SHA1"))))

#?(:clj
   (defn start-xtdb! [] ;; from XTDB’s getting started: xtdb-in-a-box
     (letfn [(kv-store [dir]
               {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
	                         :db-dir      (io/file dir)
                           :sync?       true}})]
       (xt/start-node
        {:xtdb/tx-log         (kv-store "data/dev/tx-log")
	       :xtdb/document-store (kv-store "data/dev/doc-store")
         :xtdb/index-store    (kv-store "data/dev/index-store")}))))


(defonce xtdb-node #?(:clj (start-xtdb!), :cljs nil))

#?(:clj
   (defn stop-xtdb! []
     (.close xtdb-node)))

(defn query
  "Return a task running a query in an threadpool optimized for blocking evaluation."
  [db query & args]
  #?(:clj (m/via m/blk
                 (println "Querying:" query args)
                 (try
                   (apply xt/q db query args)
                   (catch InterruptedException _
                     (println "Query cancelled"))
                   (catch Throwable err
                     (println "Query error" err))))))

(defn query-users
  "Search for users in db.
  Will query on `xtdb-node` as of `tx-time` or at the latest node time if not
  provided. Will filter by `needle` if non-nil. Return a flow that will produce
  an empty list, then the query result, and terminate."
  [xtdb-node tx-time needle]
  #?(:clj
     (let [db (if tx-time
                (xt/db xtdb-node {::xt/tx-time tx-time})
                (xt/db xtdb-node))]
       (->> (m/ap (m/? (query db '{:find [(pull e [:xt/id :user/name])]
                                   :in [needle]
                                   :where [[e :user/name name]
                                           [(clojure.string/includes? name needle)]]}
                              (or needle ""))))
            (m/reductions {} ()) ; produce an empty list until query returns.
            ))))

(defn tx-flow
  "Return a discreet flow of events from XTDB's event bus."
  [xtdb-node]
  #?(:clj (m/observe (fn [!]
                       (let [listener (xt/listen xtdb-node {::xt/event-type ::xt/indexed-tx :with-tx-ops? true} !)]
                         ;; Called with no args when observe cancels
                         #(.close listener))))))

(defn latest-tx
  "A continous flow of the latest transaction on this xtdb node.
  Starts with the result of `xt/latest-completed-tx`, then with successive
  transactions seen on the event bus."
  [xtdb-node]
  #?(:clj
     (->> (tx-flow xtdb-node)
          (m/reductions {} (xt/latest-completed-tx xtdb-node)) ;; intial value is the latest known tx
          (m/relieve {}))))

(defn pprint-str [x] (with-out-str (pprint x)))

(p/defn App []
  ~@ ;; server
  (let [tx (new (latest-tx xtdb-node))]
    ~@ ;; client
    (dom/div
     (dom/h1 (dom/text "Hello XTDB"))
     (dom/p (dom/text "Latest tx: "))
     (dom/pre (dom/text (pprint-str tx)))
     (dom/p (dom/text "All users:"))
     (let [needle (dom/input (dom/attribute "placeholder" "Filter…")
                             (->> (dom/events dom/parent "input")
                                  (m/eduction (map dom/target-value))
                                  (m/reductions {} "") ; initial value
                                  (m/relieve {}) ; we only care about the latest value of input
                                  (new)))]
       (dom/table
        (dom/thead
         (dom/th (dom/text ":xt/id"))
         (dom/th (dom/text ":user/name")))
        (dom/tbody
         (dom/for [[user] ~@(new (query-users xtdb-node (::xt/tx-time tx) needle))]
           (dom/tr
            (dom/td (dom/text (:xt/id user)))
            (dom/td (dom/text (:user/name user)))))))))))

(def app
  #?(:cljs
     (p/client
      (p/main
       (binding [dom/parent (dom/by-id "root")] ; render App under #root
         (try
           (App.)
           (catch Pending _
             (prn "App is in pending state"))))))))

(comment
  ;; Evaluate these at the REPL
  ;; See the UI react to the new db state.
  (xt/submit-tx xtdb-node [[::xt/put {:xt/id "9" :user/name "alice"}]])
  (xt/submit-tx xtdb-node [[::xt/put {:xt/id "10" :user/name "bob"}]])
  (xt/submit-tx xtdb-node [[::xt/put {:xt/id "11" :user/name "charlie"}]])
  )
