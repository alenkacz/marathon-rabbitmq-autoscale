package cz.alenkacz.marathon.scaler

import mesosphere.marathon.client.Marathon
import mesosphere.marathon.client.model.v2.GetAppsResponse
import org.junit.runner.RunWith
import org.mockito.Mockito._
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import collection.JavaConversions._

@RunWith(classOf[JUnitRunner])
class MarathonProxyTest extends TestFixture with MockitoSugar {
  it should "parse configuration from marathon labels" in { fixture =>
    val marathonMock = mock[Marathon]
    when(marathonMock.getApps).thenReturn(appWithLabel("labeled-app"))
    val actual = MarathonProxy.findAppsWithAutoscaleLabels(marathonMock, fixture.rmqClient)

    actual.length should be (1)
    actual.head.name should be ("labeled-app")
  }

  it should "return empty apps when no interesting labels found" in { fixture =>
    val marathonMock = mock[Marathon]
    when(marathonMock.getApps).thenReturn(appWithoutLabel("labeled-app"))
    val actual = MarathonProxy.findAppsWithAutoscaleLabels(marathonMock, fixture.rmqClient)

    actual.isEmpty should be (true)
  }

  it should "not consider applications with non-existing queues" in { fixture =>
    val marathonMock = mock[Marathon]
    when(marathonMock.getApps).thenReturn(appWithLabel("labeled-app", "non-existing-queue"))
    val actual = MarathonProxy.findAppsWithAutoscaleLabels(marathonMock, fixture.rmqClient)

    actual.isEmpty should be (true)
  }

  private def appWithLabel(appName: String, queueName: String = "test"): GetAppsResponse = {
    val response = new GetAppsResponse
    val app = new mesosphere.marathon.client.model.v2.App
    app.setId(appName)
    app.setLabels(Map(MarathonProxy.QUEUE_LABEL_NAME -> queueName, MarathonProxy.MAX_MESSAGES_LABEL_NAME -> "10"))
    response.setApps(List(app))
    response
  }

  private def appWithoutLabel(appName: String): GetAppsResponse = {
    val response = new GetAppsResponse
    val app = new mesosphere.marathon.client.model.v2.App
    app.setId(appName)
    response.setApps(List(app))
    response
  }
}
