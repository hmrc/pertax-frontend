package util

import com.google.inject.Inject
import play.api.Configuration

class NinoUtil @Inject() (implicit appConfig: Configuration) {
  private val defaultLastNinoDigit = 9
  private lazy val lastNinoDigitFromConfig = appConfig
    .getOptional[Int]("feature.onboarding-by-nino.lastNumericDigit")
    .getOrElse(defaultLastNinoDigit)

  def shouldShowNewLayoutForNino(nino: String): Boolean = {
    val lastNinoDigitFromNino = nino.filter(_.isDigit).takeRight(1).toInt
    lastNinoDigitFromNino == lastNinoDigitFromConfig
  }
}

object NinoUtil {
  def apply()(implicit appConfig: Configuration): NinoUtil = new NinoUtil()
}
