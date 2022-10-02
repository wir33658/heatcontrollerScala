package de.robs

import akka.actor.{ActorSystem, ClassicActorSystemProvider}
import akka.stream.{ActorMaterializer, Materializer, SystemMaterializer}
import com.typesafe.scalalogging.Logger

object SimpleTest extends App {
  implicit val ac: ActorSystem = ActorSystem("smarthomeserver")
  implicit def matFromSystem(implicit provider: ClassicActorSystemProvider): Materializer = SystemMaterializer(provider.classicSystem).materializer
  // implicit val materializer = ActorMaterializer()

  val log = Logger("SimpleTest")

  log.debug("--- debug : Simple test log")
  log.error("--- error : Simple test log")
  log.warn("--- warning : Simple test log")
  log.info("--- info : Simple test log")

  val d = Math.round(1.534234234)
  val i = d.toInt
  println(s"i = $i")

}
