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
import controllers.auth.requests.UserRequest
import models.{ActivatedOnlineFilerSelfAssessmentUser, UserDetails}
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import services.partials.MessageFrontendService
import services.{CitizenDetailsService, PersonDetailsSuccessResponse, UserDetailsService}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.play.partials.HtmlPartial
import util.Fixtures._
import util.{BaseSpec, Fixtures, LocalPartialRetriever}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MessageControllerSpec extends BaseSpec {

  lazy val fakeRequest = FakeRequest("", "")
  lazy val userRequest = UserRequest(
    None,
    None,
    None,
    ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111")),
    "SomeAuth",
    ConfidenceLevel.L200,
    None,
    None,
    None,
    None,
    fakeRequest)

  override implicit lazy val app: Application = localGuiceApplicationBuilder(userRequest)
    .overrides(bind[CitizenDetailsService].toInstance(MockitoSugar.mock[CitizenDetailsService]))
    .overrides(bind[PertaxAuthConnector].toInstance(MockitoSugar.mock[PertaxAuthConnector]))
    .overrides(bind[PertaxAuditConnector].toInstance(MockitoSugar.mock[PertaxAuditConnector]))
    .overrides(bind[FrontEndDelegationConnector].toInstance(MockitoSugar.mock[FrontEndDelegationConnector]))
    .overrides(bind[MessageFrontendService].toInstance(MockitoSugar.mock[MessageFrontendService]))
    .overrides(bind[UserDetailsService].toInstance(MockitoSugar.mock[UserDetailsService]))
    .overrides(bind[LocalPartialRetriever].toInstance(MockitoSugar.mock[LocalPartialRetriever]))
    .build()

  override def beforeEach: Unit =
    reset(injected[MessageFrontendService], injected[CitizenDetailsService])

  trait LocalSetup {

    lazy val controller = {
      val c = injected[MessageController]

      when(c.messageFrontendService.getUnreadMessageCount(any())) thenReturn {
        Future.successful(None)
      }

      c
    }
  }

  "Calling MessageController.messageList" should {

    "call messages and return 200 when called by a high sa  GG user" in new LocalSetup {

      when(controller.messageFrontendService.getMessageListPartial(any())) thenReturn {
        Future(HtmlPartial.Success(Some("Success"), Html("<title/>")))
      }

      val r = controller.messageList(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
      verify(controller.messageFrontendService, times(1)).getMessageListPartial(any())
      verify(controller.citizenDetailsService, times(0)).personDetails(any())(any())
    }

    "call messages and return 200 when called by a high paye GG user" in new LocalSetup {

      when(controller.citizenDetailsService.personDetails(meq(Fixtures.fakeNino))(any())) thenReturn {
        Future.successful(PersonDetailsSuccessResponse(Fixtures.buildPersonDetails))
      }

      when(controller.messageFrontendService.getMessageListPartial(any())) thenReturn {
        Future(HtmlPartial.Success(Some("Success"), Html("<title/>")))
      }

      val r = controller.messageList(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
      verify(controller.messageFrontendService, times(1)).getMessageListPartial(any())
      verify(controller.citizenDetailsService, times(1)).personDetails(any())(any())
    }

    "return 401 for a high GG user for a high GG user not enrolled in SA" in new LocalSetup {

      val r = controller.messageList(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe UNAUTHORIZED

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
      verify(controller.messageFrontendService, times(0)).getMessageListPartial(any())
      verify(controller.citizenDetailsService, times(0)).personDetails(any())(any())
    }

    "return 401 for a Verify user enrolled in SA" in new LocalSetup {

      when(controller.citizenDetailsService.personDetails(meq(Fixtures.fakeNino))(any())) thenReturn {
        Future.successful(PersonDetailsSuccessResponse(Fixtures.buildPersonDetails))
      }

      val r = controller.messageList(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe UNAUTHORIZED

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
      verify(controller.messageFrontendService, times(0)).getMessageListPartial(any())
      verify(controller.citizenDetailsService, times(1)).personDetails(meq(Fixtures.fakeNino))(any())
    }

    "return 401 for a Verify not enrolled in SA" in new LocalSetup {

      when(controller.citizenDetailsService.personDetails(meq(Fixtures.fakeNino))(any())) thenReturn {
        Future.successful(PersonDetailsSuccessResponse(Fixtures.buildPersonDetails))
      }

      val r = controller.messageList(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe UNAUTHORIZED

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
      verify(controller.messageFrontendService, times(0)).getMessageListPartial(any())
      verify(controller.citizenDetailsService, times(1)).personDetails(meq(Fixtures.fakeNino))(any())
    }

  }

  "Calling MessageController.messageDetail" should {

    "call messages and return 200 when called by a SA high GG" in new LocalSetup {

      when(controller.messageFrontendService.getMessageDetailPartial(any())(any())) thenReturn {
        Future(HtmlPartial.Success(Some("Success"), Html("<title/>")))
      }

      val r = controller.messageDetail("SOME-MESSAGE-TOKEN")(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
      verify(controller.messageFrontendService, times(1)).getMessageDetailPartial(any())(any())
      verify(controller.citizenDetailsService, times(0)).personDetails(any())(any())
    }

    "call messages and return 200 when called by a high PAYE GG" in new LocalSetup {

      when(controller.citizenDetailsService.personDetails(meq(Fixtures.fakeNino))(any())) thenReturn {
        Future.successful(PersonDetailsSuccessResponse(Fixtures.buildPersonDetails))
      }

      when(controller.messageFrontendService.getMessageDetailPartial(any())(any())) thenReturn {
        Future(HtmlPartial.Success(Some("Success"), Html("<title/>")))
      }

      val r = controller.messageDetail("SOME-MESSAGE-TOKEN")(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
      verify(controller.messageFrontendService, times(1)).getMessageDetailPartial(any())(any())
      verify(controller.citizenDetailsService, times(1)).personDetails(any())(any())
    }

    "return 401 for a high GG user not enrolled in SA" in new LocalSetup {

      val r = controller.messageDetail("SOME-MESSAGE-TOKEN")(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe UNAUTHORIZED
      verify(controller.messageFrontendService, times(0)).getMessageDetailPartial(any())(any())
      verify(controller.citizenDetailsService, times(0)).personDetails(any())(any())
    }

    "return 401 for a high GG user not logged in via GG and enrolled in SA" in new LocalSetup {

      when(controller.citizenDetailsService.personDetails(meq(Fixtures.fakeNino))(any())) thenReturn {
        Future.successful(PersonDetailsSuccessResponse(Fixtures.buildPersonDetails))
      }

      val r = controller.messageDetail("SOME-MESSAGE-TOKEN")(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe UNAUTHORIZED

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
      verify(controller.messageFrontendService, times(0)).getMessageDetailPartial(any())(any())
      verify(controller.citizenDetailsService, times(1)).personDetails(meq(Fixtures.fakeNino))(any())
    }

    "return 401 for a high GG user not logged in via GG and not enrolled in SA" in new LocalSetup {

      when(controller.citizenDetailsService.personDetails(meq(Fixtures.fakeNino))(any())) thenReturn {
        Future.successful(PersonDetailsSuccessResponse(Fixtures.buildPersonDetails))
      }

      val r = controller.messageDetail("SOME-MESSAGE-TOKEN")(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe UNAUTHORIZED

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
      verify(controller.messageFrontendService, times(0)).getMessageDetailPartial(any())(any())
      verify(controller.citizenDetailsService, times(1)).personDetails(meq(Fixtures.fakeNino))(any())
    }
  }
}
