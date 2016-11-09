# marathon-rabbitmq-autoscale
[![Build Status](https://travis-ci.org/alenkacz/marathon-rabbitmq-autoscale.svg?branch=master)](https://travis-ci.org/alenkacz/marathon-rabbitmq-autoscale)

Adds possibility to automatically scale applications running in Marathon that are consuming messages from RabbitMQ. If the current number of instances starts to fall behind the number of messages that is piling up in the queues, this application will take care of increasing instances to catch up the demand.

Marathon-rabbitmq-autoscale (autoscaler) is distributed as docker image available on [Docker Hub](https://hub.docker.com/r/alenkacz/marathon-rabbitmq-autoscale/).

## How to run autoscaler
Since the application is dependent on Marathon, the most natural way to deploy it to run it in marathon. It is configured using environment variables.

## How to configure applications to be autoscaled
There are two ways how to configure application to be automatically scaled:

1. enriching app running in Marathon with predefined labels
2. altering autoscaler configuration (via environment variables)

The first choice is the preferred one.

### Enriching app with labels
This is the preferred choice that fits better to the environment of Marathon application. After the start, autoscaler checks all applications running in given marathon instance and configures application that contains both required labels.

After you deploy your app to Marathon, you have to add these labels:
- *AUTOSCALE_QUEUE* - this queue will be watched and app scaled based on that
- *AUTOSCALE_VHOST* (optional) - if the queue is in a different vhost than the default one "/" you have to specify it
- *AUTOSCALE_MAXMESSAGES* - when this message count is hit, number of instances of this app will be increased
- *AUTOSCALE_MAXINSTANCES* (optional) - maximum number of instances after which the app cannot be autoscaled

#### How to add label to a Marathon application
It is possible to add label from:

1. GUI
2. API

Example of api call

```json
{
    "id": "/product/service/myApp",
    "labels": {
        "LABEL_NAME": "LABEL_VALUE"
    }
}
```

### Altering autoscaler configuration
