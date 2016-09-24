(ns marathon-rabbitmq-autoscale.core-test
  (:require [clojure.test :refer :all]
            [langohr.queue :as lq]
            [langohr.core :as rmq]
            [langohr.channel :as lch]
            [marathon-rabbitmq-autoscale.core :refer :all]
            [marathon-client.core :as marathon]))

(def conn (rmq/connect {:uri "amqp://guest:guest@localhost:5672"}))
(def channel (lch/open conn))
(def marathon (marathon/client {:uri "http://marathon.whale.int.avast.com/"}))

(defn wait-for-rabbitmq-socket []
  (Thread/sleep 2000)
  )

(defn create-testing-queues []
  (lq/declare channel "test-queue" {:exclusive false :auto-delete true})
  )

(defn setup-rabbitmq [f]
  (wait-for-rabbitmq-socket)
  (create-testing-queues)
  (f))

(use-fixtures :once setup-rabbitmq)

(deftest basic-test
  (testing "Queue is valid when empty"
    (is (= true (queue-valid channel "test-queue" 1))))
  (testing "Marathon returns info"
    (is (= true (scale-application (marathon "test") 1)))))
