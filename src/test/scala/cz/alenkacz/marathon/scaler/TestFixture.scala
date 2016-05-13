package cz.alenkacz.marathon.scaler

import com.rabbitmq.client._
import org.scalatest.{Matchers, Outcome}

abstract class TestFixture extends org.scalatest.fixture.FlatSpec with Matchers {
  case class FixtureParam(rmqChannel: Channel)

  override protected def withFixture(test: OneArgTest): Outcome = {
    val fixture = FixtureParam(rmqConnect())
    fixture.rmqChannel.queueDeclare("test", true, false, false, null)
    try super.withFixture(test.toNoArgTest(fixture))
    finally {
      fixture.rmqChannel.close()
    }
  }

  private def rmqConnect(): Channel = {
    val rmqConnectionFactory: ConnectionFactory = {
      val factory = new ConnectionFactory()
      factory.setAutomaticRecoveryEnabled(true)
      factory
    }
    val rmqConnection = rmqConnectionFactory.newConnection(Array(new Address(System.getenv("RABBITMQ_HOST"), Integer.parseInt(System.getenv("RABBITMQ_TCP_5672")))))
    rmqConnection.createChannel()
  }
}
