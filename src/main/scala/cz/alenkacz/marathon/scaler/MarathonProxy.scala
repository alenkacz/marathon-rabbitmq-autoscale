package cz.alenkacz.marathon.scaler

import com.typesafe.scalalogging.StrictLogging
import cz.alenkacz.marathon.scaler.rabbitmq.Client
import mesosphere.marathon.client.Marathon

import collection.JavaConverters._

object MarathonProxy extends StrictLogging {
  def scaleUp(marathonClient: Marathon, applicationName: String, maxInstancesCount: Option[Int]): Unit = {
    scale(marathonClient, applicationName, maxInstancesCount, None, (applicationState, maxInstancesCount, _) => Math.min(applicationState.getInstances + 1, maxInstancesCount.getOrElse(Integer.MIN_VALUE)))
  }

  def scaleDown(marathonClient: Marathon, applicationName: String, minInstancesCount: Option[Int]): Unit = {
    scale(marathonClient, applicationName, None, minInstancesCount, (applicationState, _, minInstancesCount) => Math.max(applicationState.getInstances - 1, minInstancesCount.getOrElse(Integer.MAX_VALUE)))
  }

  private def scale(marathonClient: Marathon, applicationName: String, maxInstancesCount: Option[Int], minInstancesCount: Option[Int], targetInstancesCount: (mesosphere.marathon.client.model.v2.App, Option[Int], Option[Int]) => Int): Unit = {
    val applicationState = marathonClient.getApp(applicationName).getApp
    val targetCount = targetInstancesCount(applicationState, maxInstancesCount, minInstancesCount)
    targetCount match {
      case newInstanceCount if targetCount != applicationState.getInstances =>
        logger.info(s"Current instances count of application $applicationName is ${applicationState.getInstances} and will be adjusted to $targetCount")

        applicationState.setInstances(targetCount)
        marathonClient.updateApp(applicationName, applicationState, true)
      case _ =>
        logger.debug(s"Application already have target (min/max) count of instances which is $targetCount")
    }
  }

  val QUEUE_LABEL_NAME = "AUTOSCALE_QUEUE"
  val VHOST_LABEL_NAME = "AUTOSCALE_VHOST"
  val MAX_MESSAGES_LABEL_NAME = "AUTOSCALE_MAXMESSAGES"
  val MAX_INSTANCES_LABEL_NAME = "AUTOSCALE_MAXINSTANCES"
  val MIN_INSTANCES_LABEL_NAME = "AUTOSCALE_MININSTANCES"
  val RMQ_SERVER_LABEL_NAME = "AUTOSCALE_RMQSERVER"

  def findAppsWithAutoscaleLabels(marathonClient: Marathon, rabbitMqClients: Map[String, Client]): Seq[Application] = {
    val labelledApps = marathonClient.getApps.getApps.asScala
      .filter(a => a.getLabels != null && a.getLabels.asScala.exists(p => p._1.equalsIgnoreCase(QUEUE_LABEL_NAME)) && a.getLabels.asScala.exists(p => p._1.equalsIgnoreCase(MAX_MESSAGES_LABEL_NAME)))
      .map(a => {
        val labels = a.getLabels.asScala
        val queueName = labels.find(_._1.equalsIgnoreCase(QUEUE_LABEL_NAME)).map(_._2.trim).get
        val vhostName = labels.find(_._1.equalsIgnoreCase(VHOST_LABEL_NAME)).map(_._2.trim).getOrElse("/")
        val maxMessagesCount = labels.find(_._1.equalsIgnoreCase(MAX_MESSAGES_LABEL_NAME)).map(_._2).get.toInt
        val maxInstancesCount = labels.find(_._1.equalsIgnoreCase(MAX_INSTANCES_LABEL_NAME)).map(_._2).map(_.toInt)
        val minInstancesCount = labels.find(_._1.equalsIgnoreCase(MIN_INSTANCES_LABEL_NAME)).map(_._2).map(_.toInt)
        val serverName = labels.find(_._1.equalsIgnoreCase(RMQ_SERVER_LABEL_NAME)).map(_._2.trim).getOrElse("")
        ApplicationFactory.tryCreate(rabbitMqClients(serverName), a.getId, serverName, vhostName, queueName, maxMessagesCount, maxInstancesCount, minInstancesCount)
      }).filter(_.isSuccess).map(_.get)

    logger.info(s"Configured following apps via marathon labels: '${labelledApps.map(a => a).mkString(",")}'")
    labelledApps
  }
}
