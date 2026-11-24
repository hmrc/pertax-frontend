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
import connectors.PreferencesFrontendConnector
import controllers.auth.requests.UserRequest
import models.*
import models.admin.{AlertBannerPaperlessStatusToggle, HomePageChangesBannerToggle, PeakDemandBannerToggle, ShowPlannedOutageBannerToggle, VoluntaryContributionsAlertToggle}
import play.api.i18n.Messages
import play.api.mvc.AnyContent
import play.twirl.api.Html
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import views.html.components.alertBanner.paperlessStatus.{bouncedEmail, postalExperiment, unverifiedEmail}
import views.html.components.alertBanner.{addressFixBanner, newHomePageChangesBanner, peakDemandBanner, shutteringBanner, voluntaryContributionsAlertView}

import scala.concurrent.{ExecutionContext, Future}

class AlertBannerHelper @Inject() (
  preferencesFrontendConnector: PreferencesFrontendConnector,
  featureFlagService: FeatureFlagService,
  bouncedEmailView: bouncedEmail,
  postalExperimentView: postalExperiment,
  unverifiedEmailView: unverifiedEmail,
  voluntaryContributionsAlertView: voluntaryContributionsAlertView,
  peakDemandBannerView: peakDemandBanner,
  addressFixBannerView: addressFixBanner,
  shutteringBannerView: shutteringBanner,
  newHomePageChangesBannerView: newHomePageChangesBanner
) {

  def getContent(personDetails: Option[PersonDetails])(implicit
    request: UserRequest[AnyContent],
    ec: ExecutionContext,
    messages: Messages
  ): Future[Option[Html]] =
    getContent(personDetails, getPaperlessStatusBannerContent)

  def getContent(
    personDetails: Option[PersonDetails],
    isRedesign: Boolean,
    includePaperlessUrl: Boolean,
    returnLinkText: String
  )(implicit
    request: UserRequest[AnyContent],
    ec: ExecutionContext,
    messages: Messages
  ): Future[AlertBannerResult] =
    getPaperlessStatus(returnLinkText).flatMap { paperlessStatus =>
      getPaperlessStatusContent(paperlessStatus, isRedesign, includePaperlessUrl).flatMap { paperlessContent =>
        getContent(personDetails, Future.successful(paperlessContent.bannerContent))
          .map(AlertBannerResult(_, paperlessContent.postalExperimentContent))
      }
    }

  private def getContent(
    personDetails: Option[PersonDetails],
    paperlessStatusBannerContent: Future[Option[Html]]
  )(implicit ec: ExecutionContext, messages: Messages): Future[Option[Html]] = {
    val contentFutures = List(
      getShutteringBannerContent,
      getPeakDemandBannerContent,
      getAddressFixBannerContent(personDetails),
      paperlessStatusBannerContent,
      getHomePageChangesBannerContent
    )
    Future.sequence(contentFutures).map(_.collectFirst { case Some(html) => html })
  }

  private def getPaperlessStatus(returnLinkText: String)(implicit
    request: UserRequest[AnyContent],
    ec: ExecutionContext
  ): Future[Option[PaperlessMessagesStatus]] =
    preferencesFrontendConnector
      .getPaperlessStatus(request.uri, returnLinkText)
      .value
      .map(_.toOption)
      .recover { case _ => None }

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

  private def getPaperlessStatusContent(
    paperlessStatus: Option[PaperlessMessagesStatus],
    isRedesign: Boolean,
    includePaperlessUrl: Boolean
  )(implicit
    ec: ExecutionContext,
    messages: Messages
  ): Future[PaperlessStatusContent] =
    featureFlagService.get(AlertBannerPaperlessStatusToggle).map { case toggle =>
      paperlessStatus match {
        case Some(PaperlessStatusBounced(link)) if toggle.isEnabled    =>
          PaperlessStatusContent(bannerContent = Some(bouncedEmailView(link)))
        case Some(PaperlessStatusUnverified(link)) if toggle.isEnabled =>
          PaperlessStatusContent(bannerContent = Some(unverifiedEmailView(link)))
        case Some(PaperlessStatusOptOut(link)) if isRedesign           =>
          PaperlessStatusContent(
            postalExperimentContent = Some(postalExperimentView(Option.when(includePaperlessUrl)(link)))
          )
        case _                                                         => PaperlessStatusContent()
      }
    }

  private def getPeakDemandBannerContent(implicit ec: ExecutionContext, messages: Messages): Future[Option[Html]] =
    featureFlagService.get(PeakDemandBannerToggle).map {
      case toggle if toggle.isEnabled => Some(peakDemandBannerView())
      case _                          => None
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

  def getHomePageChangesBannerContent(implicit ec: ExecutionContext, messages: Messages): Future[Option[Html]] =
    featureFlagService.get(HomePageChangesBannerToggle).map {
      case toggle if toggle.isEnabled => Some(newHomePageChangesBannerView())
      case _                          => None
    }
}

final case class AlertBannerResult(content: Option[Html], postalExperimentContent: Option[Html])

private final case class PaperlessStatusContent(
  bannerContent: Option[Html] = None,
  postalExperimentContent: Option[Html] = None
)
