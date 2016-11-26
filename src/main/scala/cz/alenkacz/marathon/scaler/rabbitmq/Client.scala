package cz.alenkacz.marathon.scaler.rabbitmq

import com.rabbitmq.http.client.{Client => HttpClient}

import scala.util.{Failure, Success, Try}

class Client(apiUrl: String, username: String, password: String) {
  lazy val client = new HttpClient(apiUrl, username, password)

  def queueExists(vhost: String, queueName: String): Try[Boolean] = {
    Try(client.getQueue(vhost, queueName) match {
      case null => false
      case _ => true
    })
  }

  def messageCount(vhost: String, queueName: String): Try[Long] = {
    Try(client.getQueue(vhost, queueName).getTotalMessages)
  }

  case class Queue(messages: Int)
}