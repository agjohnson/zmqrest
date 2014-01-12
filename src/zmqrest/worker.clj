(ns zmqrest.worker
  (:import [org.jeromq ZMQ ZMQ$Poller])
  (:require [cheshire.core :refer :all]
            [taoensso.nippy :as nippy]
            [taoensso.timbre :as timbre :refer [log debug]]
            [zmqrest.web :refer :all])
  (:import [zmqrest.web Request Response]))

; Worker types
(defprotocol WorkerProtocol
  (dispatch [this req]))

(defrecord Worker [data]
  WorkerProtocol
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
           :msg "Function does not exist"})))))

(defn register-callback [worker path cb]
  (update-in worker [:callbacks] conj {:path path
                                       :cb cb}))

(defn workers
  "Create threaded workers with callbacks"
  [worker threads]
  (let [ctx (atom (ZMQ/context 1))]
    (dotimes [n threads]
      (debug (str "initialize worker " n))
      (future
        (let [sck (.socket @ctx ZMQ/REP)]
          (debug (str "connect worker " n))
          (.connect sck "tcp://127.0.0.1:32001")
          (let [poller (.poller @ctx 1)]
            (.register poller sck ZMQ$Poller/POLLIN)
            (loop []
              (debug (str "poll worker " n))
              (.poll poller 5000)
              (if (.pollin poller 0)
                (let [req (request (.recv sck 0))
                      resp (.dispatch worker (:data req))]
                  (debug (str "receive on worker " n ": " req))
                  (.send sck (.toZMQ resp))
                  (debug (str "send on worker " n ": " resp))))
              (recur))))))
    {:ctx ctx}))
