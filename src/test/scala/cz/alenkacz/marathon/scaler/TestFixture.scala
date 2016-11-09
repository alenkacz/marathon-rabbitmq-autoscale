package cz.alenkacz.marathon.scaler

import com.rabbitmq.client._
import cz.alenkacz.marathon.scaler.rabbitmq.Client
import org.scalatest.{Matchers, Outcome}

abstract class TestFixture extends org.scalatest.fixture.FlatSpec with Matchers {
  case class FixtureParam(rmqChannel: Channel, rmqClient: Client)

  override protected def withFixture(test: OneArgTest): Outcome = {
    val fixture = FixtureParam(rmqConnect(), new Client(s"http://${System.getenv("RABBITMQ_HOST")}:${System.getenv("RABBITMQ_TCP_15672")}/api", "guest", "guest"))
    fixture.rmqChannel.queueDeclare("test", true, false, false, null)
    fixture.rmqChannel.confirmSelect()
    try super.withFixture(test.toNoArgTest(fixture))
    finally {
      fixture.rmqChannel.close()
    }
  }

  private def rmqConnect(): Channel = {
    val rmqConnectionFactory: ConnectionFactory = {
      val factory = new ConnectionFactory()
      factory.setAutomaticRecoveryEnabled(true)
      factory.setVirtualHost("/")
      factory
    }
    val rmqConnection = rmqConnectionFactory.newConnection(Array(new Address(System.getenv("RABBITMQ_HOST"), Integer.parseInt(System.getenv("RABBITMQ_TCP_5672")))))
    rmqConnection.createChannel()
  }
}
