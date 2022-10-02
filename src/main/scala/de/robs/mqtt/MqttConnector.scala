package de.robs.mqtt

import java.nio.charset.StandardCharsets

import akka.Done
import com.typesafe.scalalogging.Logger
import org.eclipse.paho.client.mqttv3.{MqttClient, MqttMessage}
import spray.json.JsObject

trait MqttConnector {
  implicit val log: Logger
  val client: MqttClient
  val qos: Int
  var problemsCounter: Int = 0

  def send(json: JsObject, topic: String): Unit = {
    val content = json.compactPrint
    log.info(s"Sending ($topic) : $content")
    val message = new MqttMessage()
    message.setPayload(content.getBytes(StandardCharsets.UTF_8))
    message.setQos(qos)
    publish(topic, message)
  }

  def publish(topic: String, message: MqttMessage): Unit = {
//    retry(5){
    try {
      client.publish(topic, message)
    } catch {
      case t: Throwable =>
        log.warn("Client is fucked up", t)
        problemsCounter = 100000
    }
    //    }
  }

  def problems(): Int = problemsCounter

  @annotation.tailrec
  private def retry[T](n: Int)(fn: => T): T = {
    util.Try {
      /*
      if(!client.isConnected){
        log.warn("Client is not connected, trying to connect ...")
        client.reconnect()
        log.warn("Client connected.")
      }
      */
      if(client.isConnected){
        fn
      } else {
        log.warn("Client is not connected, trying to reconnect ...")
        try {
          client.reconnect()
        } catch {
          case t: Throwable =>
            log.warn("Client reconnection failed")
            problemsCounter = 100000
        }
        Unit.asInstanceOf[T]
      }
    } match {
      case util.Success(x) => x
      case _ if n > 1 =>
        problemsCounter = problemsCounter + 1
        val delay = (1/n) * 100000  // if n = 1 the result is 100000 ms (= 100s); if n = 50 the result is 2000 ms (= 2s)
        Thread.sleep(delay) // wait longer the more often the retry happens.
        retry(n - 1)(fn)
      case util.Failure(e) =>
        log.error(e.getMessage, e)
        problemsCounter = 100000 // ALARM
//        Unit.asInstanceOf[T]
        throw e
    }
  }
}