# marathon-rabbitmq-autoscale
[![Build Status](https://travis-ci.org/alenkacz/marathon-rabbitmq-autoscale.svg?branch=master)](https://travis-ci.org/alenkacz/marathon-rabbitmq-autoscale)

Adds possibility to automatically scale applications running in Marathon that are consuming messages from RabbitMQ. If the current number of instances starts to fall behind the number of messages that is piling up in the queues, this application will take care of increasing instances to catch up the demand.

Marathon-rabbitmq-autoscale (autoscaler) is distributed as docker image available on [Docker Hub](https://hub.docker.com/r/alenkacz/marathon-rabbitmq-autoscale/).

## How to run autoscaler
Since the application is dependent on Marathon, the most natural way to deploy it to run it in marathon. It is configured using environment variable JAVA_OPTS - it accept java command line arguments as a content (see [java documentation](https://docs.oracle.com/javase/tutorial/essential/environment/cmdLineArgs.html)).

See example configuration attached:
```json
{
  "id": "/autoscaler",
  "cpus": 1,
  "mem": 256,
  "disk": 0,
  "instances": 1,
  "container": {
    "type": "DOCKER",
    "docker": {
      "image": "alenkacz/marathon-rabbitmq-autoscale",
      "network": "BRIDGE",
      "forcePullImage": true
    }
  },
  "env": {
    "JAVA_OPTS": "-DrabbitMq.username=user -DrabbitMq.password=password -DrabbitMq.httpApiEndpoint=https://rabbitmq.yourdomain.com:15671/api -Dmarathon.url=http://marathon.yourdomain.com/"
  }
}
```

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
- *AUTOSCALE_RMQSERVER* (optional) - the RabbitMQ server name from the configuration. This allows to work with multiple RMQ servers

#### How to add label to a Marathon application
It is possible to add label from:

1. GUI
2. API

Example of api call

```json
{
    "id": "/product/service/myApp",
    "labels": {
        "AUTOSCALE_QUEUE": "queuename"
    }
}
```

### Altering autoscaler configuration
You can also configure the application directly as an autoscaler configuration. See the section 'How to run autoscaler' and add configuration via java commandline arguments. For example *-Dapps.0.name=testapp*. The format of this is determined by the choice of [configuration library](https://github.com/typesafehub/config).
