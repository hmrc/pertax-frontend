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
import play.twirl.api.Html
import services.partials.MessagePartialService
import services.{CitizenDetailsService, PersonDetailsSuccessResponse, UserDetailsService}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel
import uk.gov.hmrc.play.partials.HtmlPartial
import util.Fixtures._
import util.{BaseSpec, Fixtures, LocalPartialRetriever}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MessageControllerSpec extends BaseSpec  {

  override implicit lazy val app: Application = localGuiceApplicationBuilder
    .overrides(bind[CitizenDetailsService].toInstance(MockitoSugar.mock[CitizenDetailsService]))
    .overrides(bind[PertaxAuthConnector].toInstance(MockitoSugar.mock[PertaxAuthConnector]))
    .overrides(bind[PertaxAuditConnector].toInstance(MockitoSugar.mock[PertaxAuditConnector]))
    .overrides(bind[FrontEndDelegationConnector].toInstance(MockitoSugar.mock[FrontEndDelegationConnector]))
    .overrides(bind[MessagePartialService].toInstance(MockitoSugar.mock[MessagePartialService]))
    .overrides(bind[UserDetailsService].toInstance(MockitoSugar.mock[UserDetailsService]))
    .overrides(bind[LocalPartialRetriever].toInstance(MockitoSugar.mock[LocalPartialRetriever]))
    .build()


  override def beforeEach: Unit = {
    reset(injected[MessagePartialService], injected[CitizenDetailsService])
  }


  trait LocalSetup {

    def authProviderType: String

    lazy val controller = {
      val c = injected[MessageController]

      when(c.authConnector.currentAuthority(any(), any())) thenReturn {
        Future.successful(Some(buildFakeAuthority()))
      }
      when(c.userDetailsService.getUserDetails(any())(any())) thenReturn {
        Future.successful(Some(UserDetails(authProviderType)))
      }

      c
    }
  }

  "Calling MessageController.messageList" should {

    "call messages and return 200 when called by a high sa  GG user" in new LocalSetup {

      lazy val authProviderType = UserDetails.GovernmentGatewayAuthProvider

      when(controller.authConnector.currentAuthority(any(), any())) thenReturn {
        Future.successful(Some(buildFakeAuthority(withPaye = false, withSa = true, confidenceLevel = ConfidenceLevel.L200)))
      }

      when(controller.messagePartialService.getMessageListPartial(any())) thenReturn {
        Future(HtmlPartial.Success(Some("Success"),Html("<title/>")))
      }

      val r = controller.messageList(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK

      verify(controller.messagePartialService, times(1)).getMessageListPartial(any())
      verify(controller.citizenDetailsService, times(0)).personDetails(any())(any())
    }

    "call messages and return 200 when called by a high paye GG user" in new LocalSetup {

      lazy val authProviderType = UserDetails.GovernmentGatewayAuthProvider

      when(controller.citizenDetailsService.personDetails(meq(Fixtures.fakeNino))(any())) thenReturn {
        Future.successful(PersonDetailsSuccessResponse(Fixtures.buildPersonDetails))
      }

      when(controller.authConnector.currentAuthority(any(), any())) thenReturn {
        Future.successful(Some(buildFakeAuthority(withPaye = true, withSa = false, confidenceLevel = ConfidenceLevel.L200)))
      }

      when(controller.messagePartialService.getMessageListPartial(any())) thenReturn {
        Future(HtmlPartial.Success(Some("Success"),Html("<title/>")))
      }

      val r = controller.messageList(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK

      verify(controller.messagePartialService, times(1)).getMessageListPartial(any())
      verify(controller.citizenDetailsService, times(1)).personDetails(any())(any())
    }

    "return 401 for a high GG user for a high GG user not enrolled in SA" in new LocalSetup {

      lazy val authProviderType = UserDetails.GovernmentGatewayAuthProvider

      when(controller.authConnector.currentAuthority(any(), any())) thenReturn {
        Future.successful(Some(buildFakeAuthority(withPaye = false, withSa = false, confidenceLevel = ConfidenceLevel.L200)))
      }

      val r = controller.messageList(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe UNAUTHORIZED
      verify(controller.messagePartialService, times(0)).getMessageListPartial(any())
      verify(controller.citizenDetailsService, times(0)).personDetails(any())(any())
    }

    "return 401 for a Verify user enrolled in SA" in new LocalSetup {

      lazy val authProviderType = UserDetails.VerifyAuthProvider

      when(controller.authConnector.currentAuthority(any(), any())) thenReturn {
        Future.successful(Some(buildFakeAuthority(withSa = true)))
      }

      when(controller.citizenDetailsService.personDetails(meq(Fixtures.fakeNino))(any())) thenReturn {
        Future.successful(PersonDetailsSuccessResponse(Fixtures.buildPersonDetails))
      }

      val r = controller.messageList(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe UNAUTHORIZED
      verify(controller.messagePartialService, times(0)).getMessageListPartial(any())
      verify(controller.citizenDetailsService, times(1)).personDetails(meq(Fixtures.fakeNino))(any())
    }

    "return 401 for a Verify not enrolled in SA" in new LocalSetup {

      lazy val authProviderType = UserDetails.VerifyAuthProvider

      when(controller.authConnector.currentAuthority(any(), any())) thenReturn {
        Future.successful(Some(buildFakeAuthority(withSa = false)))
      }

      when(controller.citizenDetailsService.personDetails(meq(Fixtures.fakeNino))(any())) thenReturn {
        Future.successful(PersonDetailsSuccessResponse(Fixtures.buildPersonDetails))
      }

      val r = controller.messageList(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe UNAUTHORIZED
      verify(controller.messagePartialService, times(0)).getMessageListPartial(any())
      verify(controller.citizenDetailsService, times(1)).personDetails(meq(Fixtures.fakeNino))(any())
    }

  }

  "Calling MessageController.messageDetail" should {

    "call messages and return 200 when called by a SA high GG" in new LocalSetup {

      lazy val authProviderType = UserDetails.GovernmentGatewayAuthProvider

      when(controller.authConnector.currentAuthority(any(), any())) thenReturn {
        Future.successful(Some(buildFakeAuthority(withPaye = false, withSa = true, confidenceLevel = ConfidenceLevel.L200)))
      }

      when(controller.messagePartialService.getMessageDetailPartial(any())(any())) thenReturn {
        Future(HtmlPartial.Success(Some("Success"),Html("<title/>")))
      }

      val r = controller.messageDetail("SOME-MESSAGE-TOKEN")(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK
      verify(controller.messagePartialService, times(1)).getMessageDetailPartial(any())(any())
      verify(controller.citizenDetailsService, times(0)).personDetails(any())(any())
    }

    "call messages and return 200 when called by a high PAYE GG" in new LocalSetup {

      lazy val authProviderType = UserDetails.GovernmentGatewayAuthProvider

      when(controller.citizenDetailsService.personDetails(meq(Fixtures.fakeNino))(any())) thenReturn {
        Future.successful(PersonDetailsSuccessResponse(Fixtures.buildPersonDetails))
      }

      when(controller.authConnector.currentAuthority(any(), any())) thenReturn {
        Future.successful(Some(buildFakeAuthority(withPaye = true, withSa = false, confidenceLevel = ConfidenceLevel.L200)))
      }

      when(controller.messagePartialService.getMessageDetailPartial(any())(any())) thenReturn {
        Future(HtmlPartial.Success(Some("Success"),Html("<title/>")))
      }

      val r = controller.messageDetail("SOME-MESSAGE-TOKEN")(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK
      verify(controller.messagePartialService, times(1)).getMessageDetailPartial(any())(any())
      verify(controller.citizenDetailsService, times(1)).personDetails(any())(any())
    }

    "return 401 for a high GG user not enrolled in SA" in new LocalSetup {

      lazy val authProviderType = UserDetails.GovernmentGatewayAuthProvider

      when(controller.authConnector.currentAuthority(any(), any())) thenReturn {
        Future.successful(Some(buildFakeAuthority(withPaye = false, withSa = false, confidenceLevel = ConfidenceLevel.L200)))
      }

      val r = controller.messageDetail("SOME-MESSAGE-TOKEN")(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe UNAUTHORIZED
      verify(controller.messagePartialService, times(0)).getMessageDetailPartial(any())(any())
      verify(controller.citizenDetailsService, times(0)).personDetails(any())(any())
    }

    "return 401 for a high GG user not logged in via GG and enrolled in SA" in new LocalSetup {

      lazy val authProviderType = UserDetails.VerifyAuthProvider

      when(controller.authConnector.currentAuthority(any(), any())) thenReturn {
        Future.successful(Some(buildFakeAuthority(withSa = true)))
      }

      when(controller.citizenDetailsService.personDetails(meq(Fixtures.fakeNino))(any())) thenReturn {
        Future.successful(PersonDetailsSuccessResponse(Fixtures.buildPersonDetails))
      }

      val r = controller.messageDetail("SOME-MESSAGE-TOKEN")(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe UNAUTHORIZED
      verify(controller.messagePartialService, times(0)).getMessageDetailPartial(any())(any())
      verify(controller.citizenDetailsService, times(1)).personDetails(meq(Fixtures.fakeNino))(any())
    }

    "return 401 for a high GG user not logged in via GG and not enrolled in SA" in new LocalSetup {

      lazy val authProviderType = UserDetails.VerifyAuthProvider

      when(controller.authConnector.currentAuthority(any(), any())) thenReturn {
        Future.successful(Some(buildFakeAuthority(withSa = false)))
      }

      when(controller.citizenDetailsService.personDetails(meq(Fixtures.fakeNino))(any())) thenReturn {
        Future.successful(PersonDetailsSuccessResponse(Fixtures.buildPersonDetails))
      }

      val r = controller.messageDetail("SOME-MESSAGE-TOKEN")(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe UNAUTHORIZED
      verify(controller.messagePartialService, times(0)).getMessageDetailPartial(any())(any())
      verify(controller.citizenDetailsService, times(1)).personDetails(meq(Fixtures.fakeNino))(any())
    }
  }
}
