package de.robs.mqtt

import akka.Done
import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import com.typesafe.scalalogging.Logger
import spray.json.{JsBoolean, JsObject, JsString}

import scala.concurrent.{ExecutionContext, Future}

object MqttPub extends App with MqttConnector {

  import org.eclipse.paho.client.mqttv3.MqttClient
  import org.eclipse.paho.client.mqttv3.MqttConnectOptions
  import org.eclipse.paho.client.mqttv3.MqttException
  import org.eclipse.paho.client.mqttv3.MqttMessage
  import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

  implicit val ac: ActorSystem = ActorSystem("publisher-system")
  implicit  val ec: ExecutionContext = ac.dispatcher
  implicit val log: Logger = Logger("MqttPub")

  val topic = "/home/heatcontroller"
  val content = "Message from MqttPublishSample"
  val qos = 2
  val broker = "tcp://192.168.178.52:1883" // Raspi

  val clientId = "HeatController"
  val persistence = new MemoryPersistence

  implicit val client = new MqttClient(broker, MqttClient.generateClientId, persistence)
  val connOpts = new MqttConnectOptions
  connOpts.setCleanSession(true)
  System.out.println("Connecting to broker: " + broker)
  client.connect(connOpts)
  System.out.println("Connected")

  input()
  while(true){
    Thread.sleep(500)
  }

  def input() = Future {
    var done = false
    while(!done) {
      print(">")
      val input = scala.io.StdIn.readLine()
      println(s"input : $input")
      if (input == "x"){
        done = true
      } else if(input.startsWith("heat-calib")){
        headCalib()
      } else if(input.startsWith("heat-tempdiff")){
        val params = input.split(' ')
        if(params.length == 2) {
          val tempdiff = params(1)
          headTempDiff(tempdiff)
        } else println("heat-tempdiff <TEMP_DIFF>")
      } else if(input.startsWith("door")){
        val params = input.split(' ')
        if(params.length == 3) {
          val id = params(1)
          val stateStr = params(2).toLowerCase()
          val state = if(stateStr == "true" || stateStr == "on" ||  stateStr == "open")true else false
          door(id, state)
        } else println("door <ID> <STATE>")
      } else if(input.startsWith("window")){
        val params = input.split(' ')
        if(params.length == 3) {
          val id = params(1)
          val stateStr = params(2).toLowerCase()
          val state = if(stateStr == "true" || stateStr == "on" ||  stateStr == "open")true else false
          window(id, state)
        } else println("window <ID> <STATE>")
      }
    }
    shutdown()
  }

  def headCalib(): Unit = {
    println(s"headCalib")
    val json = JsObject(Map("cmd" -> JsString("calib")))
    headCmd(json)
  }

  def headTempDiff(tempDiff: String): Unit = {
    println(s"headTempDiff")
    val json = JsObject(Map("cmd" -> JsString("tempdiff"), "params" -> JsString(tempDiff)))
    headCmd(json)
  }

  def headCmd(json: JsObject): Unit = try {
    println(s"headCmd")
    send(json, "/home/heatcontroller")
    Done
  } catch {
    case t: Throwable => t.printStackTrace()
  }

  def door(id: String, state: Boolean): Unit = try {
    println(s"door $id $state")
    val json = JsObject(Map("id" -> JsString(id), "state" -> JsBoolean(state)))
    send(json, "/home/doors")
    Done
  } catch {
    case t: Throwable => t.printStackTrace()
  }

  def window(id: String, state: Boolean): Unit = try {
    println(s"window $id $state")
    val json = JsObject(Map("id" -> JsString(id), "state" -> JsBoolean(state)))
    send(json, "/home/windows")
    Done
  } catch {
    case t: Throwable => t.printStackTrace()
  }

  def shutdown(): Unit ={
    client.disconnect()
    System.out.println("Disconnected")
    System.exit(0)
  }
}
