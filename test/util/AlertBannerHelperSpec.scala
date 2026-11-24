/*
 * Copyright 2025 HM Revenue & Customs
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
import models.admin.{AlertBannerPaperlessStatusToggle, HomePageChangesBannerToggle, PeakDemandBannerToggle, ShowPlannedOutageBannerToggle, VoluntaryContributionsAlertToggle}
import models.{PaperlessMessagesStatus, PaperlessStatusBounced, PaperlessStatusNewCustomer, PaperlessStatusNoEmail, PaperlessStatusOptIn, PaperlessStatusOptOut, PaperlessStatusReopt, PaperlessStatusReoptModified, PaperlessStatusUnverified}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.concurrent.IntegrationPatience
import play.api.Application
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.inject.bind
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.Fixtures.{buildFakeAddress, buildPersonDetailsWithPersonalAndCorrespondenceAddress}
import testUtils.{BaseSpec, UserRequestFixture}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import views.html.components.alertBanner.paperlessStatus.*
import views.html.components.alertBanner.{addressFixBanner, newHomePageChangesBanner, peakDemandBanner, shutteringBanner, voluntaryContributionsAlertView}

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

    when(mockFeatureFlagService.get(PeakDemandBannerToggle))
      .thenReturn(Future.successful(FeatureFlag(PeakDemandBannerToggle, isEnabled = false)))

    when(mockFeatureFlagService.get(ShowPlannedOutageBannerToggle))
      .thenReturn(Future.successful(FeatureFlag(ShowPlannedOutageBannerToggle, isEnabled = false)))

    when(mockFeatureFlagService.get(HomePageChangesBannerToggle))
      .thenReturn(Future.successful(FeatureFlag(HomePageChangesBannerToggle, isEnabled = false)))
  }

  override lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[PreferencesFrontendConnector].toInstance(mockPreferencesFrontendConnector)
    )
    .build()

  override lazy val messagesApi: MessagesApi                      = app.injector.instanceOf[MessagesApi]
  implicit lazy val messages: Messages                            = MessagesImpl(Lang("en"), messagesApi)
  lazy val alertBannerHelper: AlertBannerHelper                   = app.injector.instanceOf[AlertBannerHelper]
  lazy val bouncedEmailView: bouncedEmail                         = app.injector.instanceOf[bouncedEmail]
  lazy val postalExperimentView: postalExperiment                 = app.injector.instanceOf[postalExperiment]
  lazy val unverifiedEmailView: unverifiedEmail                   = app.injector.instanceOf[unverifiedEmail]
  lazy val peakDemandBannerView: peakDemandBanner                 = app.injector.instanceOf[peakDemandBanner]
  lazy val addressFixBannerView: addressFixBanner                 = app.injector.instanceOf[addressFixBanner]
  lazy val shutteringBannerView: shutteringBanner                 = app.injector.instanceOf[shutteringBanner]
  lazy val newHomePageChangesBannerView: newHomePageChangesBanner = app.injector.instanceOf[newHomePageChangesBanner]

  lazy val voluntaryContributionsAlertView: voluntaryContributionsAlertView =
    app.injector.instanceOf[voluntaryContributionsAlertView]

  "AlertBannerHelper.getContent" must {
    "return shuttering banner content" in {
      when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](PaperlessStatusOptIn(): PaperlessMessagesStatus)
      )

      when(mockFeatureFlagService.get(ShowPlannedOutageBannerToggle))
        .thenReturn(Future.successful(FeatureFlag(ShowPlannedOutageBannerToggle, isEnabled = true)))

      val result = alertBannerHelper.getContent(None, true, false).futureValue

      result mustBe Some(shutteringBannerView())
    }

    "return peak demand banner content" in {
      when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](PaperlessStatusOptIn(): PaperlessMessagesStatus)
      )

      when(mockFeatureFlagService.get(PeakDemandBannerToggle))
        .thenReturn(Future.successful(FeatureFlag(PeakDemandBannerToggle, isEnabled = true)))

      val result = alertBannerHelper.getContent(None, true, false).futureValue

      result mustBe Some(peakDemandBannerView())
    }

    "return address error content" in {
      when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](PaperlessStatusOptIn(): PaperlessMessagesStatus)
      )

      val personDetails = Some(
        buildPersonDetailsWithPersonalAndCorrespondenceAddress.copy(address =
          Some(buildFakeAddress.copy(country = Some("ABROAD - NOT KNOWN")))
        )
      )

      val result = alertBannerHelper.getContent(personDetails, true, false).futureValue

      result mustBe Some(addressFixBannerView())
    }

    "return bounce email content " in {
      val link = "/link"
      when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](PaperlessStatusBounced(link): PaperlessMessagesStatus)
      )

      val result = alertBannerHelper.getContent(None, true, false).futureValue

      result mustBe Some(bouncedEmailView(link))
    }

    "return verify email content " in {
      val link = "/link"
      when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](PaperlessStatusUnverified(link): PaperlessMessagesStatus)
      )

      val result = alertBannerHelper.getContent(None, true, false).futureValue

      result mustBe Some(unverifiedEmailView(link))
    }

    "return address new home page changes banner content" in {
      when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](PaperlessStatusOptIn(): PaperlessMessagesStatus)
      )

      when(mockFeatureFlagService.get(HomePageChangesBannerToggle))
        .thenReturn(Future.successful(FeatureFlag(HomePageChangesBannerToggle, isEnabled = true)))

      val result = alertBannerHelper.getContent(None, true, false).futureValue

      result mustBe Some(newHomePageChangesBannerView())
    }

    "return None " when
      List(
        PaperlessStatusNewCustomer(),
        PaperlessStatusReopt(),
        PaperlessStatusReoptModified(),
//        PaperlessStatusOptOut(),
        PaperlessStatusOptIn(),
        PaperlessStatusNoEmail()
      ).foreach { paperlessStatusResponse =>
        s"paperless status is $paperlessStatusResponse" in {
          when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any())).thenReturn(
            EitherT.rightT[Future, UpstreamErrorResponse](paperlessStatusResponse: PaperlessMessagesStatus)
          )

          val result = alertBannerHelper.getContent(None, true, false).futureValue

          result mustBe None
        }
      }
  }

  "AlertBannerHelper.getContent for the redesign" must {
    "return the postal experiment content from one preferences call" in {
      val paperlessStatus = PaperlessStatusOptOut("paperless-url")
      when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](paperlessStatus: PaperlessMessagesStatus)
      )

      val result = alertBannerHelper
        .getContent(None, isRedesign = true, true)
        .futureValue

      result mustBe Some(postalExperimentView(Some("paperless-url")))
      verify(mockPreferencesFrontendConnector, times(1))
        .getPaperlessStatus("/", "Back to your tasks")
    }

    "not return postal experiment content for the legacy page" in {
      when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](PaperlessStatusOptOut("paperless-url"): PaperlessMessagesStatus)
      )

      val result = alertBannerHelper
        .getContent(None, isRedesign = false, false)
        .futureValue

      result mustBe None
    }

    "reuse a bounced status for the banner content" in {
      val paperlessStatus = PaperlessStatusBounced("/link")
      when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](paperlessStatus: PaperlessMessagesStatus)
      )

      val result = alertBannerHelper
        .getContent(None, isRedesign = true, false)
        .futureValue

      result mustBe Some(bouncedEmailView("/link"))
      verify(mockPreferencesFrontendConnector, times(1))
        .getPaperlessStatus("/", "")
    }

    "fail safely when preferences-frontend returns an error" in {
      when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any())).thenReturn(
        EitherT.leftT[Future, PaperlessMessagesStatus](UpstreamErrorResponse("failed", 500))
      )

      val result = alertBannerHelper
        .getContent(None, isRedesign = true, false)
        .futureValue

      result mustBe None
    }
  }

  "AlertBannerHelper.getVoluntaryContributionsAlertBannerContent" must {
    "return the voluntary contributions alert banner when the feature flag is enabled" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(VoluntaryContributionsAlertToggle)))
        .thenReturn(Future.successful(FeatureFlag(VoluntaryContributionsAlertToggle, isEnabled = true)))

      val result = alertBannerHelper.getVoluntaryContributionsAlertBannerContent.futureValue

      result mustBe Some(voluntaryContributionsAlertView())
    }

    "return None when the voluntary contributions alert feature flag is disabled" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(VoluntaryContributionsAlertToggle)))
        .thenReturn(Future.successful(FeatureFlag(VoluntaryContributionsAlertToggle, isEnabled = false)))

      val result = alertBannerHelper.getVoluntaryContributionsAlertBannerContent.futureValue

      result mustBe None
    }
  }
}
