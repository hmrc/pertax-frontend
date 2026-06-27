/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package util

import com.google.inject.Inject
import play.api.Configuration
import uk.gov.hmrc.domain.Nino

class NinoUtil @Inject() (implicit appConfig: Configuration) {

  private val defaultPtapHomepageNinoRolloutLastNumericDigits: Seq[Int] = Seq.empty

  private lazy val ptapHomepageNinoRolloutLastNumericDigits: Seq[Int] =
    appConfig
      .getOptional[Seq[Int]]("feature.ptap-homepage.nino-rollout.last-numeric-digits")
      .getOrElse(defaultPtapHomepageNinoRolloutLastNumericDigits)

  def isNinoEligibleForPtapHomepage(nino: Nino): Boolean = {
    val lastNumericDigit = nino.nino.filter(_.isDigit).takeRight(1).toInt
    ptapHomepageNinoRolloutLastNumericDigits.contains(lastNumericDigit)
  }
}
