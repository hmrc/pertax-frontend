/*
 * Copyright 2019 HM Revenue & Customs
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

package controllers.helpers

import controllers.auth.requests.UserRequest
import models.{ActivatePaperlessNotAllowedResponse, ActivatePaperlessRequiresUserActionResponse, NonFilerSelfAssessmentUser, UserName}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.Results._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services._
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name}
import util.{BaseSpec, Fixtures}

import scala.concurrent.Future

class PaperlessInterruptHelperSpec extends BaseSpec {

  "Calling PaperlessInterruptHelper.enforcePaperlessPreference" should {

    implicit val userRequest = UserRequest(
      Some(Fixtures.fakeNino),
      Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
      None,
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

    "Redirect to paperless interupt page for a user who has no enrolments" in {
      lazy val paperlessInterruptHelper = new PaperlessInterruptHelper {
        override val preferencesFrontendService: PreferencesFrontendService =
          MockitoSugar.mock[PreferencesFrontendService]
        when(preferencesFrontendService.getPaperlessPreference()(any())) thenReturn {
          Future.successful(ActivatePaperlessRequiresUserActionResponse("/activate-paperless"))
        }
      }

      val r = paperlessInterruptHelper.enforcePaperlessPreference(Ok)
      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/activate-paperless")
    }

    "Return the result of the block when getPaperlessPreference does not return ActivatePaperlessRequiresUserActionResponse" in {
      lazy val paperlessInterruptHelper = new PaperlessInterruptHelper {
        override val preferencesFrontendService: PreferencesFrontendService =
          MockitoSugar.mock[PreferencesFrontendService]
        when(preferencesFrontendService.getPaperlessPreference()(any())) thenReturn {
          Future.successful(ActivatePaperlessNotAllowedResponse)
        }
      }

      val r = paperlessInterruptHelper.enforcePaperlessPreference(Ok)
      status(r) shouldBe OK
    }
  }
}
