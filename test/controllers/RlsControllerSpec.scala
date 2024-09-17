/*
 * Copyright 2024 HM Revenue & Customs
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
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.admin.RlsInterruptToggle
import models.{AddressesLock, NonFilerSelfAssessmentUser, PersonDetails, UserAnswers}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK, SEE_OTHER}
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, redirectLocation, status}
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{ActionBuilderFixture, BaseSpec, Fixtures}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import views.html.InternalServerErrorView
import views.html.personaldetails.CheckYourAddressInterruptView

import scala.concurrent.Future

class RlsControllerSpec extends BaseSpec {

  val mockAuditConnector: AuditConnector             = mock[AuditConnector]
  val mockCachingHelper: AddressJourneyCachingHelper = mock[AddressJourneyCachingHelper]

  when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(Success))

  def controller: RlsController =
    new RlsController(
      mockAuthJourney,
      mockAuditConnector,
      mockCachingHelper,
      mockEditAddressLockRepository,
      mockFeatureFlagService,
      inject[MessagesControllerComponents],
      inject[CheckYourAddressInterruptView],
      inject[InternalServerErrorView]
    )(inject[ConfigDecorator], ec)

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
      .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, isEnabled = true)))
    when(mockCachingHelper.addToCache(any(), any())(any(), any())) thenReturn {
      Future.successful(UserAnswers.empty("id"))
    }
  }

  "rlsInterruptOnPageLoad" must {
    "return internal server error" when {
      "There is no personal details" in {
        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = false, postal = false)))
        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(personDetails = None, request = request)
            )
        })

        val r: Future[Result] = controller.rlsInterruptOnPageLoad(FakeRequest())

        status(r) mustBe INTERNAL_SERVER_ERROR
      }
    }

    "redirect to home page" when {
      "there is no residential and postal address" in {
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, None, None)

        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = false, postal = false)))
        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(personDetails = Some(personDetails), request = request)
            )
        })

        val r: Future[Result] = controller.rlsInterruptOnPageLoad(FakeRequest())

        status(r) mustBe SEE_OTHER
        redirectLocation(r) mustBe Some("/personal-account")
      }

      "residential address is rls and residential address has been updated" in {
        val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(_.copy(isRls = true))
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, address, None)

        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = true, postal = false)))
        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(personDetails = Some(personDetails), request = request)
            )
        })

        val r: Future[Result] = controller.rlsInterruptOnPageLoad(FakeRequest())

        status(r) mustBe SEE_OTHER
        redirectLocation(r) mustBe Some("/personal-account")
      }

      "postal address is rls and postal address has been updated" in {
        val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(_.copy(isRls = true))
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, None, address)

        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = true, postal = true)))
        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(personDetails = Some(personDetails), request = request)
            )
        })

        val r: Future[Result] = controller.rlsInterruptOnPageLoad(FakeRequest())

        status(r) mustBe SEE_OTHER
        redirectLocation(r) mustBe Some("/personal-account")
      }

      "postal and main addresses are rls and both addresses have been updated" in {
        val mainAddress   = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(_.copy(isRls = true))
        val postalAddress = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(_.copy(isRls = true))
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, mainAddress, postalAddress)

        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = true, postal = true)))
        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(personDetails = Some(personDetails), request = request)
            )
        })

        val r: Future[Result] = controller.rlsInterruptOnPageLoad(FakeRequest())

        status(r) mustBe SEE_OTHER
        redirectLocation(r) mustBe Some("/personal-account")
      }
    }

    "return ok" when {
      "residential address is rls" in {
        val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(_.copy(isRls = true))
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, address, None)

        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = false, postal = false)))
        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(personDetails = Some(personDetails), request = request)
            )
        })

        val r: Future[Result] = controller.rlsInterruptOnPageLoad(FakeRequest())

        status(r) mustBe OK
        contentAsString(r) must include("""id="main_address"""")
        contentAsString(r) mustNot include("""id="postal_address"""")
        contentAsString(r) must include("You need to update your main address to receive post from HMRC.")
      }

      "postal address is rls" in {
        val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(_.copy(isRls = true))
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, None, address)

        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = false, postal = false)))
        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(personDetails = Some(personDetails), request = request)
            )
        })

        val r: Future[Result] = controller.rlsInterruptOnPageLoad(FakeRequest())

        status(r) mustBe OK
        contentAsString(r) must include("""id="postal_address"""")
        contentAsString(r) mustNot include("""id="main_address"""")
        contentAsString(r) must include("You need to update your postal address to receive post from HMRC.")
      }

      "postal and residential address are rls" in {
        val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(_.copy(isRls = true))
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, address, address)

        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = false, postal = false)))
        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(personDetails = Some(personDetails), request = request)
            )
        })

        val r: Future[Result] = controller.rlsInterruptOnPageLoad(FakeRequest())

        status(r) mustBe OK
        contentAsString(r) must include("""id="main_address"""")
        contentAsString(r) must include("""id="postal_address"""")
        contentAsString(r) must include("You need to update your main and postal addresses to receive post from HMRC.")
      }

      "residential address is rls and postal address has been updated" in {
        val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(_.copy(isRls = true))
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, address, None)

        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = false, postal = true)))
        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(personDetails = Some(personDetails), request = request)
            )
        })

        val r: Future[Result] = controller.rlsInterruptOnPageLoad(FakeRequest())

        status(r) mustBe OK
        contentAsString(r) must include("""id="main_address"""")
        contentAsString(r) mustNot include("""id="postal_address"""")
        contentAsString(r) must include("You need to update your main address to receive post from HMRC.")
      }

      "postal and residential address is rls and residential address has been updated" in {
        val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(_.copy(isRls = true))
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, address, address)

        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = true, postal = false)))
        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(personDetails = Some(personDetails), request = request)
            )
        })

        val r: Future[Result] = controller.rlsInterruptOnPageLoad(FakeRequest())

        status(r) mustBe OK
        contentAsString(r) mustNot include("""id="main_address"""")
        contentAsString(r) must include("""id="postal_address"""")
        contentAsString(r) must include("You need to update your postal address to receive post from HMRC.")
      }

      "postal and residential address is rls and correspondence address has been updated" in {
        val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(_.copy(isRls = true))
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, address, address)

        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = false, postal = true)))
        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(personDetails = Some(personDetails), request = request)
            )
        })

        val r: Future[Result] = controller.rlsInterruptOnPageLoad(FakeRequest())

        status(r) mustBe OK
        contentAsString(r) must include("""id="main_address"""")
        contentAsString(r) mustNot include("""id="postal_address"""")
        contentAsString(r) must include("You need to update your main address to receive post from HMRC.")
      }

      "postal address is rls and residential address has been updated" in {
        val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(_.copy(isRls = true))
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, None, address)

        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = true, postal = false)))
        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(personDetails = Some(personDetails), request = request)
            )
        })

        val r: Future[Result] = controller.rlsInterruptOnPageLoad(FakeRequest())

        status(r) mustBe OK
        contentAsString(r) mustNot include("""id="main_address"""")
        contentAsString(r) must include("""id="postal_address"""")
        contentAsString(r) must include("You need to update your postal address to receive post from HMRC.")
      }

      "return a 200 status when accessing index page with good nino and a non sa User" in {
        val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(_.copy(isRls = true))
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, address, address)

        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = false, postal = false)))
        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(
                saUser = NonFilerSelfAssessmentUser,
                personDetails = Some(personDetails),
                request = request
              )
            )
        })

        val r: Future[Result] = controller.rlsInterruptOnPageLoad(FakeRequest())
        status(r) mustBe OK
      }
    }

    "show the remove postal address" when {
      "a residential address exists" in {
        val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(_.copy(isRls = true))
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, Fixtures.buildPersonDetailsCorrespondenceAddress.address, address)

        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = false, postal = false)))
        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(personDetails = Some(personDetails), request = request)
            )
        })

        val r: Future[Result] = controller.rlsInterruptOnPageLoad(FakeRequest())

        status(r) mustBe OK
        contentAsString(r) mustNot include("""id="main_address"""")
        contentAsString(r) must include("""id="postal_address"""")
        contentAsString(r) must include("You need to update your postal address to receive post from HMRC.")
        contentAsString(r) must include(controllers.address.routes.ClosePostalAddressController.onPageLoad.url)
      }
    }

    "hide the remove postal address" when {
      "a residential address does not exist" in {
        val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(_.copy(isRls = true))
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, None, address)

        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = false, postal = false)))
        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(personDetails = Some(personDetails), request = request)
            )
        })

        val r: Future[Result] = controller.rlsInterruptOnPageLoad(FakeRequest())

        status(r) mustBe OK
        contentAsString(r) mustNot include("""id="main_address"""")
        contentAsString(r) must include("""id="postal_address"""")
        contentAsString(r) must include("You need to update your postal address to receive post from HMRC.")
        contentAsString(r) mustNot include(controllers.address.routes.ClosePostalAddressController.onPageLoad.url)

      }
    }
  }
}
