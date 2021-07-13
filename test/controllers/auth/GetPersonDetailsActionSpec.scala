/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers.auth

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models.{Person, PersonDetails, WrongCredentialsSelfAssessmentUser}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Result
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.partials.MessageFrontendService
import services.{CitizenDetailsService, PersonDetailsNotFoundResponse, PersonDetailsSuccessResponse}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import util.{BaseSpec, Fixtures}

import scala.concurrent.Future

class GetPersonDetailsActionSpec extends BaseSpec {

  val mockMessageFrontendService = mock[MessageFrontendService]
  val mockCitizenDetailsService = mock[CitizenDetailsService]
  val configDecorator: ConfigDecorator = mock[ConfigDecorator]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[MessageFrontendService].toInstance(mockMessageFrontendService))
    .overrides(bind[CitizenDetailsService].toInstance(mockCitizenDetailsService))
    .overrides(bind[ConfigDecorator].toInstance(configDecorator))
    .configure(Map("metrics.enabled" -> false))
    .build()

  val refinedRequest =
    UserRequest(
      Some(Fixtures.fakeNino),
      None,
      WrongCredentialsSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
      Credentials("", "Verify"),
      ConfidenceLevel.L50,
      None,
      None,
      None,
      None,
      None,
      None,
      FakeRequest("", "")
    )

  val personDetailsSuccessResponse = PersonDetailsSuccessResponse(
    PersonDetails(Person(Some("TestFirstName"), None, None, None, None, None, None, None, None), None, None))

  val personDetailsBlock: UserRequest[_] => Future[Result] = userRequest => {
    val person = userRequest.personDetails match {
      case Some(PersonDetails(Person(Some(firstName), None, None, None, None, None, None, None, None), None, None)) =>
        firstName
      case _ => "No Person Details Defined"
    }

    Future.successful(Ok(s"Person Details: $person"))
  }

  val unreadMessageCountBlock: UserRequest[_] => Future[Result] = userRequest =>
    Future.successful(Ok(s"Person Details: ${userRequest.unreadMessageCount.getOrElse("no message count present")}"))

  def harness[A](block: UserRequest[_] => Future[Result])(implicit request: UserRequest[A]): Future[Result] = {
    lazy val actionProvider = app.injector.instanceOf[GetPersonDetailsAction]
    actionProvider.invokeBlock(request, block)
  }

  "GetPersonDetailsAction" when {
    when(mockMessageFrontendService.getUnreadMessageCount(any()))
      .thenReturn(Future.successful(Some(1)))

    "a user has PersonDetails in CitizenDetails" must {

      "add the PersonDetails to the request" in {
        when(mockCitizenDetailsService.personDetails(any())(any()))
          .thenReturn(Future.successful(personDetailsSuccessResponse))

        val result = harness(personDetailsBlock)(refinedRequest)
        status(result) mustBe OK
        contentAsString(result) mustBe "Person Details: TestFirstName"
      }
    }

    "when a user has no PersonDetails in CitizenDetails" must {
      "return the request it was passed" in {
        when(mockCitizenDetailsService.personDetails(any())(any()))
          .thenReturn(Future.successful(PersonDetailsNotFoundResponse))

        val result = harness(personDetailsBlock)(refinedRequest)
        status(result) mustBe OK
        contentAsString(result) mustBe "Person Details: No Person Details Defined"
      }

    }

    "when the person details message count toggle is set to true" must {
      "return a request with the unread message count" in {
        when(mockCitizenDetailsService.personDetails(any())(any()))
          .thenReturn(Future.successful(personDetailsSuccessResponse))

        when(configDecorator.personDetailsMessageCountEnabled).thenReturn(true)

        val result = harness(unreadMessageCountBlock)(refinedRequest)
        status(result) mustBe OK
        contentAsString(result) mustBe "Person Details: 1"
      }
    }

    "when the person details message count toggle is set to false" must {
      "return a request with the unread message count" in {
        when(mockCitizenDetailsService.personDetails(any())(any()))
          .thenReturn(Future.successful(personDetailsSuccessResponse))

        when(configDecorator.personDetailsMessageCountEnabled).thenReturn(false)

        val result = harness(unreadMessageCountBlock)(refinedRequest)
        status(result) mustBe OK
        contentAsString(result) mustBe "Person Details: no message count present"
      }
    }
  }
}
