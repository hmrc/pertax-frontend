/*
 * Copyright 2019 HM Revenue & Customs
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

package controllers

import connectors.{FrontEndDelegationConnector, PertaxAuditConnector, PertaxAuthConnector}
import models.UserDetails
import org.jsoup.Jsoup
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import play.api.{Application, Configuration}
import services.partials.MessageFrontendService
import services.{CitizenDetailsService, PersonDetailsSuccessResponse, UserDetailsService}
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel
import uk.gov.hmrc.play.frontend.filters.{CookieCryptoFilter, SessionCookieCryptoFilter}
import uk.gov.hmrc.play.partials.PartialRetriever
import uk.gov.hmrc.renderer.TemplateRenderer
import util.Fixtures._
import util.{BaseSpec, Fixtures, MockTemplateRenderer}

import scala.concurrent.Future

class NiLetterControllerSpec extends BaseSpec {

  val sessionCookieCryptoFilter = new SessionCookieCryptoFilter(injected[ApplicationCrypto])

  override implicit lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[TemplateRenderer].toInstance(MockTemplateRenderer))
    .overrides(bind[PartialRetriever].toInstance(MockitoSugar.mock[PartialRetriever]))
    .overrides(bind[CookieCryptoFilter].toInstance(sessionCookieCryptoFilter))
    .configure(configValues)
    .build()

  override def beforeEach: Unit =
    reset(injected[CitizenDetailsService])

  trait LocalSetup {

    def isHighGG: Boolean
    def isVerify: Boolean

    lazy val controller = {

      val c = injected[NiLetterController]

      when(injected[MessageFrontendService].getUnreadMessageCount(any())) thenReturn {
        Future.successful(None)
      }

      c
    }

  }

  "Calling NiLetterController.printNationalInsuranceNumber" should {

    "call printNationalInsuranceNumber should return OK when called by a high GG user" in new LocalSetup {
      override lazy val isHighGG = true
      override lazy val isVerify = false

      lazy val r = controller.printNationalInsuranceNumber()(buildFakeRequestWithAuth("GET"))

      status(r) shouldBe OK
    }

    "call printNationalInsuranceNumber should return OK when called by a verify user" in new LocalSetup {
      override lazy val isHighGG = false
      override lazy val isVerify = true

      lazy val r = controller.printNationalInsuranceNumber()(buildFakeRequestWithAuth("GET"))

      status(r) shouldBe OK
      val doc = Jsoup.parse(contentAsString(r))
      doc.getElementById("page-title").text() shouldBe "Your National Insurance letter"
      doc
        .getElementById("keep-ni-number-safe")
        .text() shouldBe "Keep this number in a safe place. Do not destroy this letter."
      doc.getElementById("available-information-text-relay").text() should include(
        "Information is available in large print, audio tape and Braille formats.")
      doc.getElementById("available-information-text-relay").text() should include(
        "Text Relay service prefix number - 18001")
      doc
        .getElementById("your-ni-number-unique")
        .text() shouldBe "Your National Insurance number is unique to you and will never change. To prevent identity fraud, do not share it with anyone who does not need it."
    }
  }
}
