package cz.alenkacz.marathon.scaler

import java.time.Duration

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import mesosphere.marathon.client.{Marathon, MarathonClient}
import cz.alenkacz.marathon.scaler.MarathonProxy._
import cz.alenkacz.marathon.scaler.rabbitmq.Client
import cz.alenkacz.marathon.scaler.ExtendedConfig._

import collection.JavaConverters._
import scala.collection.mutable
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

  def checkAndScale(applications: Seq[Application], rmqClients: Map[String, Client], marathonClient: Marathon, isCooledDown: Application => Boolean): Unit = {
    applications.foreach(app => {
      (isOverLimit(rmqClients(app.rmqServerName), app.vhost, app.queueName, app.maxMessagesCount), isCooledDown(app)) match {
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

  private def rabbitMqConfigValid(rabbitMqConfigs: Seq[Config]) = rabbitMqConfigs.size < 2 || rabbitMqConfigs.count(c => c.getOptionalString("name").isDefined) == (rabbitMqConfigs.size - 1) // only one config can be without name

  def normalizeHttpApiEndpoint(apiEndpointUrl: String): String = apiEndpointUrl match {
    case u if u.endsWith("api/") => u
    case u if u.endsWith("api") => s"$u/"
    case u => u // probably invalid url but there is no easy way to fix it
  }

  def setupRabbitMqClients(config: Config): Map[String, Client] = {
    val rabbitMqConfigs = config.getConfigList("rabbitMq").asScala
    if (!rabbitMqConfigValid(rabbitMqConfigs)) {
      throw new InvalidConfigurationException("RabbitMq configuration cannot contain multiple rabbitMq servers without a name specified")
    }
    rabbitMqConfigs.map(rmq => rmq.getOptionalString("name").getOrElse("") -> new Client(normalizeHttpApiEndpoint(rmq.getString("httpApiEndpoint")), rmq.getString("username"), rmq.getString("password"))).toMap
  }

  def main(args: Array[String]): Unit = {
    logger.debug("Loading application")
    val config = ConfigFactory.load()
    val rmqClients = setupRabbitMqClients(config)
    logger.debug("Connected to rabbitMq server")
    val marathonConfig = config.getConfig("marathon")
    val marathonClient = MarathonClient.getInstance(marathonConfig.getString("url"))
    logger.debug("Connected to marathon server")
    val applications = getApplicationConfigurationList(config, rmqClients)
    logger.info(s"Loaded ${applications.length} applications")
    val checkIntervalMilliseconds = config.getOptionalDuration("interval").getOrElse(Duration.ofSeconds(60)).toMillis
    val cooldown = config.getOptionalInt("cooldown").getOrElse(5)
    val lastScaled = Map.empty[String, Long]

    val secondsToCheckLabels = marathonConfig.getOptionalDuration("labelsCheckPeriod").getOrElse(Duration.ofMinutes(1))
    var autoscaleLabelledApps = findAppsWithAutoscaleLabels(marathonClient, rmqClients)
    while (true) {
      val startTime = System.currentTimeMillis()
      autoscaleLabelledApps = if (secondsToCheckLabels.getSeconds <= 0) findAppsWithAutoscaleLabels(marathonClient, rmqClients) else autoscaleLabelledApps
      checkAndScale(autoscaleLabelledApps ++ applications, rmqClients, marathonClient, app => isCooledDown(app, lastScaled, System.currentTimeMillis(), checkIntervalMilliseconds, cooldown))

      Thread.sleep(checkIntervalMilliseconds)
      secondsToCheckLabels.minus(Duration.ofMillis(System.currentTimeMillis() - startTime))
    }
  }
}

class InvalidConfigurationException(message: String) extends Exception(message)

