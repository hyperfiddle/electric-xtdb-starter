(ns app.todo-list
  (:import (hyperfiddle.electric Pending))
  (:require [clojure.pprint :refer [pprint]]
            clojure.string
            #?(:clj [clojure.java.io :as io])
            [xtdb.api #?(:clj :as :cljs :as-alias) xt]
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]
            [missionary.core :as m]))

#?(:clj (assert (= "true" (System/getenv "XTDB_ENABLE_BYTEUTILS_SHA1"))))

#?(:clj
   (defn start-xtdb! [] ; from XTDB’s getting started: xtdb-in-a-box
     (letfn [(kv-store [dir]
               {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                           :db-dir (io/file dir)
                           :sync? true}})]
       (xt/start-node
         {:xtdb/tx-log (kv-store "data/dev/tx-log")
          :xtdb/document-store (kv-store "data/dev/doc-store")
          :xtdb/index-store (kv-store "data/dev/index-store")}))))

#?(:clj (defonce !xtdb (start-xtdb!))) (comment (.close !xtdb))

(e/defn QueryUsers [!xtdb tx-time needle]
  (let [db (if tx-time
             (xt/db !xtdb {::xt/tx-time tx-time})
             (xt/db !xtdb))]
    (try
      (e/offload
        #(xt/q db '{:find [(pull e [:xt/id :user/name])]
                    :in [needle]
                    :where [[e :user/name name]
                            [(clojure.string/includes? name needle)]]}
           (or needle "")))
      (catch Pending _ [])))) ; empty list of users while query is pending.

#?(:clj (defn tx-flow [!xtdb]
          (m/observe (fn [!]
                       (let [listener (xt/listen !xtdb {::xt/event-type ::xt/indexed-tx :with-tx-ops? true} !)]
                         #(.close listener))))))

(e/defn LatestTx [!xtdb]
  (->> (tx-flow !xtdb)
    (m/reductions {} (xt/latest-completed-tx !xtdb)) ; intial value is the latest known tx
    (m/relieve {})
    (new)))

(defn pprint-str [x] (with-out-str (pprint x))) ; with-out-str is not electric compatible

(e/defn Todo-list []
  (e/server
    (let [tx (LatestTx. !xtdb)]
      (e/client
        (dom/div
          (dom/h1 (dom/text "Hello XTDB"))
          (dom/p (dom/text "Latest tx: "))
          (dom/pre (dom/text (pprint-str tx)))
          (dom/p (dom/text "All users:"))
          (let [needle (ui/input (e/fn [v] v)
                         (dom/props {:placeholder "Filter…"}))]
            (dom/table
              (dom/thead
                (dom/th (dom/text ":xt/id"))
                (dom/th (dom/text ":user/name")))
              (dom/tbody
                (e/for [[user] (e/server (QueryUsers. !xtdb (::xt/tx-time tx) needle))]
                  (dom/tr
                    (dom/td (dom/text (:xt/id user)))
                    (dom/td (dom/text (:user/name user)))))))))))))

(comment
  ;; Evaluate these at the REPL
  ;; See the UI react to the new db state.
  (xt/submit-tx !xtdb [[::xt/put {:xt/id "9" :user/name "alice"}]])
  (xt/submit-tx !xtdb [[::xt/put {:xt/id "10" :user/name "bob"}]])
  (xt/submit-tx !xtdb [[::xt/put {:xt/id "11" :user/name "charlie"}]])
  )
