/*
 * Copyright 2025 HM Revenue & Customs
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

package services

import play.api.Application
import play.api.inject.bind
import services.StartDateDecisionService.*
import testUtils.BaseSpec

import java.time.LocalDate

class StartDateDecisionServiceSpec extends BaseSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[StartDateDecisionService].toInstance(new StartDateDecisionService)
    )
    .build()

  private lazy val service: StartDateDecisionService = app.injector.instanceOf[StartDateDecisionService]

  private val today: LocalDate         = LocalDate.of(2025, 1, 10)
  private val earlier: LocalDate       = today.minusDays(5)
  private val sameAsCurrent: LocalDate = today.minusDays(3)
  private val later: LocalDate         = today.minusDays(1)
  private val future: LocalDate        = today.plusDays(1)

  override def beforeEach(): Unit =
    super.beforeEach()

  "StartDateDecisionService.determineStartDate" must {

    "return Left(FutureDateError) when requested date is after today" in {
      val res = service.determineStartDate(
        requestedDate = future,
        recordedStartDate = Some(earlier),
        today = today,
        overseasMove = false,
        scotlandBorderChange = false
      )
      res mustBe Left(FutureDateError)
    }

    "return Left(EarlyDateError) when overseasMove=true and requestedDate <= recorded start date" in {
      val resEqual   = service.determineStartDate(
        requestedDate = sameAsCurrent,
        recordedStartDate = Some(sameAsCurrent),
        today = today,
        overseasMove = true,
        scotlandBorderChange = false
      )
      val resEarlier = service.determineStartDate(
        requestedDate = earlier,
        recordedStartDate = Some(sameAsCurrent),
        today = today,
        overseasMove = true,
        scotlandBorderChange = false
      )
      resEqual mustBe Left(EarlyDateError)
      resEarlier mustBe Left(EarlyDateError)
    }

    "return Right(requestedDate) when overseasMove=true and requestedDate is after recorded start date" in {
      val res = service.determineStartDate(
        requestedDate = later,
        recordedStartDate = Some(earlier),
        today = today,
        overseasMove = true,
        scotlandBorderChange = false
      )
      res mustBe Right(later)
    }

    "return Left(EarlyDateError) when scotlandBorderChange=true and requestedDate <= recorded start date" in {
      val res = service.determineStartDate(
        requestedDate = sameAsCurrent,
        recordedStartDate = Some(sameAsCurrent),
        today = today,
        overseasMove = false,
        scotlandBorderChange = true
      )
      res mustBe Left(EarlyDateError)
    }

    "DOMESTIC: return Right(today) when requestedDate <= recorded start date (no overseasMove, no scotlandBorderChange)" in {
      val resEqual   = service.determineStartDate(
        requestedDate = sameAsCurrent,
        recordedStartDate = Some(sameAsCurrent),
        today = today,
        overseasMove = false,
        scotlandBorderChange = false
      )
      val resEarlier = service.determineStartDate(
        requestedDate = earlier,
        recordedStartDate = Some(sameAsCurrent),
        today = today,
        overseasMove = false,
        scotlandBorderChange = false
      )
      resEqual mustBe Right(today)
      resEarlier mustBe Right(today)
    }

    "DOMESTIC: return Right(requestedDate) when requestedDate is after recorded start date (no overseasMove, no scotlandBorderChange)" in {
      val res = service.determineStartDate(
        requestedDate = later,
        recordedStartDate = Some(earlier),
        today = today,
        overseasMove = false,
        scotlandBorderChange = false
      )
      res mustBe Right(later)
    }

    "return Right(requestedDate) when there is no recorded start date on record" in {
      val res = service.determineStartDate(
        requestedDate = earlier,
        recordedStartDate = None,
        today = today,
        overseasMove = false,
        scotlandBorderChange = false
      )
      res mustBe Right(earlier)
    }
  }
}
