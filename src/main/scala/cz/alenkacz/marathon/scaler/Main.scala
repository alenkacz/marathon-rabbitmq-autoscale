package cz.alenkacz.marathon.scaler

import java.time.Duration

import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.JavaConversions._
import com.typesafe.scalalogging.StrictLogging
import mesosphere.marathon.client.{Marathon, MarathonClient}
import cz.alenkacz.marathon.scaler.MarathonProxy._
import com.rabbitmq.http.client.Client

object Main extends StrictLogging {

  private def isOverLimit(rmqClient: Client, vhost: String, queueName: String, maxMessagesCount: Int) = {
    val messagesCount = rmqClient.getQueue(vhost, queueName).getTotalMessages
    logger.info(messagesCount.toString)
    messagesCount  > maxMessagesCount
  }

  def checkAndScale(applications: Seq[Application], rmqClient: Client, marathonClient: Marathon): Unit = {
    applications.foreach(app => {
      isOverLimit(rmqClient, app.vhost, app.queueName, app.maxMessagesCount) match {
        case true =>
          logger.info(s"Application's ${app.name} queue '${app.queueName}' is over limit, app will be scaled up")
          scaleUp(marathonClient, app.name, app.maxInstancesCount)
        case false =>
          logger.info(s"No need to scale ${app.name}. Queue message count is inside the limits.")
      }
    })
  }

  def main(args: Array[String]): Unit = {
    logger.debug("Loading application")
    val config = ConfigFactory.load()
    val rabbitMqConfig = config.getConfig("rabbitMq")
    val rmqClient = new Client(rabbitMqConfig.getString("httpApiEndpoint"), rabbitMqConfig.getString("username"), rabbitMqConfig.getString("password"))
    logger.debug("Connected to rabbitMq server")
    val marathonConfig = config.getConfig("marathon")
    val marathonClient = MarathonClient.getInstance(marathonConfig.getString("url"))
    logger.debug("Connected to marathon server")
    val applications = getApplicationConfigurationList(config, rmqClient)
    logger.info(s"Loaded ${applications.length} applications")

    val secondsToCheckLabels = marathonConfig.getOptionalDuration("labelsCheckPeriod").getOrElse(Duration.ofMinutes(1))
    var autoscaleLabelledApps = findAppsWithAutoscaleLabels(marathonClient, rmqClient)
    while (true) {
      val startTime = System.currentTimeMillis()
      autoscaleLabelledApps = if (secondsToCheckLabels.getSeconds <= 0) findAppsWithAutoscaleLabels(marathonClient, rmqClient) else autoscaleLabelledApps
      checkAndScale(autoscaleLabelledApps ++ applications, rmqClient, marathonClient)

      Thread.sleep(60000)
      secondsToCheckLabels.minus(Duration.ofMillis(System.currentTimeMillis() - startTime))
    }
  }

  def getApplicationConfigurationList(config: Config, rabbitMqClient: Client): Seq[Application] = {
    if (config.hasPath("applications")) {
      config.getConfigList("applications").map(a => ApplicationFactory.tryCreate(rabbitMqClient, a.getString("name"), a.getOptionalString("vhost").getOrElse("/"), a.getString("queue"), a.getInt("maxMessagesCount"), a.getOptionalInt("maxInstancesCount"))).filter(_.isSuccess).map(_.get)
    } else {
      Seq.empty
    }
  }

  implicit class RichConfig(val underlying: Config) extends AnyVal {
    def getOptionalInt(path: String): Option[Int] = if (underlying.hasPath(path)) {
      Some(underlying.getInt(path))
    } else {
      None
    }

    def getOptionalString(path: String): Option[String] = if (underlying.hasPath(path)) {
      Some(underlying.getString(path))
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

