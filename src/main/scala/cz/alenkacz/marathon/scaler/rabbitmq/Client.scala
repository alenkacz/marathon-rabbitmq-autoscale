package cz.alenkacz.marathon.scaler.rabbitmq

import java.net.URL
import java.security.cert.X509Certificate

import com.rabbitmq.http.client.{Client => HttpClient}
import com.typesafe.scalalogging.StrictLogging
import org.apache.http.conn.ssl.{
  SSLConnectionSocketFactory,
  SSLContextBuilder,
  SSLSocketFactory,
  TrustStrategy
}

import scala.util.{Failure, Success, Try}

class Client(apiUrl: String, username: String, password: String)
    extends StrictLogging {
  lazy val trustAllSslContext = new SSLContextBuilder()
    .loadTrustMaterial(null, new TrustStrategy() {
      override def isTrusted(chain: Array[X509Certificate],
                             authType: String): Boolean = true
    })
    .build()
  lazy val client =
    new HttpClient(new URL(apiUrl), username, password, trustAllSslContext)

  def queueExists(vhost: String, queueName: String): Try[Boolean] = {
    Try(client.getQueue(vhost, queueName) match {
      case null => false
      case _ => true
    })
  }

  def messageCount(vhost: String, queueName: String): Try[Long] = {
    val count = client.getQueue(vhost, queueName).getTotalMessages
    logger.info(s"Queue.messageCount returned $count")
    Try(count)
  }

  def purgeQueue(vhost: String, queueName: String): Unit =
    client.purgeQueue(vhost, queueName)

  case class Queue(messages: Int)
}
