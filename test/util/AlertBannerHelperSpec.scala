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
import models.{PaperlessMessagesStatus, PaperlessStatusBounced, PaperlessStatusNewCustomer, PaperlessStatusNoEmail, PaperlessStatusOptIn, PaperlessStatusOptOut, PaperlessStatusReopt, PaperlessStatusReoptModified, PaperlessStatusUnverified}
import models.admin.AlertBannerPaperlessStatusToggle
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.scalatest.concurrent.IntegrationPatience
import play.api.Application
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.inject.bind
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.{BaseSpec, UserRequestFixture}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import views.html.components.alertBanner.paperlessStatus._

import scala.concurrent.Future

class AlertBannerHelperSpec extends BaseSpec with IntegrationPatience {

  lazy val mockPreferencesFrontendConnector: PreferencesFrontendConnector = mock[PreferencesFrontendConnector]
  implicit val userRequest: UserRequest[AnyContentAsEmpty.type]           =
    UserRequestFixture.buildUserRequest(request = FakeRequest().withSession("sessionId" -> "FAKE_SESSION_ID"))

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPreferencesFrontendConnector)

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(AlertBannerPaperlessStatusToggle)))
      .thenReturn(Future.successful(FeatureFlag(AlertBannerPaperlessStatusToggle, isEnabled = true)))
  }

  override lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[PreferencesFrontendConnector].toInstance(mockPreferencesFrontendConnector)
    )
    .build()

  override lazy val messagesApi: MessagesApi    = app.injector.instanceOf[MessagesApi]
  implicit lazy val messages: Messages          = MessagesImpl(Lang("en"), messagesApi)
  lazy val alertBannerHelper: AlertBannerHelper = app.injector.instanceOf[AlertBannerHelper]
  lazy val bouncedEmailView: bouncedEmail       = app.injector.instanceOf[bouncedEmail]
  lazy val unverifiedEmailView: unverifiedEmail = app.injector.instanceOf[unverifiedEmail]

  "AlertBannerHelper.getContent" must {
    "return bounce email content " in {
      val link = "/link"
      when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](PaperlessStatusBounced(link): PaperlessMessagesStatus)
      )

      val result = alertBannerHelper.getContent.futureValue

      result mustBe List(bouncedEmailView(link))
    }

    "return verify email content " in {
      val link = "/link"
      when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](PaperlessStatusUnverified(link): PaperlessMessagesStatus)
      )

      val result = alertBannerHelper.getContent.futureValue

      result mustBe List(unverifiedEmailView(link))
    }
  }

  "return None " when {
    List(
      PaperlessStatusNewCustomer(),
      PaperlessStatusReopt(),
      PaperlessStatusReoptModified(),
      PaperlessStatusOptOut(),
      PaperlessStatusOptIn(),
      PaperlessStatusNoEmail()
    ).foreach { paperlessStatusResponse =>
      s"paperless status is $paperlessStatusResponse" in {
        when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](paperlessStatusResponse: PaperlessMessagesStatus)
        )

        val result = alertBannerHelper.getContent.futureValue

        result mustBe List.empty
      }
    }

    "paperless status returns a server error" in {
      when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any())).thenReturn(
        EitherT.leftT[Future, PaperlessMessagesStatus](UpstreamErrorResponse("Server error", 500))
      )

      val result = alertBannerHelper.getContent.futureValue

      result mustBe List.empty
    }
  }
}
