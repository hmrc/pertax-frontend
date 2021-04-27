/*
 * Copyright 2021 HM Revenue & Customs
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

import java.util.UUID

import connectors.SelfAssessmentConnector
import models.{NotEnrolledSelfAssessmentUser, SaEnrolmentResponse, UserDetails}
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import util.BaseSpec
import util.UserRequestFixture.buildUserRequest

import scala.concurrent.{ExecutionContext, Future}

class SelfAssessmentServiceSpec extends BaseSpec with MockitoSugar {

  val mockSelfAssessmentConnector: SelfAssessmentConnector = mock[SelfAssessmentConnector]

  implicit lazy val ec: ExecutionContext = injected[ExecutionContext]

  def sut: SelfAssessmentService = new SelfAssessmentService(mockSelfAssessmentConnector, config)

  val utr: SaUtr = new SaUtrGenerator().nextSaUtr

  val providerId: String = UUID.randomUUID().toString

  implicit val userRequest =
    buildUserRequest(
      request = FakeRequest(),
      saUser = NotEnrolledSelfAssessmentUser(utr),
      credentials = Credentials(providerId, UserDetails.GovernmentGatewayAuthProvider),
    )

  "SelfAssessmentService" when {

    "getSaEnrolmentUrl is called" should {

      "return a redirect Url" when {

        "the connector returns a successful response" in {

          val redirectUrl = "/foo"

          when(mockSelfAssessmentConnector.enrolForSelfAssessment(any())(any(), any())) thenReturn Future.successful(
            Some(SaEnrolmentResponse(redirectUrl)))

          await(sut.getSaEnrolmentUrl) shouldBe Some(redirectUrl)
        }
      }

      "return None" when {

        "the connector returns a failure response" in {

          when(mockSelfAssessmentConnector.enrolForSelfAssessment(any())(any(), any())) thenReturn Future.successful(
            None)

          await(sut.getSaEnrolmentUrl) shouldBe None
        }
      }
    }
  }
}
