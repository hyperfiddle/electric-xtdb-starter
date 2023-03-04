(ns app.todo-list
  (:import (hyperfiddle.electric Pending))
  (:require clojure.pprint
            clojure.string
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]
            [missionary.core :as m]
            [xtdb.api #?(:clj :as :cljs :as-alias) xt]))

(e/def !xtdb)
(e/def db)

(e/defn QueryUsers [needle]
  (try
    #_(e/offload)
    (xt/q db '{:find [(pull e [:xt/id :user/name])]
                :in [needle]
                :where [[e :user/name name]
                        [(clojure.string/includes? name needle)]]}
       (or needle ""))
    (catch Pending _ []))) ; empty list of users while query is pending.

#?(:clj
   (defn xtdb-latest> [!xtdb] ; we use ">" to denote this fn returns a missionary continuous flow
     (->> (m/observe (fn [!]
                       (let [listener (xt/listen !xtdb {::xt/event-type ::xt/indexed-tx :with-tx-ops? true} !)]
                         #(.close listener))))
       (m/reductions {} (xt/latest-completed-tx !xtdb)) ; initial value is the latest known tx
       (m/relieve {}))))

(defn pprint-str [x] (with-out-str (clojure.pprint/pprint x))) ; with-out-str is not electric compatible

(e/defn Todo-list []
  (e/server
    (binding [!xtdb user/!xtdb]
      (binding [db (xt/db !xtdb)
                   #_(let [tx-time (new (xtdb-latest> user/!xtdb))]
                     (if tx-time (xt/db !xtdb {::xt/tx-time tx-time})
                                 (xt/db !xtdb)))]
        (e/client
          (dom/div
            (dom/h1 (dom/text "Hello XTDB"))
            #_#_(dom/p (dom/text "Latest tx: "))
                    (dom/pre (dom/text (pprint-str tx-time)))
            (dom/p (dom/text "All users:"))
            (let [needle (ui/input "" (e/fn [v] v)
                           (dom/props {:placeholder "Filter…"}))]
              (dom/table
                (dom/thead
                  (dom/th (dom/text ":xt/id"))
                  (dom/th (dom/text ":user/name")))
                (dom/tbody
                  (e/for [[user] (e/server (QueryUsers. needle))]
                    (dom/tr
                      (dom/td (dom/text (:xt/id user)))
                      (dom/td (dom/text (:user/name user))))))))))))))

(comment
  ;; Evaluate these at the REPL
  ;; See the UI react to the new db state.
  (xt/submit-tx !xtdb [[::xt/put {:xt/id "9" :user/name "alice"}]])
  (xt/submit-tx !xtdb [[::xt/put {:xt/id "10" :user/name "bob"}]])
  (xt/submit-tx !xtdb [[::xt/put {:xt/id "11" :user/name "charlie"}]])
  )
