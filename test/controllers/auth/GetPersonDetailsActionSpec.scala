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
import models.admin.GetPersonFromCitizenDetailsToggle
import models.{Person, PersonDetails, WrongCredentialsSelfAssessmentUser}
import org.mockito.ArgumentMatchers.any
import play.api.Application
import play.api.inject.bind
import play.api.mvc.Results.Ok
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.CitizenDetailsService
import services.partials.MessageFrontendService
import testUtils.{BaseSpec, Fixtures}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{Generator, Nino, SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import scala.concurrent.Future
import scala.util.Random

class GetPersonDetailsActionSpec extends BaseSpec {

  val mockMessageFrontendService: MessageFrontendService = mock[MessageFrontendService]
  val mockCitizenDetailsService: CitizenDetailsService   = mock[CitizenDetailsService]
  val configDecorator: ConfigDecorator                   = mock[ConfigDecorator]
  private val requestNino: Nino                          = Nino(Fixtures.fakeNino.nino)
  private val citizenDetailsNino: Nino                   = Nino(new Generator(new Random()).nextNino.nino)
  override lazy val app: Application                     = localGuiceApplicationBuilder()
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
      FakeRequest("", "")
    )

  val personDetails: PersonDetails =
    PersonDetails(
      person = Person(
        firstName = Some("TestFirstName"),
        middleName = None,
        lastName = None,
        initials = None,
        title = None,
        honours = None,
        sex = None,
        dateOfBirth = None,
        nino = Some(citizenDetailsNino)
      ),
      address = None,
      correspondenceAddress = None
    )

  val personDetailsNoNino: PersonDetails =
    personDetails copy (person = personDetails.person copy (nino = None))

  val personDetailsBlock: UserRequest[_] => Future[Result] = userRequest => {
    val person = userRequest.personDetails match {
      case Some(PersonDetails(Person(Some(firstName), None, None, None, None, None, None, None, _), None, None)) =>
        s"Firstname: $firstName, nino: ${userRequest.nino.getOrElse("None")}"
      case _                                                                                                     => "No Person Details Defined"
    }

    Future.successful(Ok(s"Person Details: $person"))
  }

  def harness[A](block: UserRequest[_] => Future[Result])(implicit request: UserRequest[A]): Future[Result] = {
    lazy val actionProvider = app.injector.instanceOf[GetPersonDetailsAction]
    actionProvider.invokeBlock(request, block)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockCitizenDetailsService)
  }

  "GetPersonDetailsAction" when {

    "a user has PersonDetails in CitizenDetails" must {

      "add the PersonDetails to the request + use the nino from citizen details when present" in {
        when(mockCitizenDetailsService.personDetails(any())(any(), any()))
          .thenReturn(EitherT[Future, UpstreamErrorResponse, PersonDetails](Future.successful(Right(personDetails))))

        val result = harness(personDetailsBlock)(refinedRequest)
        status(result) mustBe OK
        contentAsString(result) mustBe s"Person Details: Firstname: TestFirstName, nino: ${citizenDetailsNino.nino}"

        verify(mockCitizenDetailsService, times(1)).personDetails(any())(any(), any())
      }

      "add the PersonDetails to the request + use the nino from request when citizen details nino not present" in {
        when(mockCitizenDetailsService.personDetails(any())(any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, PersonDetails](
              Future.successful(Right(personDetailsNoNino))
            )
          )

        val result = harness(personDetailsBlock)(refinedRequest)
        status(result) mustBe OK
        contentAsString(result) mustBe s"Person Details: Firstname: TestFirstName, nino: ${requestNino.nino}"

        verify(mockCitizenDetailsService, times(1)).personDetails(any())(any(), any())
      }
    }

    "GetPersonFromCitizenDetailsToggle is true" must {
      "return the request it was passed when a user has no PersonDetails in CitizenDetails " in {
        when(mockFeatureFlagService.get(GetPersonFromCitizenDetailsToggle))
          .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))

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

      "return no person details when CitizenDetails returns bad gateway" in {
        when(mockFeatureFlagService.get(GetPersonFromCitizenDetailsToggle))
          .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))

        when(mockCitizenDetailsService.personDetails(any())(any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, PersonDetails](
              Future.successful(Left(UpstreamErrorResponse("", BAD_GATEWAY)))
            )
          )

        val result = harness(personDetailsBlock)(refinedRequest)
        status(result) mustBe OK
        contentAsString(result) mustBe "Person Details: No Person Details Defined"

        verify(mockCitizenDetailsService, times(1)).personDetails(any())(any(), any())
      }

    }

    "when the GetPersonFromCitizenDetailsToggle is set to false" must {
      "return None" in {
        when(mockFeatureFlagService.get(GetPersonFromCitizenDetailsToggle))
          .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = false)))

        val result = harness(personDetailsBlock)(refinedRequest)
        status(result) mustBe OK
        contentAsString(result) mustBe "Person Details: No Person Details Defined"

        verify(mockCitizenDetailsService, times(0)).personDetails(any())(any(), any())
      }
    }
  }
}
