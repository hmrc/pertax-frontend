/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.address.AddressControllerHelper
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.bindable._
import controllers.controllershelpers.AddressJourneyAuditingHelper._
import controllers.controllershelpers.{AddressJourneyCachingHelper, PersonalDetailsCardGenerator}
import models._
import models.dto._
import org.joda.time.LocalDate
import play.api.Logger
import play.api.data.Form
import play.api.mvc._
import play.twirl.api.Html
import repositories.EditAddressLockRepository
import services._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.renderer.TemplateRenderer
import util.AuditServiceTools._
import util.{LanguageHelper, LocalPartialRetriever}
import views.html.error
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails._

import scala.concurrent.{ExecutionContext, Future}

class AddressController @Inject()(
  val personalDetailsCardGenerator: PersonalDetailsCardGenerator,
  val editAddressLockRepository: EditAddressLockRepository,
  ninoDisplayService: NinoDisplayService,
  authJourney: AuthJourney,
  cachingHelper: AddressJourneyCachingHelper,
  withActiveTabAction: WithActiveTabAction,
  auditConnector: AuditConnector,
  cc: MessagesControllerComponents,
  displayAddressInterstitialView: DisplayAddressInterstitialView,
  personalDetailsView: PersonalDetailsView,
  cannotUseServiceView: CannotUseServiceView,
  addressAlreadyUpdatedView: AddressAlreadyUpdatedView
)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer,
  ec: ExecutionContext)
    extends AddressControllerHelper(authJourney, withActiveTabAction, cc, displayAddressInterstitialView) {

  def personalDetails: Action[AnyContent] = authenticate.async { implicit request =>
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

  def cannotUseThisService(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        cachingHelper.gettingCachedAddressPageVisitedDto { addressPageVisitedDto =>
          cachingHelper.enforceDisplayAddressPageVisited(addressPageVisitedDto) {
            Future.successful(Ok(cannotUseServiceView(typ)))
          }
        }
      }
    }

  def showAddressAlreadyUpdated(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        Future.successful(Ok(addressAlreadyUpdatedView()))
      }
    }
}
