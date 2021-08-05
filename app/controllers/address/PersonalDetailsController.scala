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

package controllers.address

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.controllershelpers.{AddressJourneyCachingHelper, PersonalDetailsCardGenerator}
import models.{AddressJourneyTTLModel, AddressPageVisitedDtoId, PersonDetails}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import play.twirl.api.Html
import repositories.EditAddressLockRepository
import services.NinoDisplayService
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.renderer.TemplateRenderer
import util.AuditServiceTools.buildPersonDetailsEvent
import viewmodels.{PersonalDetailsTableRowModel, PersonalDetailsViewModel}
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.{PersonalDetailsView, PersonalDetailsViewV2}
import views.html.personaldetails.partials.{AddressView, CorrespondenceAddressView}

import scala.concurrent.{ExecutionContext, Future}

class PersonalDetailsController @Inject() (
  val personalDetailsCardGenerator: PersonalDetailsCardGenerator,
  val personalDetailsViewModel: PersonalDetailsViewModel,
  val editAddressLockRepository: EditAddressLockRepository,
  ninoDisplayService: NinoDisplayService,
  authJourney: AuthJourney,
  cachingHelper: AddressJourneyCachingHelper,
  withActiveTabAction: WithActiveTabAction,
  auditConnector: AuditConnector,
  cc: MessagesControllerComponents,
  displayAddressInterstitialView: DisplayAddressInterstitialView,
  personalDetailsView: PersonalDetailsView,
  personalDetailsViewV2: PersonalDetailsViewV2,
  addressView: AddressView,
  correspondenceAddressView: CorrespondenceAddressView
)(implicit configDecorator: ConfigDecorator, templateRenderer: TemplateRenderer, ec: ExecutionContext)
    extends AddressController(authJourney, withActiveTabAction, cc, displayAddressInterstitialView) {

  def onPageLoad: Action[AnyContent] = authenticate.async { implicit request =>
    import models.dto.AddressPageVisitedDto

    for {
      addressModel <- request.nino
                        .map { nino =>
                          editAddressLockRepository.get(nino.withoutSuffix)
                        }
                        .getOrElse(Future.successful(List[AddressJourneyTTLModel]()))
      ninoToDisplay <- ninoDisplayService.getNino
      personalDetailsCards: Seq[Html] = personalDetailsCardGenerator
                                          .getPersonalDetailsCards(addressModel, ninoToDisplay)
      personDetails: Option[PersonDetails] = request.personDetails

      _ <- personDetails
             .map { details =>
               auditConnector.sendEvent(buildPersonDetailsEvent("personalDetailsPageLinkClicked", details))
             }
             .getOrElse(Future.successful(Unit))
      _ <- cachingHelper.addToCache(AddressPageVisitedDtoId, AddressPageVisitedDto(true))

    } yield Ok(personalDetailsView(personalDetailsCards))
  }

  def onPageLoadv2: Action[AnyContent] = authenticate.async { implicit request =>
    import models.dto.AddressPageVisitedDto

    for {
      addressModel <- request.nino
                       .map { nino =>
                         editAddressLockRepository.get(nino.withoutSuffix)
                       }
                       .getOrElse(Future.successful(List[AddressJourneyTTLModel]()))
      ninoToDisplay <- ninoDisplayService.getNino

      personalDetailsModel = personalDetailsViewModel.getPersonDetailsTable(addressModel, ninoToDisplay)
      personDetails: Option[PersonDetails] = request.personDetails

      _ <- personDetails
            .map { details =>
              auditConnector.sendEvent(buildPersonDetailsEvent("personalDetailsPageLinkClicked", details))
            }
            .getOrElse(Future.successful(Unit))
      _ <- cachingHelper.addToCache(AddressPageVisitedDtoId, AddressPageVisitedDto(true))

    } yield Ok(personalDetailsViewV2(personalDetailsModel))
  }
}
