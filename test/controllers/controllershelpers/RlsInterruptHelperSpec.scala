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

package controllers.controllershelpers

import cats.data.EitherT
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models.admin.RlsInterruptToggle
import models.{AddressesLock, NonFilerSelfAssessmentUser, PersonDetails, UserAnswers}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.Application
import play.api.inject.bind
import play.api.mvc.Results.*
import play.api.mvc.{AnyContent, Result}
import play.api.test.FakeRequest
import repositories.JourneyCacheRepository
import services.CitizenDetailsService
import testUtils.Fixtures.{buildFakeAddress, buildFakeCorrespondenceAddress}
import testUtils.{BaseSpec, Fixtures}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import scala.concurrent.Future

class RlsInterruptHelperSpec extends BaseSpec {

  val okBlock: Result = Ok("Block")

  val interrupt: Result = SeeOther("/personal-account/update-your-address")

  implicit lazy val mockConfigDecorator: ConfigDecorator      = mock[ConfigDecorator]
  lazy val mockCitizenDetailsService: CitizenDetailsService   = mock[CitizenDetailsService]
  lazy val mockJourneyCacheRepository: JourneyCacheRepository = mock[JourneyCacheRepository]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(bind[CitizenDetailsService].toInstance(mockCitizenDetailsService))
    .overrides(bind[ConfigDecorator].toInstance(mockConfigDecorator))
    .overrides(bind[JourneyCacheRepository].toInstance(mockJourneyCacheRepository))
    .build()

  lazy val rlsInterruptHelper: RlsInterruptHelper = app.injector.instanceOf[RlsInterruptHelper]

  implicit val userRequest: UserRequest[AnyContent] = UserRequest(
    Fixtures.fakeNino,
    NonFilerSelfAssessmentUser,
    Credentials("", "GovernmentGateway"),
    ConfidenceLevel.L200,
    None,
    Set(),
    None,
    None,
    FakeRequest(),
    UserAnswers.empty
  )

  "enforceByRlsStatus" when {
    "the enforce getAddressStatusFromCID toggle is set to true" must {

      "return the result of the block when residential and correspondence are not 1" in {
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(
          person,
          Some(buildFakeAddress),
          Some(buildFakeCorrespondenceAddress)
        )
        when(mockCitizenDetailsService.personDetails(any())(any(), any(), any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails))
        )

        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = false, postal = false)))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
          .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, isEnabled = true)))

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe okBlock
      }

      "redirect to /personal-account/check-your-address when residential address status is 1" in {
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(
          person,
          Some(buildFakeAddress.copy(isRls = true)),
          Some(buildFakeCorrespondenceAddress)
        )
        when(mockCitizenDetailsService.personDetails(any())(any(), any(), any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails))
        )

        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = false, postal = false)))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
          .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, isEnabled = true)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Fixtures.fakeNino,
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          None,
          Set(),
          None,
          None,
          FakeRequest(),
          UserAnswers.empty
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe interrupt
      }

      "redirect to /personal-account/check-your-address when correspondence address status is 1" in {
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(
          person,
          Some(buildFakeAddress),
          Some(buildFakeCorrespondenceAddress.copy(isRls = true))
        )
        when(mockCitizenDetailsService.personDetails(any())(any(), any(), any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails))
        )

        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = false, postal = false)))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
          .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, isEnabled = true)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Fixtures.fakeNino,
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          None,
          Set(),
          None,
          None,
          FakeRequest(),
          UserAnswers.empty
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe interrupt
      }

      "redirect to /personal-account/check-your-address when both residential and correspondence address status is 1" in {
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(
          person,
          Some(buildFakeAddress.copy(isRls = true)),
          Some(buildFakeCorrespondenceAddress.copy(isRls = true))
        )
        when(mockCitizenDetailsService.personDetails(any())(any(), any(), any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails))
        )

        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = false, postal = false)))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
          .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, isEnabled = true)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Fixtures.fakeNino,
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          None,
          Set(),
          None,
          None,
          FakeRequest(),
          UserAnswers.empty
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe interrupt
      }

      "return the result as s block when residential address status is 1 and residential address has been updated" in {
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(
          person,
          Some(buildFakeAddress.copy(isRls = true)),
          Some(buildFakeCorrespondenceAddress)
        )
        when(mockCitizenDetailsService.personDetails(any())(any(), any(), any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails))
        )

        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = true, postal = false)))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
          .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, isEnabled = true)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Fixtures.fakeNino,
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          None,
          Set(),
          None,
          None,
          FakeRequest(),
          UserAnswers.empty
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe okBlock
      }

      "return the result as a block when postal address status is 1 and postal address has been updated" in {
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(
          person,
          Some(buildFakeAddress),
          Some(buildFakeCorrespondenceAddress.copy(isRls = true))
        )
        when(mockCitizenDetailsService.personDetails(any())(any(), any(), any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails))
        )

        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = false, postal = true)))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
          .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, isEnabled = true)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Fixtures.fakeNino,
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          None,
          Set(),
          None,
          None,
          FakeRequest(),
          UserAnswers.empty
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe okBlock
      }

      "return result as a block when both residential and correspondence address status is 1 and both addresses have been updated" in {
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(
          person,
          Some(buildFakeAddress.copy(isRls = true)),
          Some(buildFakeCorrespondenceAddress.copy(isRls = true))
        )
        when(mockCitizenDetailsService.personDetails(any())(any(), any(), any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails))
        )

        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = true, postal = true)))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
          .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, isEnabled = true)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Fixtures.fakeNino,
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          None,
          Set(),
          None,
          None,
          FakeRequest(),
          UserAnswers.empty
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe okBlock
      }

      "redirect to /personal-account/check-your-address when both residential and correspondence address status is 1 and residential address has been updated" in {
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(
          person,
          Some(buildFakeAddress.copy(isRls = true)),
          Some(buildFakeCorrespondenceAddress.copy(isRls = true))
        )
        when(mockCitizenDetailsService.personDetails(any())(any(), any(), any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails))
        )

        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = true, postal = false)))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
          .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, isEnabled = true)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Fixtures.fakeNino,
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          None,
          Set(),
          None,
          None,
          FakeRequest(),
          UserAnswers.empty
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe interrupt
      }

      "redirect to /personal-account/check-your-address when both residential and correspondence address status is 1 and correspondence address has been updated" in {
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(
          person,
          Some(buildFakeAddress.copy(isRls = true)),
          Some(buildFakeCorrespondenceAddress.copy(isRls = true))
        )
        when(mockCitizenDetailsService.personDetails(any())(any(), any(), any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails))
        )

        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = false, postal = true)))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
          .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, isEnabled = true)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Fixtures.fakeNino,
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          None,
          Set(),
          None,
          None,
          FakeRequest(),
          UserAnswers.empty
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe interrupt
      }

    }

    "the enforce rlsInterruptToggle toggle is set to false" must {
      "return the result of a passed in block" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
          .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, isEnabled = false)))
        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(main = false, postal = false)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Fixtures.fakeNino,
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          None,
          Set(),
          None,
          None,
          FakeRequest(),
          UserAnswers.empty
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock))
        result.futureValue mustBe okBlock
      }
    }
  }
}
