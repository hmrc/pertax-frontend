/*
 * Copyright 2018 HM Revenue & Customs
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
import play.api.Application
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import services.partials.MessageFrontendService
import services.{CitizenDetailsService, PersonDetailsSuccessResponse, UserDetailsService}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel
import uk.gov.hmrc.play.partials.PartialRetriever
import util.Fixtures._
import util.{BaseSpec, Fixtures}

import scala.concurrent.Future


class PrintControllerSpec extends BaseSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder
    .overrides(bind[CitizenDetailsService].toInstance(MockitoSugar.mock[CitizenDetailsService]))
    .overrides(bind[PertaxAuthConnector].toInstance(MockitoSugar.mock[PertaxAuthConnector]))
    .overrides(bind[PertaxAuditConnector].toInstance(MockitoSugar.mock[PertaxAuditConnector]))
    .overrides(bind[UserDetailsService].toInstance(MockitoSugar.mock[UserDetailsService]))
    .overrides(bind[PartialRetriever].toInstance(MockitoSugar.mock[PartialRetriever]))
    .overrides(bind[FrontEndDelegationConnector].toInstance(MockitoSugar.mock[FrontEndDelegationConnector]))
    .overrides(bind[MessageFrontendService].toInstance(MockitoSugar.mock[MessageFrontendService]))
    .build()


  override def beforeEach: Unit = {
    reset(injected[CitizenDetailsService])
  }

  trait LocalSetup {

    def isHighGG: Boolean
    def isVerify: Boolean

    lazy val controller =  {

      val c = injected[PrintController]

      when(c.citizenDetailsService.personDetails(meq(Fixtures.fakeNino))(any())) thenReturn {
        Future.successful(PersonDetailsSuccessResponse(Fixtures.buildPersonDetails))
      }

      when(c.authConnector.currentAuthority(any(), any())) thenReturn {
        Future.successful(Some(buildFakeAuthority(withPaye = true, withSa = true, confidenceLevel = if (isHighGG) ConfidenceLevel.L200 else ConfidenceLevel.L50)))
      }
      when(injected[MessageFrontendService].getUnreadMessageCount(any())) thenReturn {
        Future.successful(None)
      }

      val authProviderType = if(isVerify) UserDetails.VerifyAuthProvider else UserDetails.GovernmentGatewayAuthProvider
      when(c.userDetailsService.getUserDetails(any())(any())) thenReturn {
        Future.successful(Some(UserDetails(authProviderType)))
      }

      c
    }

  }

  "Calling PrintControllers.printNationalInsuranceNumber" should {

    "call printNationalInsuranceNumber should return OK when called by a high GG user" in new LocalSetup {
      override lazy val isHighGG = true
      override lazy val isVerify = false

      lazy val r = controller.printNationalInsuranceNumber()(buildFakeRequestWithAuth("GET"))

      status(r) shouldBe OK
      verify(controller.citizenDetailsService, times(1)).personDetails(meq(Fixtures.fakeNino))(any())
    }

    "call printNationalInsuranceNumber should return OK when called by a verify user" in new LocalSetup {
      override lazy val isHighGG = false
      override lazy val isVerify = true

      lazy val r = controller.printNationalInsuranceNumber()(buildFakeRequestWithAuth("GET"))

      status(r) shouldBe OK
      verify(controller.citizenDetailsService, times(1)).personDetails(meq(Fixtures.fakeNino))(any())
      val doc = Jsoup.parse(contentAsString(r))
      doc.getElementById("page-title").text() shouldBe "Your National Insurance letter"
      doc.getElementById("keep-ni-number-safe").text() shouldBe "Keep this number in a safe place. Do not destroy this letter."
      doc.getElementById("available-information-text-relay").text() should include("Information is available in large print, audio tape and Braille formats.")
      doc.getElementById("available-information-text-relay").text() should include("Text Relay service prefix number - 18001")
      doc.getElementById("your-ni-number-unique").text() shouldBe "Your National Insurance number is unique to you and will never change. To prevent identity fraud, do not share it with anyone who does not need it."
    }
  }
}
