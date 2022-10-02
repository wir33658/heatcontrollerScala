package de.robs

import scala.collection.parallel.immutable._

class CircularBuffer[A](var maxSize: Int = 10) {
  var ls: Vector[A] = Vector.empty[A]

  def add(obj: A): Unit = {
    ls = (ls :+ obj) takeRight maxSize
  }
}
