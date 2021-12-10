package utils

import scala.io.Source.fromFile

object FileHelper {

  def loadFile(name: String): String = {
    val source = fromFile(name)
    try source.mkString finally source.close()
  }
}
