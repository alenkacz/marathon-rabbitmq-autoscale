package cz.alenkacz.marathon.scaler

import com.rabbitmq.client._
import cz.alenkacz.marathon.scaler.rabbitmq.Client
import org.scalatest.{Matchers, Outcome}

abstract class TestFixture extends org.scalatest.fixture.FlatSpec with Matchers {
  case class FixtureParam(rmqClients: Map[String, TestClient])
  case class TestClient(channel: Channel, apiUrl: String, username: String, password: String) extends Client(apiUrl, username, password) {
    def prepareTopology(): Unit = {
      channel.queueDeclare("test", true, false, false, null)
      channel.confirmSelect()
    }

    def close(): Unit = channel.close()
  }

  override protected def withFixture(test: OneArgTest): Outcome = {
    val fixture = FixtureParam(
      Map(
        "" -> TestClient(rmqConnect(System.getenv("RABBITMQ_HOST"), Integer.parseInt(System.getenv("RABBITMQ_TCP_5672"))), s"http://${System.getenv("RABBITMQ_HOST")}:${System.getenv("RABBITMQ_TCP_15672")}/api/", "guest", "guest"),
        "second" -> TestClient(rmqConnect(System.getenv("RABBITMQ-SECOND_HOST"), Integer.parseInt(System.getenv("RABBITMQ-SECOND_TCP_5672"))),s"http://${System.getenv("RABBITMQ-SECOND_HOST")}:${System.getenv("RABBITMQ-SECOND_TCP_15672")}/api/", "guest", "guest")
      ))
    fixture.rmqClients.values.foreach(c => c.prepareTopology())
    try super.withFixture(test.toNoArgTest(fixture))
    finally {
      fixture.rmqClients.values.foreach(c => c.close())
    }
  }

  private def rmqConnect(host: String, port: Int): Channel = {
    val rmqConnectionFactory: ConnectionFactory = {
      val factory = new ConnectionFactory()
      factory.setAutomaticRecoveryEnabled(true)
      factory.setVirtualHost("/")
      factory.setHost(host)
      factory.setPort(port)
      factory
    }
    rmqConnectionFactory.newConnection().createChannel()
  }
}
