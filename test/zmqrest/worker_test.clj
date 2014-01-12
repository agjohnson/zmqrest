(ns zmqrest.worker-test
  (:require [clojure.test :refer :all]
            [zmqrest.worker :refer :all])
  (:import [zmqrest.worker Worker]))

(deftest worker-create
  (testing "Register callback on worker"
    (let [worker (Worker. [])
          with-cb (register-callback
                    worker
                    "test"
                    (fn [x] {:resp "Test"
                             :foobar (:foobar x)}))]
      (are [k v req]
           (= v (-> (.normalize (.dispatch with-cb req)) k))
           :resp "Test" {:func "test"}
           :status 200 {:func "test"}
           :error 404 {:func nil}
           :status 404 {:func nil}
           :foobar "FOOBAR" {:func "test" :foobar "FOOBAR"}))))
