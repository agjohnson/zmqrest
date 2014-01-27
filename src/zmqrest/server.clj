(ns zmqrest.server
  (:require [ring.adapter.jetty :refer :all]
            [zmqrest.backend :refer [master]])
  (:import [zmqrest.worker Router]))

(defn default-handler [req]
  {:status 200
   :content-type "text/plain"
   :body "OKAY"})

(defn -main [& args]
  (let [router (-> (Router. [])
                   (.register
                     "/api/test"
                     (fn [x] {:foobar "FOOBAR"}))
                   (.register
                     "/api/version"
                     (fn [x] {:version 1.0})))]
    (let [backend (master)
          workers (.workers backend router)]
      (.start backend)
      (.start workers 5)
      (run-jetty (->> default-handler
                      (.middleware backend))
                 {:port 8080}))))
