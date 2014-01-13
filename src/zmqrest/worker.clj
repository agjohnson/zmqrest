(ns zmqrest.worker
  (:import [org.jeromq ZMQ ZMQ$Poller])
  (:require [cheshire.core :refer :all]
            [taoensso.nippy :as nippy]
            [taoensso.timbre :as timbre :refer [log debug]]
            [zmqrest.web :refer :all])
  (:import [zmqrest.web Request Response]))

; Worker router
(defprotocol RouterProtocol
  (dispatch [this req])
  (register [this path cb]))

(defrecord Router [data]
  RouterProtocol
  ; Dispatch request
  ; TODO add regex path
  (dispatch [this req]
    (let [callbacks (:callbacks this)
          matches (filter
                    (fn [x]
                      (= (:path x)
                         (:func req)))
                   callbacks)
          cb (:cb (first matches))]
      (Response.
        (if (not (nil? cb))
          (cb req)
          {:error 404
           :msg "Function does not exist"}))))
  (register [this path cb]
    (update-in this [:callbacks] conj {:path path
                                       :cb cb})))

(defn router
  "Router constructor"
  ([] (Router. []))
  ([data] (Router. data)))

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
                        resp (.dispatch router (:data req))]
                    (debug (str "receive on worker " n ": " req))
                    (.send sck (.toZMQ resp))
                    (debug (str "send on worker " n ": " resp))))
                (recur))))))
      (assoc this :ctx ctx))))

(defn workers
  "Create threaded workers with callbacks"
  ([router] (Worker. router {}))
  ([router config] (Worker. router config)))
