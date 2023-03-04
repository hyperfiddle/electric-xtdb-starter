(ns app.todo-list
  (:require #?(:clj [app.db :as db])
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]))

(e/def !xtdb)
(e/def db) ; injected database ref; Electric defs are always dynamic

(e/defn TodoItem [id]
  (e/server
    (let [e (db/entity db id)
          status (:task/status e)]
      (e/client
        (dom/div
          (ui/checkbox
            (case status :active false, :done true)
            (e/fn [v]
              (e/server
                (e/discard
                  (db/submit-tx !xtdb [[:xtdb.api/put
                                        {:xt/id id
                                         :task/description (:task/description e) ; repeat
                                         :task/status (if v :done :active)}]]))))
            (dom/props {:id id}))
          (dom/label (dom/props {:for id}) (dom/text (e/server (:task/description e)))))))))

(e/defn InputSubmit [F]
  ; Custom input control using lower dom interface for Enter handling
  (dom/input (dom/props {:placeholder "Buy milk"})
    (dom/on "keydown" (e/fn [e]
                        (when (= "Enter" (.-key e))
                          (when-some [v (contrib.str/empty->nil (-> e .-target .-value))]
                            (new F v)
                            (set! (.-value dom/node) "")))))))

(e/defn TodoCreate []
  (e/client
    (InputSubmit. (e/fn [v]
                    (e/server
                      (e/discard
                        (db/submit-tx !xtdb [[:xtdb.api/put
                                              {:xt/id (random-uuid)
                                               :task/description v
                                               :task/status :active}]])))))))

(e/defn Todo-list []
  (e/server
    (binding [!xtdb user/!xtdb
              db (new (db/latest-db> user/!xtdb))]
      (e/client
        (dom/link (dom/props {:rel :stylesheet :href "/todo-list.css"}))
        (dom/h1 (dom/text "minimal todo list"))
        (dom/p (dom/text "it's multiplayer, try two tabs"))
        (dom/div (dom/props {:class "todo-list"})
          (TodoCreate.)
          (dom/div {:class "todo-items"}
            (e/server
              (e/for-by :xt/id [{:keys [xt/id]} (e/offload #(db/todo-records db))]
                (TodoItem. id))))
          (dom/p (dom/props {:class "counter"})
            (dom/span (dom/props {:class "count"})
              (dom/text (e/server (e/offload #(db/todo-count db)))))
            (dom/text " items left")))))))