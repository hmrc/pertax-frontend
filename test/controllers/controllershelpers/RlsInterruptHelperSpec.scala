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
import controllers.bindable.{InvalidAddresses, ValidAddressesBothInterrupt, ValidAddressesCorrespondenceInterrupt, ValidAddressesNoInterrupt, ValidAddressesResidentialInterrupt}
import models.{ActivatePaperlessNotAllowedResponse, ActivatePaperlessRequiresUserActionResponse, NonFilerSelfAssessmentUser, UserName}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.mvc.Results._
import play.api.mvc.{AnyContent, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{CitizenDetailsService, _}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name}
import util.{BaseSpec, Fixtures}

import scala.concurrent.Future

class RlsInterruptHelperSpec extends BaseSpec {

  val rlsInterruptHelper = new RlsInterruptHelper {
    override val citizenDetailsService: CitizenDetailsService = mock[CitizenDetailsService]
  }

  val okBlock: Result = Ok("Block")

  implicit val userRequest: UserRequest[AnyContent] = UserRequest(
    Some(Fixtures.fakeNino),
    Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
    NonFilerSelfAssessmentUser,
    Credentials("", "GovernmentGateway"),
    ConfidenceLevel.L200,
    None,
    None,
    None,
    None,
    None,
    None,
    FakeRequest()
  )

  implicit val mockConfigDecorator: ConfigDecorator = mock[ConfigDecorator]

  "enforceByRlsStatus" when {
    "the enforce getAddressStatusFromCID toggle is set to true" must {
      when(mockConfigDecorator.getAddressStatusFromCID).thenReturn(true)

      "return the result of the block when getAddressStatusFromPersonalDetails returns ValidAddressesNoInterrupt" in {
        when(rlsInterruptHelper.citizenDetailsService.getAddressStatusFromPersonalDetails(any(), any()))
          .thenReturn(Future.successful(ValidAddressesNoInterrupt))

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe okBlock
      }

      "return the result of the block when getAddressStatusFromPersonalDetails returns ValidAddressesResidentialInterrupt" in {
        when(rlsInterruptHelper.citizenDetailsService.getAddressStatusFromPersonalDetails(any(), any()))
          .thenReturn(Future.successful(ValidAddressesResidentialInterrupt))

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe Redirect("redirectUrl")
      }

      "return the result of the block when getAddressStatusFromPersonalDetails returns ValidAddressesCorrespondenceInterrupt" in {
        when(rlsInterruptHelper.citizenDetailsService.getAddressStatusFromPersonalDetails(any(), any()))
          .thenReturn(Future.successful(ValidAddressesCorrespondenceInterrupt))

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe Redirect("redirectUrl")
      }

      "return the result of the block when getAddressStatusFromPersonalDetails returns ValidAddressesBothInterrupt" in {
        when(rlsInterruptHelper.citizenDetailsService.getAddressStatusFromPersonalDetails(any(), any()))
          .thenReturn(Future.successful(ValidAddressesBothInterrupt))

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe Redirect("redirectUrl")
      }

      "return the result of the block when getAddressStatusFromPersonalDetails returns InvalidAddresses" in {
        when(rlsInterruptHelper.citizenDetailsService.getAddressStatusFromPersonalDetails(any(), any()))
          .thenReturn(Future.successful(InvalidAddresses))

        whenReady(rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).failed) { e =>
          e mustBe a[Exception]
        }
      }
    }

    "the enforce getAddressStatusFromCID toggle is set to false" must {
      "return the result of a passed in block" in {
        when(mockConfigDecorator.getAddressStatusFromCID).thenReturn(false)

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock))
        result.futureValue mustBe okBlock
      }
    }
  }
}
