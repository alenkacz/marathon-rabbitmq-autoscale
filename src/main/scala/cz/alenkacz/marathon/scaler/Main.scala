package cz.alenkacz.marathon.scaler

import java.time.Duration

import com.typesafe.config.{Config, ConfigFactory}

import com.typesafe.scalalogging.StrictLogging
import mesosphere.marathon.client.{Marathon, MarathonClient}
import cz.alenkacz.marathon.scaler.MarathonProxy._
import cz.alenkacz.marathon.scaler.rabbitmq.Client
import cz.alenkacz.marathon.scaler.ExtendedConfig._

import scala.util.Success

object Main extends StrictLogging {
  private def isOverLimit(rmqClient: Client, vhost: String, queueName: String, maxMessagesCount: Int): Boolean = {
    rmqClient.messageCount(vhost, queueName) match {
      case Success(count) =>
        count > maxMessagesCount
      case _ => false
    }
  }

  def isCooledDown(app: Application, lastScaled: Map[String, Long], currentTime: Long, checkPeriod: Long, coolDown: Int): Boolean = currentTime < lastScaled.getOrElse(app.name, 0l) + (checkPeriod * coolDown)

  def checkAndScale(applications: Seq[Application], rmqClient: Client, marathonClient: Marathon, isCooledDown: Application => Boolean): Unit = {
    applications.foreach(app => {
      (isOverLimit(rmqClient, app.vhost, app.queueName, app.maxMessagesCount), isCooledDown(app)) match {
        case (true, false) =>
          logger.info(s"Application's ${app.name} queue '${app.queueName}' is over limit, app will be scaled up")
          scaleUp(marathonClient, app.name, app.maxInstancesCount)
        case (true,true) =>
          logger.debug(s"Application ${app.name} is over limit but is currently in cooldown period - not scaling")
        case (false, _) =>
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
    val checkIntervalMilliseconds = config.getOptionalDuration("interval").getOrElse(Duration.ofSeconds(60)).toMillis
    val cooldown = config.getOptionalInt("cooldown").getOrElse(5)
    val lastScaled = Map.empty[String, Long]

    val secondsToCheckLabels = marathonConfig.getOptionalDuration("labelsCheckPeriod").getOrElse(Duration.ofMinutes(1))
    var autoscaleLabelledApps = findAppsWithAutoscaleLabels(marathonClient, rmqClient)
    while (true) {
      val startTime = System.currentTimeMillis()
      autoscaleLabelledApps = if (secondsToCheckLabels.getSeconds <= 0) findAppsWithAutoscaleLabels(marathonClient, rmqClient) else autoscaleLabelledApps
      checkAndScale(autoscaleLabelledApps ++ applications, rmqClient, marathonClient, app => isCooledDown(app, lastScaled, System.currentTimeMillis(), checkIntervalMilliseconds, cooldown))

      Thread.sleep(checkIntervalMilliseconds)
      secondsToCheckLabels.minus(Duration.ofMillis(System.currentTimeMillis() - startTime))
    }
  }
}

