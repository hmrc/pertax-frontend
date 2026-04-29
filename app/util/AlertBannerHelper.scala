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

import cats.implicits.catsStdInstancesForFuture
import com.google.inject.Inject
import connectors.{FandFConnector, PreferencesFrontendConnector}
import controllers.auth.requests.UserRequest
import models.*
import models.admin.{AlertBannerPaperlessStatusToggle, HomePageChangesBannerToggle, PeakDemandBannerToggle, ShowPlannedOutageBannerToggle, VoluntaryContributionsAlertToggle}
import play.api.i18n.Messages
import play.api.mvc.AnyContent
import play.twirl.api.Html
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import views.html.components.alertBanner.paperlessStatus.{bouncedEmail, unverifiedEmail}
import views.html.components.alertBanner.{addressFixBanner, fandfBanner, newHomePageChangesBanner, oldHomePageChangesBanner, peakDemandBanner, shutteringBanner, voluntaryContributionsAlertView}

import scala.concurrent.{ExecutionContext, Future}

class AlertBannerHelper @Inject() (
  preferencesFrontendConnector: PreferencesFrontendConnector,
  featureFlagService: FeatureFlagService,
  fandFConnector: FandFConnector,
  bouncedEmailView: bouncedEmail,
  unverifiedEmailView: unverifiedEmail,
  voluntaryContributionsAlertView: voluntaryContributionsAlertView,
  peakDemandBannerView: peakDemandBanner,
  fandfBannerView: fandfBanner,
  addressFixBannerView: addressFixBanner,
  shutteringBannerView: shutteringBanner,
  newHomePageChangesBannerView: newHomePageChangesBanner,
  oldHomePageChangesBannerView: oldHomePageChangesBanner
) {

  def getContent(personDetails: Option[PersonDetails], newDesign: Boolean)(implicit
    request: UserRequest[AnyContent],
    ec: ExecutionContext,
    messages: Messages
  ): Future[Option[Html]] = {
    val contentFutures = List(
      getShutteringBannerContent,
      getPeakDemandBannerContent,
      getAddressFixBannerContent(personDetails),
      getFandfBannerContent,
      getPaperlessStatusBannerContent,
      getHomePageChangesBannerContent(newDesign)
    )
    Future.sequence(contentFutures).map(_.collectFirst { case Some(html) => html })
  }

  private def getPaperlessStatusBannerContent(implicit
    request: UserRequest[AnyContent],
    ec: ExecutionContext,
    messages: Messages
  ): Future[Option[Html]] =
    featureFlagService.get(AlertBannerPaperlessStatusToggle).flatMap {
      case toggle if toggle.isEnabled =>
        preferencesFrontendConnector
          .getPaperlessStatus(request.uri, "")
          .fold(
            _ => None,
            {
              case PaperlessStatusBounced(link)    => Some(bouncedEmailView(link))
              case PaperlessStatusUnverified(link) => Some(unverifiedEmailView(link))
              case _                               => None
            }
          )
      case _                          =>
        Future.successful(None)
    }

  private def getPeakDemandBannerContent(implicit ec: ExecutionContext, messages: Messages): Future[Option[Html]] =
    featureFlagService.get(PeakDemandBannerToggle).map {
      case toggle if toggle.isEnabled => Some(peakDemandBannerView())
      case _                          => None
    }

  private def getFandfBannerContent(implicit
    request: UserRequest[AnyContent],
    ec: ExecutionContext,
    messages: Messages
  ): Future[Option[Html]] =
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    fandFConnector.showFandfBanner(request.authNino).map {
      case true => Some(fandfBannerView())
      case _    => None
    }

  private def getShutteringBannerContent(implicit ec: ExecutionContext, messages: Messages): Future[Option[Html]] =
    featureFlagService.get(ShowPlannedOutageBannerToggle).map {
      case toggle if toggle.isEnabled => Some(shutteringBannerView())
      case _                          => None
    }

  private def getAddressFixBannerContent(
    personDetails: Option[PersonDetails]
  )(implicit messages: Messages): Future[Option[Html]] =
    personDetails match {
      case Some(details) if details.notKnownAddress => Future.successful(Some(addressFixBannerView()))
      case _                                        => Future.successful(None)
    }

  def getVoluntaryContributionsAlertBannerContent(implicit
    ec: ExecutionContext,
    messages: Messages
  ): Future[Option[Html]] =
    featureFlagService.get(VoluntaryContributionsAlertToggle).map { toggle =>
      if (toggle.isEnabled) {
        Some(voluntaryContributionsAlertView())
      } else {
        None
      }
    }

  def getHomePageChangesBannerContent(
    newDesign: Boolean
  )(implicit ec: ExecutionContext, messages: Messages): Future[Option[Html]] =
    featureFlagService.get(HomePageChangesBannerToggle).map {
      case toggle if toggle.isEnabled =>
        if (newDesign) Some(newHomePageChangesBannerView()) else Some(oldHomePageChangesBannerView())
      case _                          => None
    }
}
