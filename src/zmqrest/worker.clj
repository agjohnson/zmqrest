(ns zmqrest.worker
  (:import [org.jeromq ZMQ ZMQ$Poller])
  (:require [cheshire.core :refer :all]
            [taoensso.nippy :as nippy]
            [taoensso.timbre :as timbre :refer [log debug]]
            [zmqrest.web :refer :all])
  (:import [zmqrest.web Request Response]))

; Worker router
(defrecord Router [callbacks])

(defn router
  "Router constructor"
  ([] (Router. []))
  ([cbs] (Router. cbs)))

(defn- call-route
  "Given a route `route` and request `req`, call the callback with found
  regex groups -- or without if there were no groups in the regex find"
  [route req]
  (let [cb (:cb route)
        path (:path route)
        func (:func req)
        groups (re-find (re-pattern path) func)]
    (if (vector? groups)
      ; Call with matching groups
      (cb req (rest groups))
      ; Or call without groups if none
      (cb req))))

; Dispatch request
(defn dispatch
  "Dispatch request on router"
  [router req]
  (let [callbacks (:callbacks router)
        matches (filter
                  (fn [x]
                    (and
                      (re-find
                        (re-pattern (:path x))
                        (or (:func req) ""))
                      (= (or (:action req) :get)
                         (:action x))))
                 callbacks)]
    (Response.
      (if-let [match (first matches)]
        (try
          (call-route match req)
          (catch Exception e
            {:error 500
             :msg (.getMessage e)}))
        {:error 404
         :msg "Function does not exist"}))))

; Register callback for path
(defn add-route
  "Register route on router"
  ([router path action cb]
   (update-in router
              [:callbacks]
              conj
              {:path path
               :action action
               :cb cb}))
  ([router path action cb & more]
   (apply add-route
          (add-route router path action cb)
          path
          more))
  ([router path cb] (add-route router path :get cb)))

; Spawn worker threads
(defprotocol WorkerProtocol
  (config [this])
  (start [this threads]))

(defrecord Worker [router config]
  WorkerProtocol
  (config [this]
    (merge
      {:host "tcp://127.0.0.1:32001"
       :timeout 5000}
      (:config this)))

  (start [this threads]
    (let [ctx (atom (ZMQ/context 1))]
      (dotimes [n threads]
        (debug (str "initialize worker " n))
        (future
          (let [sck (.socket @ctx ZMQ/REP)]
            (debug (str "connecting worker " n " to " (-> this .config :host)))
            (.connect sck (-> this .config :host))
            (let [poller (.poller @ctx 1)]
              (.register poller sck ZMQ$Poller/POLLIN)
              (loop []
                (debug (str "poll worker " n))
                (.poll poller (-> this .config :timeout))
                (if (.pollin poller 0)
                  (let [req (request (.recv sck 0))
                        resp (dispatch router (:data req))]
                    (debug (str "receive on worker " n ": " req))
                    (.send sck (.toZMQ resp))
                    (debug (str "send on worker " n ": " resp))))
                (recur))))))
      (assoc this :ctx ctx))))

(defn workers
  "Create threaded workers with callbacks"
  ([router] (Worker. router {}))
  ([router config] (Worker. router config)))
