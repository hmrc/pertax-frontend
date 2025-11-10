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

  "StartDateDecisionService.decide" must {

    "return Left(FutureDateError) when proposed is after today" in {
      val res = service.decide(
        proposed = future,
        currentStart = Some(earlier),
        today = today,
        p85Enabled = false,
        crossBorder = false
      )
      res mustBe Left(FutureDateError)
    }

    "return Left(EarlyDateError) when P85 enabled and proposed <= current start date" in {
      val resEqual   = service.decide(
        proposed = sameAsCurrent,
        currentStart = Some(sameAsCurrent),
        today = today,
        p85Enabled = true,
        crossBorder = false
      )
      val resEarlier = service.decide(
        proposed = earlier,
        currentStart = Some(sameAsCurrent),
        today = today,
        p85Enabled = true,
        crossBorder = false
      )
      resEqual mustBe Left(EarlyDateError)
      resEarlier mustBe Left(EarlyDateError)
    }

    "return Right(proposed) when P85 enabled and proposed is after current start date" in {
      val res = service.decide(
        proposed = later,
        currentStart = Some(earlier),
        today = today,
        p85Enabled = true,
        crossBorder = false
      )
      res mustBe Right(later)
    }

    "return Left(EarlyDateError) when crossBorder=true and proposed <= current start date" in {
      val res = service.decide(
        proposed = sameAsCurrent,
        currentStart = Some(sameAsCurrent),
        today = today,
        p85Enabled = false,
        crossBorder = true
      )
      res mustBe Left(EarlyDateError)
    }

    "DOMESTIC: return Right(today) when proposed <= current start date (no P85, no crossBorder)" in {
      val resEqual   = service.decide(
        proposed = sameAsCurrent,
        currentStart = Some(sameAsCurrent),
        today = today,
        p85Enabled = false,
        crossBorder = false
      )
      val resEarlier = service.decide(
        proposed = earlier,
        currentStart = Some(sameAsCurrent),
        today = today,
        p85Enabled = false,
        crossBorder = false
      )
      resEqual mustBe Right(today)
      resEarlier mustBe Right(today)
    }

    "DOMESTIC: return Right(proposed) when proposed is after current start date (no P85, no crossBorder)" in {
      val res = service.decide(
        proposed = later,
        currentStart = Some(earlier),
        today = today,
        p85Enabled = false,
        crossBorder = false
      )
      res mustBe Right(later)
    }

    "return Right(proposed) when there is no current start date on record" in {
      val res = service.decide(
        proposed = earlier,
        currentStart = None,
        today = today,
        p85Enabled = false,
        crossBorder = false
      )
      res mustBe Right(earlier)
    }
  }
}
