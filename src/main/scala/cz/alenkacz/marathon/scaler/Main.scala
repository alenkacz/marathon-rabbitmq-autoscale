package cz.alenkacz.marathon.scaler

import com.typesafe.config.{Config, ConfigFactory}
import com.rabbitmq.client._

import scala.collection.JavaConversions._
import com.typesafe.scalalogging.StrictLogging
import mesosphere.marathon.client.{Marathon, MarathonClient}

object Main extends StrictLogging {

  case class Application(name: String, queueName: String, limit: Int, maxInstancesCount: Option[Int] = None)

  private def isOverLimit(rmqConnection: Channel, queueName: String, limit: Int) = rmqConnection.messageCount(queueName) > limit

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

  def scaleUp(marathonClient: Marathon, applicationName: String, maxInstancesCount: Option[Int]): Unit = {
    val applicationState = marathonClient.getApp(applicationName).getApp
    val targetInstanceCount = Math.min(applicationState.getInstances + 1, maxInstancesCount.getOrElse(Integer.MAX_VALUE))
    logger.info(s"Current instances count of application $applicationName is ${applicationState.getInstances} and will be increased to $targetInstanceCount")

    applicationState.setInstances(targetInstanceCount)
    marathonClient.updateApp(applicationName, applicationState, true)
  }

  def checkAndScale(applications: Seq[Application], rmqChannel: Channel, marathonClient: Marathon): Unit = {
    applications.foreach(app => {
      isOverLimit(rmqChannel, app.queueName, app.limit) match {
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
    val marathonClient = MarathonClient.getInstance(config.getConfig("marathon").getString("url"))
    logger.debug("Connected to marathon server")
    val applications = config.getConfigList("applications").map(a => Application(a.getString("name"), a.getString("queue"), a.getInt("limit"), a.getOptionalInt("maxInstancesCount")))
    logger.info(s"Loaded ${applications.length} applications")

    checkAndScale(applications, rmqChannelConnection, marathonClient)
  }

  implicit class RichConfig(val underlying: Config) extends AnyVal {
    def getOptionalInt(path: String): Option[Int] = if (underlying.hasPath(path)) {
      Some(underlying.getInt(path))
    } else {
      None
    }
  }
}

