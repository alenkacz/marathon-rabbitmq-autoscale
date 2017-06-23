package cz.alenkacz.marathon.scaler

import akka.actor.{Actor, ActorLogging}
import cz.alenkacz.marathon.scaler.RabbitMQApiActor._
import cz.alenkacz.marathon.scaler.rabbitmq.Client

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class RabbitMQApiActor(client: Client)(implicit val executionContext: ExecutionContext) extends Actor with ActorLogging {
  override def receive: Receive = {
    case MessageCountRequest(vhost, queueName) =>
      Future {
        client.messageCount(vhost, queueName)
      }.foreach {
        case Success(res) => sender() ! MessageCountResponse(res)
        case Failure(e) => throw e
      }
  }
}

object RabbitMQApiActor {
  sealed trait ApiMessage
  case class MessageCountRequest(vhost: String, queueName: String)
  case class MessageCountResponse(count: Long)
}
