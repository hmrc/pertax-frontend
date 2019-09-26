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

package controllers.auth

import controllers.auth.requests.{RefinedRequest, UserRequest}
import models.{AmbiguousFilerSelfAssessmentUser, Person, PersonDetails}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FreeSpec, MustMatchers}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results.Ok
import play.api.mvc.{AnyContent, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.partials.MessageFrontendService
import services.{CitizenDetailsService, PersonDetailsSuccessResponse}
import uk.gov.hmrc.domain.SaUtr

import scala.concurrent.Future

class GetPersonDetailsActionSpec extends FreeSpec with MustMatchers with MockitoSugar with GuiceOneAppPerSuite {

  val mockMessageFrontendService = mock[MessageFrontendService]
  val mockCitizenDetailsService = mock[CitizenDetailsService]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[MessageFrontendService].toInstance(mockMessageFrontendService))
    .build()

  def harness[A]()(implicit request: RefinedRequest[A]): Future[Result] = {

    lazy val actionProvider = app.injector.instanceOf[GetPersonDetailsAction]

    actionProvider.invokeBlock(
      request, { userRequest: UserRequest[AnyContent] =>
        Future.successful(
          Ok(s"Person Details: ${userRequest.personDetails.isDefined}")
        )
      }
    )
  }

  "GetPersonDetailsAction must" - {
    "when a user is ambiguous" - {

      "return the request it was passed" in {
        when(mockMessageFrontendService.getUnreadMessageCount(any()))
          .thenReturn(Future.successful(Some(1)))

        when(mockCitizenDetailsService.personDetails(any())(any()))
          .thenReturn(Future.successful(PersonDetailsSuccessResponse(
            PersonDetails("", Person(None, None, None, None, None, None, None, None, None), None, None))))

        val refinedRequest =
          RefinedRequest(None, AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111")), "", FakeRequest())
        val result = harness()(refinedRequest)
        status(result) mustBe OK
        contentAsString(result) must contain("true")
      }

    }
  }
}
