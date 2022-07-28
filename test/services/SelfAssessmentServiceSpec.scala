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

package services

import connectors.SelfAssessmentConnector
import models.{NotEnrolledSelfAssessmentUser, SaEnrolmentResponse, UserDetails}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.test.FakeRequest
import testUtils.BaseSpec
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}

import java.util.UUID
import scala.concurrent.Future

class SelfAssessmentServiceSpec extends BaseSpec {

  val mockSelfAssessmentConnector: SelfAssessmentConnector = mock[SelfAssessmentConnector]

  def sut: SelfAssessmentService = new SelfAssessmentService(mockSelfAssessmentConnector, config)

  val utr: SaUtr = new SaUtrGenerator().nextSaUtr

  val providerId: String = UUID.randomUUID().toString

  implicit val userRequest =
    buildUserRequest(
      request = FakeRequest(),
      saUser = NotEnrolledSelfAssessmentUser(utr),
      credentials = Credentials(providerId, UserDetails.GovernmentGatewayAuthProvider)
    )

  "SelfAssessmentService" when {

    "getSaEnrolmentUrl is called" must {

      "return a redirect Url" when {

        "the connector returns a successful response" in {

          val redirectUrl = "/foo"

          when(mockSelfAssessmentConnector.enrolForSelfAssessment(any())(any())) thenReturn Future.successful(
            Some(SaEnrolmentResponse(redirectUrl))
          )

          sut.getSaEnrolmentUrl.futureValue mustBe Some(redirectUrl)
        }
      }

      "return None" when {

        "the connector returns a failure response" in {

          when(mockSelfAssessmentConnector.enrolForSelfAssessment(any())(any())) thenReturn Future.successful(
            None
          )

          sut.getSaEnrolmentUrl.futureValue mustBe None
        }
      }
    }
  }
}
