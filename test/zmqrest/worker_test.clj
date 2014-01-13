(ns zmqrest.worker-test
  (:require [clojure.test :refer :all]
            [zmqrest.worker :as worker]))

(deftest router-create
  (testing "Create router and register callback on router"
    (let [router (-> (worker/router [])
                     (.register
                       "test"
                       (fn [x] {:resp "Test"
                                :foobar (:foobar x)})))]
      (is (not (nil? router)))
      (are [k v req]
           (= v (-> (.normalize (.dispatch router req)) k))
           :resp "Test" {:func "test"}
           :status 200 {:func "test"}
           :error 404 {:func nil}
           :status 404 {:func nil}
           :foobar "FOOBAR" {:func "test" :foobar "FOOBAR"}))))

(deftest worker-create
  (testing "Create worker object"
    (let [router (worker/router [])
          wrk (worker/workers router)
          wrk-custom (worker/workers router {:host "tcp://127.0.0.1:33001"
                                             :timeout 10000})]
      (are [a b] (= b (a (.config wrk)))
           :host "tcp://127.0.0.1:32001"
           :timeout 5000)
      (are [a b] (= b (a (.config wrk-custom)))
           :host "tcp://127.0.0.1:33001"
           :timeout 10000))))
