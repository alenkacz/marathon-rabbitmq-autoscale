package cz.alenkacz.marathon.scaler

import com.typesafe.config.{Config, ConfigFactory}
import com.rabbitmq.client._

import scala.collection.JavaConversions._
import com.typesafe.scalalogging.StrictLogging
import mesosphere.marathon.client.model.v2.App
import mesosphere.marathon.client.{Marathon, MarathonClient}

object Main extends StrictLogging {

  case class Application(name: String, queueName: String, limit: Int)

  private def isOverLimit(rmqConnection: Channel, queueName: String, limit: Int) = rmqConnection.messageCount(queueName) > limit

  private def rmqConnect(rabbitMqConfig: Config) = {
    val rmqConnectionFactory: ConnectionFactory = {
      val factory = new ConnectionFactory()
      factory.setAutomaticRecoveryEnabled(true)
      factory.setNetworkRecoveryInterval(rabbitMqConfig.getInt("networkRecoveryInterval"))

      factory.setUsername(rabbitMqConfig.getString("username"))
      factory.setPassword(rabbitMqConfig.getString("password"))

      factory
    }
    val rmqConnection = rmqConnectionFactory.newConnection(rabbitMqConfig.getStringList("servers").map(url => new Address(url)).toArray)
    rmqConnection.createChannel()
  }

  def scaleUp(marathonClient: Marathon, applicationName: String): Unit = {
    val applicationState = marathonClient.getApp(applicationName).getApp
    applicationState.setInstances(applicationState.getInstances + 1)
    marathonClient.updateApp(applicationName, applicationState, true)
  }

  def checkAndScale(applications: Seq[Application], rmqChannel: Channel, marathonClient: Marathon): Unit = {
    applications.foreach(app => {
      isOverLimit(rmqChannel, app.queueName, app.limit) match {
        case true => scaleUp(marathonClient, app.name)
        case false =>
      }
    })
  }

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    val rmqChannelConnection = rmqConnect(config.getConfig("rabbitMq"))
    val marathonClient = MarathonClient.getInstance(config.getConfig("marathon").getString("url"))

    checkAndScale(config.getConfigList("applications").map(a => Application(a.getString("name"), a.getString("queue"), a.getInt("limit"))), rmqChannelConnection, marathonClient)
  }
}
