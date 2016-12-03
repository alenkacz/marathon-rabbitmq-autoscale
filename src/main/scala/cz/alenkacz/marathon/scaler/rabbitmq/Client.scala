package cz.alenkacz.marathon.scaler.rabbitmq

import java.net.URL
import java.security.cert.X509Certificate

import com.rabbitmq.http.client.{Client => HttpClient}
import org.apache.http.conn.ssl.{SSLConnectionSocketFactory, SSLContextBuilder, SSLSocketFactory, TrustStrategy}

import scala.util.{Failure, Success, Try}

class Client(apiUrl: String, username: String, password: String) {
  lazy val trustAllSslContext =  new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {
    override def isTrusted(chain: Array[X509Certificate], authType: String): Boolean = true
  }).build()
  lazy val client = new HttpClient(new URL(apiUrl), username, password, trustAllSslContext)

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