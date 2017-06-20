/*
 * Copyright 2017 HM Revenue & Customs
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
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import services.{CitizenDetailsService, PersonDetailsSuccessResponse, UserDetailsService}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel
import uk.gov.hmrc.play.partials.PartialRetriever
import util.Fixtures._
import util.{BaseSpec, Fixtures}

import scala.concurrent.Future


class PrintControllerSpec extends BaseSpec {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(bind[CitizenDetailsService].toInstance(MockitoSugar.mock[CitizenDetailsService]))
    .overrides(bind[PertaxAuthConnector].toInstance(MockitoSugar.mock[PertaxAuthConnector]))
    .overrides(bind[PertaxAuditConnector].toInstance(MockitoSugar.mock[PertaxAuditConnector]))
    .overrides(bind[UserDetailsService].toInstance(MockitoSugar.mock[UserDetailsService]))
    .overrides(bind[PartialRetriever].toInstance(MockitoSugar.mock[PartialRetriever]))
    .overrides(bind[FrontEndDelegationConnector].toInstance(MockitoSugar.mock[FrontEndDelegationConnector]))
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

      when(c.authConnector.currentAuthority(org.mockito.Matchers.any())) thenReturn {
        Future.successful(Some(buildFakeAuthority(withPaye = true, withSa = true, confidenceLevel = if (isHighGG) ConfidenceLevel.L200 else ConfidenceLevel.L50)))
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
    }
  }
}
