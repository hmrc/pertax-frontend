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

package controllers.controllershelpers

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models.{ActivatePaperlessNotAllowedResponse, ActivatePaperlessRequiresUserActionResponse, NonFilerSelfAssessmentUser, UserName}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Results._
import play.api.mvc.{AnyContent, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services._
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name}
import util.{BaseSpec, Fixtures}

import scala.concurrent.Future

class PaperlessInterruptHelperSpec extends BaseSpec with MockitoSugar with ScalaFutures {

  val paperlessInterruptHelper = new PaperlessInterruptHelper {
    override val preferencesFrontendService: PreferencesFrontendService = mock[PreferencesFrontendService]
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

  "enforcePaperlessPreference" when {
    "the enforce paperless preference toggle is set to true" must {
      "Redirect to paperless interupt page for a user who has no enrolments" in {
        when(paperlessInterruptHelper.preferencesFrontendService.getPaperlessPreference()(any())) thenReturn {
          Future.successful(ActivatePaperlessRequiresUserActionResponse("/activate-paperless"))
        }

        when(mockConfigDecorator.enforcePaperlessPreferenceEnabled).thenReturn(true)

        val r = paperlessInterruptHelper.enforcePaperlessPreference(Future(Ok))
        status(r) mustBe SEE_OTHER
        redirectLocation(r) mustBe Some("/activate-paperless")
      }

      "Return the result of the block when getPaperlessPreference does not return ActivatePaperlessRequiresUserActionResponse" in {
        when(paperlessInterruptHelper.preferencesFrontendService.getPaperlessPreference()(any())) thenReturn {
          Future.successful(ActivatePaperlessNotAllowedResponse)
        }

        val result = paperlessInterruptHelper.enforcePaperlessPreference(Future(okBlock))
        result.futureValue mustBe okBlock
      }
    }

    "the enforce paperless preference toggle is set to false" must {
      "return the result of a passed in block" in {
        when(mockConfigDecorator.enforcePaperlessPreferenceEnabled).thenReturn(false)

        val result = paperlessInterruptHelper.enforcePaperlessPreference(Future(okBlock))
        result.futureValue mustBe okBlock
      }
    }
  }
}
