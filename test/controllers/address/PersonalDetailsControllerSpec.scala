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

package controllers.address

import controllers.controllershelpers.{RlsInterruptHelper, RlsInterruptHelperImpl}
import models.admin.RlsInterruptToggle
import models.dto.AddressPageVisitedDto
import models.{PersonDetails, UserAnswers, UserName}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import play.api.http.Status._
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{defaultAwaitTimeout, redirectLocation, status}
import repositories.EditAddressLockRepository
import routePages.HasAddressAlreadyVisitedPage
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name}
import uk.gov.hmrc.auth.core.{ConfidenceLevel, Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.domain.SaUtrGenerator
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import viewmodels.PersonalDetailsViewModel
import views.html.personaldetails.PersonalDetailsView

import scala.concurrent.Future

class PersonalDetailsControllerSpec extends AddressBaseSpec {
  val utr: String                          = new SaUtrGenerator().nextSaUtr.utr
  val fakeCredentials: Credentials         = Credentials("foo", "bar")
  val fakeConfidenceLevel: ConfidenceLevel = ConfidenceLevel.L200
  val saEnrolments: Set[Enrolment]         = Set(Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", utr)), "Activated"))
  val userName: Option[UserName]           = Some(UserName(Name(Some("Firstname"), Some("Lastname"))))

  trait LocalSetup extends AddressControllerSetup {
    def currentRequest[A]: Request[A] = FakeRequest().asInstanceOf[Request[A]]

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
      .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, isEnabled = true)))

    def rlsInterruptHelper: RlsInterruptHelper =
      new RlsInterruptHelperImpl(cc, inject[EditAddressLockRepository], mockFeatureFlagService)

    def controller: PersonalDetailsController =
      new PersonalDetailsController(
        inject[PersonalDetailsViewModel],
        mockEditAddressLockRepository,
        mockAuthJourney,
        addressJourneyCachingHelper,
        mockAuditConnector,
        rlsInterruptHelper,
        mockAgentClientAuthorisationService,
        cc,
        displayAddressInterstitialView,
        inject[PersonalDetailsView],
        mockFeatureFlagService,
        internalServerErrorView
      )

    def defaultUserAnswers: UserAnswers = UserAnswers.empty("id")

    when(mockJourneyCacheRepository.get(any())).thenReturn(Future.successful(defaultUserAnswers))
  }

  "Calling redirectToYourProfile" must {
    "redirect to the profile-and-settings page" in new LocalSetup {

      val result: Future[Result] = controller.redirectToYourProfile()(FakeRequest())

      status(result) mustBe MOVED_PERMANENTLY
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
    }
  }

  "Calling onPageLoad" must {
    "redirect to the rls interrupt page" when {
      "main address has an rls status with true" in new LocalSetup {

        override def personDetailsResponse: PersonDetails = {
          val address = fakeAddress.copy(isRls = true)
          fakePersonDetails.copy(address = Some(address))
        }

        override def personDetailsForRequest: Option[PersonDetails] = {
          val address = fakeAddress.copy(isRls = true)
          Some(fakePersonDetails.copy(address = Some(address)))
        }

        val result: Future[Result] = controller.onPageLoad(FakeRequest())

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some("/personal-account/update-your-address")
      }

      "postal address has an rls status with true" in new LocalSetup {

        override def personDetailsResponse: PersonDetails = {
          val address = fakeAddress.copy(isRls = true)
          fakePersonDetails.copy(correspondenceAddress = Some(address))
        }

        override def personDetailsForRequest: Option[PersonDetails] = {
          val address = fakeAddress.copy(isRls = true)
          Some(fakePersonDetails.copy(address = Some(address)))
        }

        val result: Future[Result] = controller.onPageLoad(FakeRequest())

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some("/personal-account/update-your-address")
      }

      "main and postal address has an rls status with true" in new LocalSetup {

        override def personDetailsResponse: PersonDetails = {
          val address = fakeAddress.copy(isRls = true)
          fakePersonDetails.copy(address = Some(address), correspondenceAddress = Some(address))
        }

        override def personDetailsForRequest: Option[PersonDetails] = {
          val address = fakeAddress.copy(isRls = true)
          Some(fakePersonDetails.copy(address = Some(address)))
        }

        val result: Future[Result] = controller.onPageLoad(FakeRequest())

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some("/personal-account/update-your-address")
      }
    }

    "show the your profile page" when {
      "no address has an rls status with true" in new LocalSetup {

        val result: Future[Result] = controller.onPageLoad(FakeRequest())

        status(result) mustBe OK
      }
    }
  }

  "Calling onPageLoad" must {
    "call citizenDetailsService.fakePersonDetails and return 200" in new LocalSetup {

      override def defaultUserAnswers: UserAnswers =
        UserAnswers.empty("id").setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      val result: Future[Result] = controller.onPageLoad(FakeRequest())

      status(result) mustBe OK

      verify(mockJourneyCacheRepository, times(1)).set(defaultUserAnswers)
      verify(mockEditAddressLockRepository, times(1)).get(any())
    }
  }
}
