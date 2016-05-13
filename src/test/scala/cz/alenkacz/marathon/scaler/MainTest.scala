package cz.alenkacz.marathon.scaler

import com.rabbitmq.client.Channel
import cz.alenkacz.marathon.scaler.Main.Application
import mesosphere.marathon.client.Marathon
import mesosphere.marathon.client.model.v2.GetAppResponse
import org.junit.runner.RunWith
import org.mockito.Matchers
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._

@RunWith(classOf[JUnitRunner])
class MainTest extends TestFixture with MockitoSugar {
  it should "not call marathon when limit is not reached" in { fixture =>
    val marathonMock = mock[Marathon]
    Main.checkAndScale(Array(Application("test", "test", 10)), fixture.rmqChannel, marathonMock)

    verify(marathonMock, never()).updateApp(Matchers.any(), Matchers.any(), Matchers.any())
  }

  it should "call marathon when limit is reached" in { fixture =>
    sendMessages(fixture.rmqChannel, "test", 15)
    val marathonMock = mock[Marathon]
    when(marathonMock.getApp("test")).thenReturn(nonEmptyAppResponse())
    Main.checkAndScale(Array(Application("test", "test", 10)), fixture.rmqChannel, marathonMock)

    verify(marathonMock, atLeastOnce()).updateApp(Matchers.any(), Matchers.any(), Matchers.any())
  }

  private def sendMessages(rmqChannel: Channel, queueName: String, number: Int): Unit = {
    1 to number foreach {_ => rmqChannel.basicPublish("", queueName, false, false, null, "test".getBytes) }
  }

  private def nonEmptyAppResponse() = {
    val response = new GetAppResponse
    response.setApp(new mesosphere.marathon.client.model.v2.App)
    response.getApp.setInstances(1)
    response
  }
}