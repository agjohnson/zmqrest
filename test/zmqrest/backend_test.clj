(ns zmqrest.backend-test
  (:require [clojure.test :refer :all]
            [zmqrest.backend :as backend]))

(deftest master-create
  (testing "Create master routing process"
    (let [master (backend/master)]
      (is (not (nil? master)))
      (are [a b] (= b (a (.config master)))
           :frontend "tcp://127.0.0.1:32002"
           :backend "tcp://127.0.0.1:32001"))))
