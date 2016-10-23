package cz.alenkacz.marathon.scaler

import java.time.Duration
import java.time.temporal.TemporalUnit
import java.util.concurrent.TimeUnit

import com.typesafe.config.{Config, ConfigFactory}
import com.rabbitmq.client._

import scala.collection.JavaConversions._
import com.typesafe.scalalogging.StrictLogging
import mesosphere.marathon.client.{Marathon, MarathonClient}
import cz.alenkacz.marathon.scaler.MarathonProxy._

object Main extends StrictLogging {

  private def isOverLimit(rmqConnection: Channel, queueName: String, maxMessagesCount: Int) = rmqConnection.messageCount(queueName) > maxMessagesCount

  private def rmqConnect(rabbitMqConfig: Config) = {
    val rmqConnectionFactory: ConnectionFactory = {
      val factory = new ConnectionFactory()
      factory.setAutomaticRecoveryEnabled(true)

      factory.setVirtualHost(rabbitMqConfig.getString("vhost"))

      factory
    }
    val servers = rabbitMqConfig.getStringList("servers").map(url => new Address(url)).toArray
    logger.debug(s"RabbitMq servers: ${servers.mkString(",")}")
    val rmqConnection = rmqConnectionFactory.newConnection(servers)
    rmqConnection.createChannel()
  }

  def checkAndScale(applications: Seq[Application], rmqChannel: Channel, marathonClient: Marathon): Unit = {
    applications.foreach(app => {
      isOverLimit(rmqChannel, app.queueName, app.maxMessagesCount) match {
        case true =>
          logger.info(s"Application's ${app.name} queue '${app.queueName}' is over limit, app will be scaled up")
          scaleUp(marathonClient, app.name, app.maxInstancesCount)
        case false =>
          logger.info(s"No need to scale ${app.name}. Queue bounds are inside the limits.")
      }
    })
  }

  def main(args: Array[String]): Unit = {
    logger.debug("Loading application")
    val config = ConfigFactory.load()
    val rmqChannelConnection = rmqConnect(config.getConfig("rabbitMq"))
    logger.debug("Connected to rabbitMq server")
    val marathonConfig = config.getConfig("marathon")
    val marathonClient = MarathonClient.getInstance(marathonConfig.getString("url"))
    logger.debug("Connected to marathon server")
    val applications = config.getConfigList("applications").map(a => Application(a.getString("name"), a.getString("queue"), a.getInt("maxMessagesCount"), a.getOptionalInt("maxInstancesCount")))
    logger.info(s"Loaded ${applications.length} applications")

    val secondsToCheckLabels = marathonConfig.getOptionalDuration("labelsCheckPeriod").getOrElse(Duration.ofMinutes(1))
    var autoscaleLabeledApps = findAppsWithAutoscaleLabels(marathonClient)
    while (true) {
      val startTime = System.currentTimeMillis()
      autoscaleLabeledApps = if (secondsToCheckLabels.getSeconds <= 0) findAppsWithAutoscaleLabels(marathonClient) else autoscaleLabeledApps
      checkAndScale(autoscaleLabeledApps ++ applications, rmqChannelConnection, marathonClient)

      Thread.sleep(1000)
      secondsToCheckLabels.minus(Duration.ofMillis(System.currentTimeMillis() - startTime))
    }
  }

  implicit class RichConfig(val underlying: Config) extends AnyVal {
    def getOptionalInt(path: String): Option[Int] = if (underlying.hasPath(path)) {
      Some(underlying.getInt(path))
    } else {
      None
    }

    def getOptionalDuration(path: String): Option[Duration] = if (underlying.hasPath(path)) {
      Some(underlying.getDuration(path))
    } else {
      None
    }
  }
}

