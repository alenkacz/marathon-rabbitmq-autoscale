# marathon-rabbitmq-autoscale
[![Build Status](https://travis-ci.org/alenkacz/marathon-rabbitmq-autoscale.svg?branch=master)](https://travis-ci.org/alenkacz/marathon-rabbitmq-autoscale)

Adds possibility to automatically scale applications running in Marathon that are consuming messages from RabbitMQ. If the current number of instances starts to fall behind the number of messages that is piling up in the queues, this application will take care of increasing instances to catch up the demand. It also has the capability of scale the application down when there are no more messages left.

Number of instances of configured application will be increased by 1 every time treshold of *AUTOSCALE_MAXMESSAGES* is reached until *AUTOSCALE_MAXINSTANCES* is reached. In case *AUTOSCALE_MAXINSTANCES* is specified, app will be also scaled down every time the number of messages is 0. To avoid app being scaled up and down all the time, there is a cooldown period that can be specified when starting up the autoscaler container. By default the cooldown period is 5 minutes.

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
    "JAVA_OPTS": "-DrabbitMq.0.username=user -DrabbitMq.0.password=password -DrabbitMq.0.httpApiEndpoint=https://rabbitmq.yourdomain.com:15671/api -Dmarathon.url=http://marathon.yourdomain.com/"
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
- *AUTOSCALE_MININSTANCES* (optional) - by setting this property, you are enabling the scale down feature that will scale down to the count of instances specified
- *AUTOSCALE_RMQSERVER* (optional) - the RabbitMQ server name from the configuration. This allows to work with multiple RMQ servers
- *AUTOSCALE_UPCOUNT* (optional) - count of instances which are started when the limit is reached. Default value is 1.
- *AUTOSCALE_DOWNCOUNT* (optional) - count of instances which are suspended when the queue is empty and the scale down feature is enabled. Default value is 1.

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
