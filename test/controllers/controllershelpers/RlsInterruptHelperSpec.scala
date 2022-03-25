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

package controllers.controllershelpers

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models.{AddressJourneyTTLModel, AddressesLock, EditCorrespondenceAddress, EditResidentialAddress, NonFilerSelfAssessmentUser, UserName}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.mvc.Results._
import play.api.mvc.{AnyContent, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name}
import util.Fixtures.{buildFakeAddress, buildFakeCorrespondenceAddress, buildFakePersonDetails, fakeNino}
import util.{BaseSpec, Fixtures}

import java.time.Instant
import scala.concurrent.Future

class RlsInterruptHelperSpec extends BaseSpec {

  val okBlock: Result = Ok("Block")

  val interrupt: Result = SeeOther("/personal-account/update-your-address")

  implicit val mockConfigDecorator: ConfigDecorator = mock[ConfigDecorator]

  val rlsInterruptHelper: RlsInterruptHelper = app.injector.instanceOf[RlsInterruptHelper]

  implicit val userRequest: UserRequest[AnyContent] = UserRequest(
    Some(Fixtures.fakeNino),
    Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
    NonFilerSelfAssessmentUser,
    Credentials("", "GovernmentGateway"),
    ConfidenceLevel.L200,
    None,
    None,
    Set(),
    None,
    None,
    None,
    None,
    FakeRequest()
  )

  "enforceByRlsStatus" when {
    "the enforce getAddressStatusFromCID toggle is set to true" must {
      when(mockConfigDecorator.rlsInterruptToggle).thenReturn(true)

      "return the result of the block when residential and correspondence are not 1" in {

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe okBlock
      }

      "redirect to /personal-account/check-your-address when residential address status is 1" in {
        when(mockEditAddressLockRepository.getAddressesLock(any())(any(), any()))
          .thenReturn(Future.successful(AddressesLock(false, false)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Some(Fixtures.fakeNino),
          Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          Some(buildFakePersonDetails.copy(address = Some(buildFakeAddress.copy(isRls = true)))),
          None,
          Set(),
          None,
          None,
          None,
          None,
          FakeRequest()
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe interrupt
      }

      "redirect to /personal-account/check-your-address when correspondence address status is 1" in {
        when(mockEditAddressLockRepository.getAddressesLock(any())(any(), any()))
          .thenReturn(Future.successful(AddressesLock(false, false)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Some(Fixtures.fakeNino),
          Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          Some(
            buildFakePersonDetails.copy(correspondenceAddress = Some(buildFakeCorrespondenceAddress.copy(isRls = true)))
          ),
          None,
          Set(),
          None,
          None,
          None,
          None,
          FakeRequest()
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe interrupt
      }

      "redirect to /personal-account/check-your-address when both residential and correspondence address status is 1" in {
        when(mockEditAddressLockRepository.getAddressesLock(any())(any(), any()))
          .thenReturn(Future.successful(AddressesLock(false, false)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Some(Fixtures.fakeNino),
          Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          Some(
            buildFakePersonDetails.copy(
              address = Some(buildFakeAddress.copy(isRls = true)),
              correspondenceAddress = Some(buildFakeCorrespondenceAddress.copy(isRls = true))
            )
          ),
          None,
          Set(),
          None,
          None,
          None,
          None,
          FakeRequest()
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe interrupt
      }

      "return the result as s block when residential address status is 1 and residential address has been updated" in {
        when(mockEditAddressLockRepository.getAddressesLock(any())(any(), any()))
          .thenReturn(Future.successful(AddressesLock(true, false)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Some(Fixtures.fakeNino),
          Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          Some(buildFakePersonDetails.copy(address = Some(buildFakeAddress.copy(isRls = true)))),
          None,
          Set(),
          None,
          None,
          None,
          None,
          FakeRequest()
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe okBlock
      }

      "return the result as a block when postal address status is 1 and postal address has been updated" in {
        when(mockEditAddressLockRepository.getAddressesLock(any())(any(), any()))
          .thenReturn(Future.successful(AddressesLock(false, true)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Some(Fixtures.fakeNino),
          Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          Some(
            buildFakePersonDetails.copy(correspondenceAddress = Some(buildFakeCorrespondenceAddress.copy(isRls = true)))
          ),
          None,
          Set(),
          None,
          None,
          None,
          None,
          FakeRequest()
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe okBlock
      }

      "return result as a block when both residential and correspondence address status is 1 and both addresses have been updated" in {
        when(mockEditAddressLockRepository.getAddressesLock(any())(any(), any()))
          .thenReturn(Future.successful(AddressesLock(true, true)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Some(Fixtures.fakeNino),
          Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          Some(
            buildFakePersonDetails.copy(
              address = Some(buildFakeAddress.copy(isRls = true)),
              correspondenceAddress = Some(buildFakeCorrespondenceAddress.copy(isRls = true))
            )
          ),
          None,
          Set(),
          None,
          None,
          None,
          None,
          FakeRequest()
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe okBlock
      }

      "redirect to /personal-account/check-your-address when both residential and correspondence address status is 1 and residential address has been updated" in {
        when(mockEditAddressLockRepository.getAddressesLock(any())(any(), any()))
          .thenReturn(Future.successful(AddressesLock(true, false)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Some(Fixtures.fakeNino),
          Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          Some(
            buildFakePersonDetails.copy(
              address = Some(buildFakeAddress.copy(isRls = true)),
              correspondenceAddress = Some(buildFakeCorrespondenceAddress.copy(isRls = true))
            )
          ),
          None,
          Set(),
          None,
          None,
          None,
          None,
          FakeRequest()
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe interrupt
      }

      "redirect to /personal-account/check-your-address when both residential and correspondence address status is 1 and correspondence address has been updated" in {
        when(mockEditAddressLockRepository.getAddressesLock(any())(any(), any()))
          .thenReturn(Future.successful(AddressesLock(false, true)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Some(Fixtures.fakeNino),
          Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          Some(
            buildFakePersonDetails.copy(
              address = Some(buildFakeAddress.copy(isRls = true)),
              correspondenceAddress = Some(buildFakeCorrespondenceAddress.copy(isRls = true))
            )
          ),
          None,
          Set(),
          None,
          None,
          None,
          None,
          FakeRequest()
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe interrupt
      }

    }

    "the enforce rlsInterruptToggle toggle is set to false" must {
      "return the result of a passed in block" in {
        when(mockConfigDecorator.rlsInterruptToggle).thenReturn(false)
        when(mockEditAddressLockRepository.getAddressesLock(any())(any(), any()))
          .thenReturn(Future.successful(AddressesLock(false, false)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Some(Fixtures.fakeNino),
          Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          Some(
            buildFakePersonDetails.copy(
              address = Some(buildFakeAddress.copy(isRls = true)),
              correspondenceAddress = Some(buildFakeCorrespondenceAddress.copy(isRls = true))
            )
          ),
          None,
          Set(),
          None,
          None,
          None,
          None,
          FakeRequest()
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock))
        result.futureValue mustBe okBlock
      }
    }
  }
}
