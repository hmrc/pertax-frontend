/*
 * Copyright 2023 HM Revenue & Customs
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

import cats.data.EitherT
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models.admin.{NpsOutageToggle, SCAWrapperToggle}
import models.{Person, PersonDetails, WrongCredentialsSelfAssessmentUser}
import org.mockito.ArgumentMatchers.any
import play.api.Application
import play.api.inject.bind
import play.api.mvc.Result
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.CitizenDetailsService
import services.partials.MessageFrontendService
import testUtils.{BaseSpec, Fixtures}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import scala.concurrent.Future

class GetPersonDetailsActionSpec extends BaseSpec {

  val mockMessageFrontendService: MessageFrontendService = mock[MessageFrontendService]
  val mockCitizenDetailsService: CitizenDetailsService   = mock[CitizenDetailsService]
  val configDecorator: ConfigDecorator                   = mock[ConfigDecorator]

  override lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(bind[MessageFrontendService].toInstance(mockMessageFrontendService))
    .overrides(bind[CitizenDetailsService].toInstance(mockCitizenDetailsService))
    .overrides(bind[ConfigDecorator].toInstance(configDecorator))
    .configure(Map("metrics.enabled" -> false))
    .build()

  val refinedRequest: UserRequest[AnyContentAsEmpty.type] =
    UserRequest(
      Fixtures.fakeNino,
      Some(Fixtures.fakeNino),
      None,
      WrongCredentialsSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
      Credentials("", "GovernmentGateway"),
      ConfidenceLevel.L50,
      None,
      None,
      Set(),
      None,
      None,
      None,
      FakeRequest("", "")
    )

  val personDetails: PersonDetails =
    PersonDetails(Person(Some("TestFirstName"), None, None, None, None, None, None, None, None), None, None)

  val personDetailsBlock: UserRequest[_] => Future[Result] = userRequest => {
    val person = userRequest.personDetails match {
      case Some(PersonDetails(Person(Some(firstName), None, None, None, None, None, None, None, None), None, None)) =>
        firstName
      case _                                                                                                        => "No Person Details Defined"
    }

    Future.successful(Ok(s"Person Details: $person"))
  }

  val unreadMessageCountBlock: UserRequest[_] => Future[Result] = userRequest =>
    Future.successful(Ok(s"Person Details: ${userRequest.unreadMessageCount.getOrElse("no message count present")}"))

  def harness[A](block: UserRequest[_] => Future[Result])(implicit request: UserRequest[A]): Future[Result] = {
    lazy val actionProvider = app.injector.instanceOf[GetPersonDetailsAction]
    actionProvider.invokeBlock(request, block)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockCitizenDetailsService)
    when(mockMessageFrontendService.getUnreadMessageCount(any()))
      .thenReturn(Future.successful(Some(1)))
  }

  "GetPersonDetailsAction" when {

    "a user has PersonDetails in CitizenDetails" must {

      "add the PersonDetails to the request" in {
        when(mockCitizenDetailsService.personDetails(any())(any(), any()))
          .thenReturn(EitherT[Future, UpstreamErrorResponse, PersonDetails](Future.successful(Right(personDetails))))

        val result = harness(personDetailsBlock)(refinedRequest)
        status(result) mustBe OK
        contentAsString(result) mustBe "Person Details: TestFirstName"

        verify(mockCitizenDetailsService, times(1)).personDetails(any())(any(), any())
      }
    }

    "when a user has no PersonDetails in CitizenDetails" must {
      "return the request it was passed" in {
        when(mockFeatureFlagService.get(NpsOutageToggle))
          .thenReturn(Future.successful(FeatureFlag(NpsOutageToggle, isEnabled = false)))

        when(mockCitizenDetailsService.personDetails(any())(any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, PersonDetails](
              Future.successful(Left(UpstreamErrorResponse("", NOT_FOUND)))
            )
          )

        val result = harness(personDetailsBlock)(refinedRequest)
        status(result) mustBe OK
        contentAsString(result) mustBe "Person Details: No Person Details Defined"

        verify(mockCitizenDetailsService, times(1)).personDetails(any())(any(), any())
      }

    }

    "when the NpsOutageToggle is set to true" must {
      "return None" in {
        when(mockFeatureFlagService.get(NpsOutageToggle))
          .thenReturn(Future.successful(FeatureFlag(NpsOutageToggle, isEnabled = true)))

        val result = harness(personDetailsBlock)(refinedRequest)
        status(result) mustBe OK
        contentAsString(result) mustBe "Person Details: No Person Details Defined"

        verify(mockCitizenDetailsService, times(0)).personDetails(any())(any(), any())
      }
    }

    "when the person details message count toggle is set to true" must {
      "return a request with the unread message count" when {
        "SCA wrapper toggle is false" in {
          when(mockFeatureFlagService.get(SCAWrapperToggle))
            .thenReturn(Future.successful(FeatureFlag(SCAWrapperToggle, isEnabled = false)))

          when(mockCitizenDetailsService.personDetails(any())(any(), any()))
            .thenReturn(EitherT[Future, UpstreamErrorResponse, PersonDetails](Future.successful(Right(personDetails))))

          when(configDecorator.personDetailsMessageCountEnabled).thenReturn(true)

          val result = harness(unreadMessageCountBlock)(refinedRequest)
          status(result) mustBe OK
          contentAsString(result) mustBe "Person Details: 1"

          verify(mockCitizenDetailsService, times(1)).personDetails(any())(any(), any())
        }
      }
    }

    "when the person details message count toggle is set to false" must {
      "return a request with the unread message count" in {
        when(mockFeatureFlagService.get(NpsOutageToggle))
          .thenReturn(Future.successful(FeatureFlag(NpsOutageToggle, isEnabled = false)))

        when(mockCitizenDetailsService.personDetails(any())(any(), any()))
          .thenReturn(EitherT[Future, UpstreamErrorResponse, PersonDetails](Future.successful(Right(personDetails))))

        when(configDecorator.personDetailsMessageCountEnabled).thenReturn(false)

        val result = harness(unreadMessageCountBlock)(refinedRequest)
        status(result) mustBe OK
        contentAsString(result) mustBe "Person Details: no message count present"

        verify(mockCitizenDetailsService, times(1)).personDetails(any())(any(), any())
      }
    }
  }
}
