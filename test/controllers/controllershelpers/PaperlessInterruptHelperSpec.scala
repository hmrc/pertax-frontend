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
import connectors.PreferencesFrontendConnector
import controllers.auth.requests.UserRequest
import models.admin.EnforcePaperlessPreferenceToggle
import models.{NonFilerSelfAssessmentUser, UserAnswers}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.Application
import play.api.inject.bind
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc.{AnyContent, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import testUtils.{BaseSpec, Fixtures}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import scala.concurrent.Future

class PaperlessInterruptHelperSpec extends BaseSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[PreferencesFrontendConnector].toInstance(mockPreferencesFrontendConnector)
    )
    .build()

  lazy val paperlessInterruptHelper: PaperlessInterruptHelper             = app.injector.instanceOf[PaperlessInterruptHelper]
  lazy val mockPreferencesFrontendConnector: PreferencesFrontendConnector = mock[PreferencesFrontendConnector]

  val okBlock: Result = Ok("Block")

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

  implicit lazy val mockConfigDecorator: ConfigDecorator = mock[ConfigDecorator]

  "enforcePaperlessPreference" when {
    "the enforce paperless preference toggle is set to true" must {
      "Redirect to paperless interrupt page for a user who has no enrolments" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(EnforcePaperlessPreferenceToggle)))
          .thenReturn(Future.successful(FeatureFlag(EnforcePaperlessPreferenceToggle, isEnabled = true)))

        when(mockPreferencesFrontendConnector.getPaperlessPreference()(any())) thenReturn
          EitherT[Future, UpstreamErrorResponse, HttpResponse](
            Future.successful(
              Right(HttpResponse(PRECONDITION_FAILED, Json.obj("redirectUserTo" -> "/activate-paperless").toString))
            )
          )

        val r = paperlessInterruptHelper.enforcePaperlessPreference(Future(Ok))
        status(r) mustBe SEE_OTHER
        redirectLocation(r) mustBe Some("/activate-paperless")
      }

      "return the result of a passed in block" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(EnforcePaperlessPreferenceToggle)))
          .thenReturn(Future.successful(FeatureFlag(EnforcePaperlessPreferenceToggle, isEnabled = false)))

        when(mockPreferencesFrontendConnector.getPaperlessPreference()(any())) thenReturn
          EitherT[Future, UpstreamErrorResponse, HttpResponse](Future.successful(Right(HttpResponse(OK, ""))))

        val result = paperlessInterruptHelper.enforcePaperlessPreference(Future(okBlock))
        result.futureValue mustBe okBlock
      }

      "Return the result of the block when getPaperlessPreference does not return Right" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(EnforcePaperlessPreferenceToggle)))
          .thenReturn(Future.successful(FeatureFlag(EnforcePaperlessPreferenceToggle, isEnabled = true)))

        when(mockPreferencesFrontendConnector.getPaperlessPreference()(any())) thenReturn
          EitherT[Future, UpstreamErrorResponse, HttpResponse](
            Future.successful(Left(UpstreamErrorResponse("", INTERNAL_SERVER_ERROR)))
          )

        val result = paperlessInterruptHelper.enforcePaperlessPreference(Future(okBlock)).futureValue
        result mustBe okBlock
      }
    }

    "the enforce paperless preference toggle is set to false" must {
      "return the result of a passed in block" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(EnforcePaperlessPreferenceToggle)))
          .thenReturn(Future.successful(FeatureFlag(EnforcePaperlessPreferenceToggle, isEnabled = false)))

        val result = paperlessInterruptHelper.enforcePaperlessPreference(Future(okBlock))
        result.futureValue mustBe okBlock
      }
    }
  }
}
