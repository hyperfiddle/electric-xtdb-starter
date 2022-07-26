(ns app.core
  (:require #?(:clj [clojure.java.io :as io])
            #?(:clj [xtdb.api :as xt]
               :cljs [xtdb.api :as-alias xt])
            [hyperfiddle.photon :as p]
            [hyperfiddle.photon-dom :as dom]
            [hyperfiddle.photon-ui :as ui]
            [missionary.core :as m]
            [clojure.pprint :refer [pprint]]
            [clojure.string])
  (:import (hyperfiddle.photon Pending))
  #?(:cljs (:require-macros app.core)))

#?(:clj (assert (= "true" (System/getenv "XTDB_ENABLE_BYTEUTILS_SHA1"))))

#?(:clj
   (defn start-xtdb! [] ; from XTDB’s getting started: xtdb-in-a-box
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

(p/defn QueryUsers
  "Search for users in db.
  Will query on `xtdb-node` as of `tx-time` or at the latest node time if not
  provided. Will filter by `needle` if non-nil. Will produce an empty list while
  query is pending, then the actual query result."
  [xtdb-node tx-time needle]
  (let [db (if tx-time
             (xt/db xtdb-node {::xt/tx-time tx-time})
             (xt/db xtdb-node))]
    (try
      (p/wrap xt/q db '{:find [(pull e [:xt/id :user/name])]
                        :in [needle]
                        :where [[e :user/name name]
                                [(clojure.string/includes? name needle)]]}
              (or needle ""))
      (catch Pending _
        [] ; empty list of users while query is pending.
        ))))

(defn tx-flow
  "Return a discreet flow of events from XTDB's event bus."
  [xtdb-node]
  #?(:clj (m/observe (fn [!]
                       (let [listener (xt/listen xtdb-node {::xt/event-type ::xt/indexed-tx :with-tx-ops? true} !)]
                         ;; Called with no args when observe cancels
                         #(.close listener))))))

(p/defn LatestTx
  "A continous flow of the latest transaction on this xtdb node.
  Starts with the result of `xt/latest-completed-tx`, then with successive
  transactions seen on the event bus."
  [xtdb-node]
  (->> (tx-flow xtdb-node)
       (m/reductions {} (xt/latest-completed-tx xtdb-node)) ; intial value is the latest known tx
       (m/relieve {})
       (new) ; bring missionary flow into Photon land
       ))

(defn pprint-str [x] (with-out-str (pprint x)))

(p/defn App []
  ~@ ;; server
  (let [tx (LatestTx. xtdb-node)]
    ~@ ;; client
    (dom/div
     (dom/h1 (dom/text "Hello XTDB"))
     (dom/p (dom/text "Latest tx: "))
     (dom/pre (dom/text (pprint-str tx)))
     (dom/p (dom/text "All users:"))
     (let [needle (::ui/value (ui/input {::dom/placeholder "Filter…"}))]
       (dom/table
        (dom/thead
         (dom/th (dom/text ":xt/id"))
         (dom/th (dom/text ":user/name")))
        (dom/tbody
         (p/for [[user] ~@ (QueryUsers. xtdb-node (::xt/tx-time tx) needle)]
           (dom/tr
            (dom/td (dom/text (:xt/id user)))
            (dom/td (dom/text (:user/name user)))))))))))

(def app
  #?(:cljs
     (p/boot
       (binding [dom/node (dom/by-id "root")] ; render App under #root
         (try
           (App.)
           (catch Pending _
             (prn "App is in pending state")))))))

(comment
  ;; Evaluate these at the REPL
  ;; See the UI react to the new db state.
  (xt/submit-tx xtdb-node [[::xt/put {:xt/id "9" :user/name "alice"}]])
  (xt/submit-tx xtdb-node [[::xt/put {:xt/id "10" :user/name "bob"}]])
  (xt/submit-tx xtdb-node [[::xt/put {:xt/id "11" :user/name "charlie"}]])
  )
