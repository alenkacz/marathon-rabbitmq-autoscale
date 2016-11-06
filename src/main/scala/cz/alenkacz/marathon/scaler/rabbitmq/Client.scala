package cz.alenkacz.marathon.scaler.rabbitmq

import java.net.URLEncoder
import java.util.concurrent.TimeUnit

import com.google.gson.GsonBuilder
import org.asynchttpclient.{AsyncHttpClient, DefaultAsyncHttpClient, DefaultAsyncHttpClientConfig, Realm}

import scala.util.{Failure, Success, Try}

class Client(apiUrl: String, username: String, password: String) {
  lazy val client = new DefaultAsyncHttpClient(new DefaultAsyncHttpClientConfig.Builder().setAcceptAnyCertificate(true).setRequestTimeout(5000).setConnectTimeout(5000).build())

  def queueExists(vhost: String, queueName: String): Try[Boolean] = {
    val request = client.prepareGet(apiUrl + s"/queues/${URLEncoder.encode(vhost)}/$queueName").addHeader("Content-type", "application/json").setRealm(new Realm.Builder(username, password).setScheme(Realm.AuthScheme.BASIC).build()).execute()
    Try(request.get(10, TimeUnit.SECONDS).getStatusCode == 200)
  }

  def messageCount(vhost: String, queueName: String): Try[Int] = {
    val request = client.prepareGet(apiUrl + s"/queues/${URLEncoder.encode(vhost)}/$queueName").addHeader("Content-type", "application/json").setRealm(new Realm.Builder(username, password).setScheme(Realm.AuthScheme.BASIC).build()).execute()
    val gson = new GsonBuilder().create()
    Try(request.get(10, TimeUnit.SECONDS)) match {
      case Success(response) =>
        Success(gson.fromJson(response.getResponseBody, classOf[Queue]).messages)
      case Failure(e) => Failure(e)
    }
  }

  case class Queue(messages: Int)
}