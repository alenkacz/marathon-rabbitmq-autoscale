package cz.alenkacz.marathon.scaler

import com.rabbitmq.http.client.Client
import com.typesafe.scalalogging.StrictLogging
import mesosphere.marathon.client.Marathon

import collection.JavaConverters._

object MarathonProxy extends StrictLogging {
  def scaleUp(marathonClient: Marathon, applicationName: String, maxInstancesCount: Option[Int]): Unit = {
    val applicationState = marathonClient.getApp(applicationName).getApp
    val targetInstanceCount = Math.min(applicationState.getInstances + 1, maxInstancesCount.getOrElse(Integer.MAX_VALUE))
    targetInstanceCount match {
      case newInstanceCount if targetInstanceCount != applicationState.getInstances =>
        logger.info(s"Current instances count of application $applicationName is ${applicationState.getInstances} and will be increased to $targetInstanceCount")

        applicationState.setInstances(targetInstanceCount)
        marathonClient.updateApp(applicationName, applicationState, true)
      case _ =>
        logger.debug(s"Application already have target count of instances which is $targetInstanceCount")
    }
  }

  val QUEUE_LABEL_NAME = "AUTOSCALE_QUEUE"
  val VHOST_LABEL_NAME = "AUTOSCALE_VHOST"
  val MAX_MESSAGES_LABEL_NAME = "AUTOSCALE_MAXMESSAGES"
  val MAXINSTANCES_LABEL_NAME = "AUTOSCALE_MAXINSTANCES"

  def findAppsWithAutoscaleLabels(marathonClient: Marathon, rabbitMqClient: Client): Seq[Application] = {
    val labelledApps = marathonClient.getApps.getApps.asScala
      .filter(a => a.getLabels != null && a.getLabels.asScala.exists(p => p._1.equalsIgnoreCase(QUEUE_LABEL_NAME) || p._1.equalsIgnoreCase(MAX_MESSAGES_LABEL_NAME)))
      .map(a => {
        val labels = a.getLabels.asScala
        val queueName = labels.find(_._1.equalsIgnoreCase(QUEUE_LABEL_NAME)).map(_._2.trim).get
        val vhostName = labels.find(_._1.equalsIgnoreCase(VHOST_LABEL_NAME)).map(_._2.trim).getOrElse("/")
        val maxMessagesCount = labels.find(_._1.equalsIgnoreCase(MAX_MESSAGES_LABEL_NAME)).map(_._2).get.toInt
        val maxInstancesCount = labels.find(_._1.equalsIgnoreCase(MAXINSTANCES_LABEL_NAME)).map(_._2).map(_.toInt)
        ApplicationFactory.tryCreate(rabbitMqClient, a.getId, vhostName, queueName, maxMessagesCount, maxInstancesCount)
      }).filter(_.isSuccess).map(_.get)

    logger.info(s"Configured following apps via marathon labels: '${labelledApps.map(a => a).mkString(",")}'")
    labelledApps
  }
}
