(ns app.db
  "Work around XTDB protocol reloading issues by never requiring xtdb.api from an
  Electric namespace. Electric namespaces must be reloadable during development.
  xtdb.api can never be reloaded. WIP, still in discovery if we can fix on Electric side"
  (:require [missionary.core :as m]
            [xtdb.api :as xt]))

(defn query-users [db search]
  (->> (xt/q db '{:find [(pull e [:xt/id :user/name])]
                  :in [search]
                  :where [[e :user/name name]
                          [(clojure.string/includes? name search)]]}
         (or search ""))
    (map first)
    (sort-by :user/name)))

(comment (query-users user/db ""))

(defn db [!xtdb] (xt/db !xtdb)) ; wrap so electric app doesn't depend directly on xtdb.api

(defn latest-db>
  "return continuous flow of XTDB tx notifications, but only works for XTDB
  in-process mode. see https://clojurians.slack.com/archives/CG3AM2F7V/p1677432108277939?thread_ts=1677430221.688989&cid=CG3AM2F7V"
  [!xtdb]
  (->> (m/observe (fn [!]
                    (let [listener (xt/listen !xtdb {::xt/event-type ::xt/indexed-tx :with-tx-ops? true} !)]
                      #(.close listener))))
    (m/reductions {} (xt/latest-completed-tx !xtdb)) ; initial value is the latest known tx, possibly nil
    (m/relieve {})
    (m/latest (fn [{:keys [:xt/tx-time] :as ?tx}]
                (if tx-time (xt/db !xtdb {::xt/tx-time tx-time})
                            (xt/db !xtdb))))))

(comment
  ;; Evaluate these at the REPL
  ;; See the UI react to the new db state.
  (xt/submit-tx user/!xtdb [[::xt/put {:xt/id "9" :task/description "buy milk" :task/status :active}]])
  (xt/submit-tx user/!xtdb [[::xt/put {:xt/id "10" :task/description "feed baby" :task/status :active}]])
  (xt/submit-tx user/!xtdb [[::xt/put {:xt/id "11" :task/description "exercise" :task/status :active}]])
  )

(defn todo-records [db]
  (->> (xt/q db '{:find [(pull ?e [:db/id :task/description])]
                  :where [[?e :task/status]]})
    (map first)
    (sort-by :task/description)))

(defn todo-count [db]
  (count (xt/q db '{:find [?e] :in [$ ?status]
                    :where [[?e :task/status ?status]]}
           :active)))

(comment (todo-count user/db))