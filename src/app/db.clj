(ns app.db
  "Work around XTDB protocol reloading issues by never requiring xtdb.api from an
  Electric namespace. Electric namespaces must be reloadable during development.
  xtdb.api can never be reloaded. WIP, still in discovery if we can fix on Electric side"
  (:require [missionary.core :as m]
            [xtdb.api :as xt]))

; wrap so electric app doesn't depend directly on xtdb.api, fixme
(defn db [!xtdb] (xt/db !xtdb))
(defn entity [db eid] (xt/entity db eid))
(defn submit-tx
  ([node tx-ops] (xt/submit-tx node tx-ops))
  ([node tx-ops opts] (xt/submit-tx node tx-ops opts)))

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

(defn todo-records [db]
  (->> (xt/q db '{:find [(pull ?e [:xt/id :task/description])]
                  :where [[?e :task/status]]})
    (map first)
    (sort-by :task/description)
    vec))

(comment (todo-records user/db))

(defn todo-count [db]
  (count (xt/q db '{:find [?e] :in [$ ?status]
                    :where [[?e :task/status ?status]]}
           :active)))

(comment (todo-count user/db))