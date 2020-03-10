/*
 * Copyright 2020 HM Revenue & Customs
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

import controllers.auth.requests.UserRequest
import models.{Person, PersonDetails, WrongCredentialsSelfAssessmentUser}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.{FreeSpec, MustMatchers}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
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
import uk.gov.hmrc.domain.{Nino, SaUtr}

import scala.concurrent.Future

class GetPersonDetailsActionSpec extends FreeSpec with MustMatchers with MockitoSugar with GuiceOneAppPerSuite {

  val mockMessageFrontendService = mock[MessageFrontendService]
  val mockCitizenDetailsService = mock[CitizenDetailsService]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[MessageFrontendService].toInstance(mockMessageFrontendService))
    .overrides(bind[CitizenDetailsService].toInstance(mockCitizenDetailsService))
    .configure(Map("metrics.enabled" -> false))
    .build()

  def harness[A]()(implicit request: UserRequest[A]): Future[Result] = {

    lazy val actionProvider = app.injector.instanceOf[GetPersonDetailsAction]

    actionProvider.invokeBlock(request, { userRequest: UserRequest[_] =>
      Future.successful(Ok(s"Person Details: ${userRequest.personDetails.isDefined}"))
    })
  }

  "GetPersonDetailsAction must" - {

    when(mockMessageFrontendService.getUnreadMessageCount(any()))
      .thenReturn(Future.successful(Some(1)))

    "when a user has PersonDetails in CitizenDetails" - {

      "add the PersonDetails to the request" in {

        when(mockCitizenDetailsService.personDetails(any())(any()))
          .thenReturn(Future.successful(PersonDetailsSuccessResponse(
            PersonDetails("blah", Person(Some("blah"), None, None, None, None, None, None, None, None), None, None))))

        val refinedRequest =
          UserRequest(
            Some(Nino("AB123456C")),
            None,
            WrongCredentialsSelfAssessmentUser(SaUtr("1111111111")),
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
        val result = harness()(refinedRequest)
        status(result) mustBe OK
        contentAsString(result) must include("true")
      }

    }

    "when a user has no PersonDetails in CitizenDetails" - {
      "return the request it was passed" in {

        when(mockCitizenDetailsService.personDetails(any())(any()))
          .thenReturn(Future.successful(PersonDetailsNotFoundResponse))

        val refinedRequest =
          UserRequest(
            Some(Nino("AB123456C")),
            None,
            WrongCredentialsSelfAssessmentUser(SaUtr("1111111111")),
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
        val result = harness()(refinedRequest)
        status(result) mustBe OK
        contentAsString(result) must include("false")
      }

    }
  }
}
