/*
 * Copyright 2023 HM Revenue & Customs
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

import controllers.auth.{AuthJourney, FakeAuthJourney}
import models.{PersonDetails, SelfAssessmentUserType}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.EditAddressLockRepository
import testUtils.BaseSpec
import uk.gov.hmrc.play.partials.FormPartialRetriever

import java.time.LocalDateTime

class BusinessHoursSpec extends BaseSpec {

  override implicit def localGuiceApplicationBuilder(
    saUser: SelfAssessmentUserType,
    personDetails: Option[PersonDetails]
  ): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .overrides(
        bind[FormPartialRetriever].toInstance(mockPartialRetriever),
        bind[EditAddressLockRepository].toInstance(mockEditAddressLockRepository),
        bind[AuthJourney].toInstance(new FakeAuthJourney(saUser, personDetails))
      )
      .configure(
        configValues ++
          Map(
            "feature.business-hours.2.day"        -> "Wednesday",
            "feature.business-hours.2.start-time" -> null,
            "feature.business-hours.2.end-time"   -> null,
            "feature.business-hours.3.day"        -> "Thursday",
            "feature.business-hours.3.start-time" -> "09:00",
            "feature.business-hours.3.end-time"   -> "17:00"
          )
      )

  val sut: BusinessHours = inject[BusinessHours]

  "isTrue" must {
    "return true" when {
      "time is within the window" in {
        val dateTime = LocalDateTime.of(2022, 9, 29, 10, 0, 0)
        sut.isTrue(dateTime) mustBe true
      }
    }

    "return false" when {
      "time is before window" in {
        val dateTime = LocalDateTime.of(2022, 9, 29, 8, 0, 0)
        sut.isTrue(dateTime) mustBe false
      }

      "time is after window" in {
        val dateTime = LocalDateTime.of(2022, 9, 29, 18, 0, 0)
        sut.isTrue(dateTime) mustBe false
      }

      "no window is defined for that day" in {
        val dateTime = LocalDateTime.of(2022, 9, 28, 18, 0, 0)
        sut.isTrue(dateTime) mustBe false
      }
    }
  }

}
