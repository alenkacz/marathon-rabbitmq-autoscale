# marathon-rabbitmq-autoscale
[![Build Status](https://travis-ci.org/alenkacz/marathon-rabbitmq-autoscale.svg?branch=master)](https://travis-ci.org/alenkacz/marathon-rabbitmq-autoscale)

Adds possibility to automatically scale applications running in Marathon that are consuming messages from RabbitMQ. If the current number of instances starts to fall behind the number of messages that is piling up in the queues, this application will take care of increasing instances to catch up the demand.

Marathon-rabbitmq-autoscale (autoscaler) is distributed as docker image available on [Docker Hub](https://hub.docker.com/r/alenkacz/marathon-rabbitmq-autoscale/).

## How to run marathon-rabbitmq-autoscale
Since the application is dependent on Marathon, the most natural way to deploy it to run it in marathon. It is configured using environment variables.

## How to configure applications to be autoscaled
There are two ways how to configure application to be automatically scaled:
1. altering autoscaler configuration (via environment variables)
2. enriching app running in Marathon with predefined labels
