(ns the-house-edge.web.core
  "Entry point for The House Edge ClojureScript SPA"
  (:require [reagent.dom :as rdom]
            [re-frame.core :as re-frame]
            [the-house-edge.web.events :as events]
            [the-house-edge.web.subs :as subs]
            [the-house-edge.web.views :as views]))

(def config {:debug? true})

(defn dev-setup []
  (when (:debug? config)
    (println "dev mode")))

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (let [root-el (.getElementById js/document "app")]
    (rdom/render [views/main-panel] root-el)))

(defn init []
  (re-frame/dispatch-sync [:initialize-db])
  (dev-setup)
  (mount-root))
