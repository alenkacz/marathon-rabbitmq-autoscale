package cz.alenkacz.marathon.scaler

import com.rabbitmq.http.client.Client
import com.typesafe.scalalogging.StrictLogging

import scala.util.{Failure, Success, Try}

trait Application {
  def name: String
  def vhost: String
  def queueName: String
  def maxMessagesCount: Int
  def maxInstancesCount: Option[Int]
}

object ApplicationFactory extends StrictLogging {
  def tryCreate(rabbitMqClient: Client, name: String, vhost: String, queueName: String, maxMessagesCount: Int, maxInstancesCount: Option[Int] = None): Try[Application] = {
    rabbitMqClient.getQueue(vhost, queueName) match {
      case null =>
        logger.warn(s"Queue '$queueName' for application '$name' does not exist. Ignoring this application configuration.")
        Failure(new Exception(s"Queue '$queueName' for application '$name' does not exist."))
      case _ => Success(ApplicationImpl(name, vhost, queueName, maxMessagesCount, maxInstancesCount))
    }
  }

  private case class ApplicationImpl(name: String, vhost: String, queueName: String, maxMessagesCount: Int, maxInstancesCount: Option[Int] = None) extends Application
}
