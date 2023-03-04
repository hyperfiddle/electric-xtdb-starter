(ns app.todo-list
  (:require #?(:clj app.db)
            clojure.pprint
            clojure.string
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]))

(e/def db)

(defn pprint-str [x] (with-out-str (clojure.pprint/pprint x))) ; with-out-str is not electric compatible

(e/defn Todo-list []
  (e/server
    (binding [db (new (app.db/latest-db> user/!xtdb))]
      (e/client
        (dom/link (dom/props {:rel :stylesheet :href "/todo-list.css"}))
        (dom/div
          (dom/h1 (dom/text "Hello XTDB"))
          (dom/p (dom/text "All users:"))
          (let [!filter (atom "") filter (e/watch !filter)]

            (ui/input filter (e/fn [v] (reset! !filter v))
              (dom/props {:placeholder "Filter…"}))

            (dom/dl
              (dom/dt (dom/text "filter"))
              (dom/dd (dom/text filter)))

            (dom/table
              (dom/thead
                (dom/th (dom/text ":xt/id"))
                (dom/th (dom/text ":user/name")))
              (dom/tbody
                (e/for-by :xt/id [user (e/server (e/offload #(app.db/query-users db filter)))]
                  (dom/tr
                    (dom/td (dom/text (:xt/id user)))
                    (dom/td (dom/text (:user/name user)))))))))))))
