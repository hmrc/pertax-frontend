package testUtils

import uk.gov.hmrc.domain.Nino

import scala.io.Source.fromFile

object FileHelper {

  def loadFile(name: String): String = {
    val source = fromFile(name)
    try source.mkString
    finally source.close()
  }

  def loadFileInterpolatingNino(name: String, nino: Nino): String =
    loadFile(name).replaceAll("<NINO>", nino.nino)
}
