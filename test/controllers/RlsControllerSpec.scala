/*
 * Copyright 2022 HM Revenue & Customs
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

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.controllershelpers.{CountryHelper, HomeCardGenerator, HomePageCachingHelper}
import models.{Address, NonFilerSelfAssessmentUser, Person, PersonDetails}
import org.mockito.Mockito.when
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK, SEE_OTHER}
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, redirectLocation, status}
import uk.gov.hmrc.auth.core.ConfidenceLevel.L200
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.renderer.TemplateRenderer
import util.UserRequestFixture.buildUserRequest
import util.{ActionBuilderFixture, BaseSpec, Fixtures}
import views.html.{HomeView, InternalServerErrorView}
import views.html.personaldetails.CheckYourAddressInterruptView

import scala.concurrent.Future
import scala.util.Random

class RlsControllerSpec extends BaseSpec {

  private val generator = new Generator(new Random())
  private val testNino: Nino = generator.nextNino

  val mockAuthJourney = mock[AuthJourney]

  def controller: RlsController =
    new RlsController(
      mockAuthJourney,
      injected[MessagesControllerComponents],
      injected[CheckYourAddressInterruptView],
      injected[InternalServerErrorView]
    )(injected[ConfigDecorator], injected[TemplateRenderer], injected[CountryHelper], ec)

  "rlsInterruptOnPageLoad" must {
    "return internal server error" when {
      "There is no personal details" in {
        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(personDetails = None, request = request)
            )
        })

        val r: Future[Result] = controller.rlsInterruptOnPageLoad()(FakeRequest())

        status(r) mustBe INTERNAL_SERVER_ERROR
      }
    }

    "redirect to home page" when {
      "there is no residential and postal address" in {
        val person = Fixtures.buildPersonDetails.person
        val personDetails = PersonDetails(person, None, None)

        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(personDetails = Some(personDetails), request = request)
            )
        })

        val r: Future[Result] = controller.rlsInterruptOnPageLoad()(FakeRequest())

        status(r) mustBe SEE_OTHER
        redirectLocation(r) mustBe Some("/personal-account")
      }
    }

    "return ok" when {
      "residential address is rls" in {
        val address = Fixtures.buildPersonDetails.address.map(_.copy(isRls = true))
        val person = Fixtures.buildPersonDetails.person
        val personDetails = PersonDetails(person, address, None)

        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(personDetails = Some(personDetails), request = request)
            )
        })

        val r: Future[Result] = controller.rlsInterruptOnPageLoad()(FakeRequest())

        status(r) mustBe OK
        contentAsString(r) must include("""id="main_address"""")
      }

      "postal address is rls" in {
        val address = Fixtures.buildPersonDetails.address.map(_.copy(isRls = true))
        val person = Fixtures.buildPersonDetails.person
        val personDetails = PersonDetails(person, None, address)

        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(personDetails = Some(personDetails), request = request)
            )
        })

        val r: Future[Result] = controller.rlsInterruptOnPageLoad()(FakeRequest())

        status(r) mustBe OK
        contentAsString(r) must include("""id="postal_address"""")
      }

      "postal and residantial address is rls" in {
        val address = Fixtures.buildPersonDetails.address.map(_.copy(isRls = true))
        val person = Fixtures.buildPersonDetails.person
        val personDetails = PersonDetails(person, address, address)

        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(personDetails = Some(personDetails), request = request)
            )
        })

        val r: Future[Result] = controller.rlsInterruptOnPageLoad()(FakeRequest())

        status(r) mustBe OK
        contentAsString(r) must include("""id="main_address"""")
        contentAsString(r) must include("""id="postal_address"""")
      }
    }

    "return a 200 status when accessing index page with good nino and a non sa User" ignore {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              request = request
            )
          )
      })

      val r: Future[Result] = controller.rlsInterruptOnPageLoad()(FakeRequest())
      status(r) mustBe OK
    }
  }

  "return a 200 status when accessing index page with a nino that does not map to any personal details in citizen-details" ignore {

    when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(
          buildUserRequest(request = request)
        )
    })

    val r: Future[Result] = controller.rlsInterruptOnPageLoad()(FakeRequest())
    status(r) mustBe OK
  }
}
