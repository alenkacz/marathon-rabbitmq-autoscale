(ns marathon-rabbitmq-autoscale.core
  (:gen-class)
  (:require [langohr.queue :as lq]
            [langohr.core :as rmq]
            [langohr.channel :as lch]
            [clojure.java.io :as io]
            [marathon-client.core :as marathon]
            [puppetlabs.config.typesafe :as ts]))

(def marathon-client
  (marathon/client {:uri "http://localhost:4343"}))

(defn scale-application [app-name]
  (inspect-app marathon-client "/" + app-name))

(defn message-count
  "Returns number of messages of a queue"
  [ch queue-name]
  (lq/message-count ch queue-name))

(defn queue-valid
  "Returns true if messages count in queue is below treshold"
  [ch queue-name treshold]
  (< (message-count ch queue-name) treshold))

(defn load-config
  "Loads YAML configuration file into clojure map"
  [file-name]
  (ts/reader->map
    (io/resource file-name)))

(defn -main
  [& args]
  (let [config (load-config "application.conf")
        conn (rmq/connect {:uri (get (get config :rabbitMQ) :connectionString)})
        ch (lch/open conn)]
    (doseq [app (get config :applications)]
      (if-not
        (queue-valid ch (get app :queue) (get app :limit)) (scale-application (get app :name))))))