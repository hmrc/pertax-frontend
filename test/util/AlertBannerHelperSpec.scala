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

package util

import cats.data.EitherT
import connectors.PreferencesFrontendConnector
import controllers.auth.requests.UserRequest
import models._
import models.admin.{AlertBannerToggle, FeatureFlag}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import play.api.Application
import play.api.inject.bind
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.{BaseSpec, UserRequestFixture}
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.concurrent.Future

class AlertBannerHelperSpec extends BaseSpec {

  val helper: AlertBannerHelper                                           = inject[AlertBannerHelper]
  lazy val mockPreferencesFrontendConnector: PreferencesFrontendConnector = mock[PreferencesFrontendConnector]
  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
    UserRequestFixture.buildUserRequest(request = FakeRequest().withSession("sessionId" -> "FAKE_SESSION_ID"))

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPreferencesFrontendConnector, mockFeatureFlagService)
  }

  override lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[PreferencesFrontendConnector].toInstance(mockPreferencesFrontendConnector)
    )
    .build()

  "AlertBannerHelper" when {
    "alertBannerStatus is called" must {
      "return Some[PaperlessStatusBounced] when bounced status retrieved from PreferencesFrontendConnector and toggle is set to true" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(AlertBannerToggle))) thenReturn Future.successful(
          FeatureFlag(AlertBannerToggle, isEnabled = true)
        )
        when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, PaperlessMessages](
              Future.successful(Right(PaperlessStatusBounced()))
            )
          )
        helper.alertBannerStatus().futureValue mustBe a[Option[PaperlessStatusBounced]]
      }
      "return Some[PaperlessStatusUnverified] when unverified status retrieved from PreferencesFrontendConnector and toggle is set to true" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(AlertBannerToggle))) thenReturn Future.successful(
          FeatureFlag(AlertBannerToggle, isEnabled = true)
        )
        when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, PaperlessMessages](
              Future.successful(Right(PaperlessStatusUnverified()))
            )
          )
        helper.alertBannerStatus().futureValue mustBe a[Option[PaperlessStatusUnverified]]
      }
      "return None when any other status is retrieved from PreferencesFrontendConnector and toggle is set to true" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(AlertBannerToggle))) thenReturn Future.successful(
          FeatureFlag(AlertBannerToggle, isEnabled = true)
        )
        when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, PaperlessMessages](
              Future.successful(Right(PaperlessStatusReopt()))
            )
          )
        helper.alertBannerStatus().futureValue mustBe None
      }
      "return None if toggle is set to false" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(AlertBannerToggle))) thenReturn Future.successful(
          FeatureFlag(AlertBannerToggle, isEnabled = false)
        )
        helper.alertBannerStatus().futureValue mustBe None
      }
    }
    "alertBannerUrl is called" must {
      "return an option containing the bounced email link when bounced email is passed and the toggle is set to true" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(AlertBannerToggle))) thenReturn Future.successful(
          FeatureFlag(AlertBannerToggle, isEnabled = true)
        )
        helper
          .alertBannerUrl(Some(PaperlessStatusBounced()))
          .futureValue
          .map(_ must include("/paperless/email-bounce?returnUrl="))
      }
      "return an option containing the verify email link when PaperlessStatusUnverified is passed and the toggle is set to true" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(AlertBannerToggle))) thenReturn Future.successful(
          FeatureFlag(AlertBannerToggle, isEnabled = true)
        )
        helper
          .alertBannerUrl(Some(PaperlessStatusUnverified()))
          .futureValue
          .map(_ must include("/paperless/email-re-verify?returnUrl="))
      }
      "return a None when any other PaperlessMessages is passed and the toggle is set to true" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(AlertBannerToggle))) thenReturn Future.successful(
          FeatureFlag(AlertBannerToggle, isEnabled = true)
        )
        helper.alertBannerUrl(Some(PaperlessStatusReopt())).futureValue mustBe None
      }
      "return a None if the toggle is set to false" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(AlertBannerToggle))) thenReturn Future.successful(
          FeatureFlag(AlertBannerToggle, isEnabled = false)
        )
        helper.alertBannerUrl(Some(PaperlessStatusUnverified())).futureValue mustBe None
      }
    }
  }
}
