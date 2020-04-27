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

package controllers.address

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.bindable.AddrType
import controllers.controllershelpers.PersonalDetailsCardGenerator
import models.{AddressJourneyTTLModel, PersonDetails}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import play.twirl.api.Html
import repositories.EditAddressLockRepository
import services.LocalSessionCache
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.renderer.TemplateRenderer
import util.AuditServiceTools.buildPersonDetailsEvent
import util.LocalPartialRetriever

import scala.concurrent.{ExecutionContext, Future}

class PersonalDetailsController @Inject()(
  val personalDetailsCardGenerator: PersonalDetailsCardGenerator,
  val editAddressLockRepository: EditAddressLockRepository,
  auditConnector: AuditConnector,
  sessionCache: LocalSessionCache,
  authJourney: AuthJourney,
  withActiveTabAction: WithActiveTabAction,
  cc: MessagesControllerComponents
)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer,
  ec: ExecutionContext)
    extends AddressBaseController(sessionCache, authJourney, withActiveTabAction, cc) {

  def onPageLoad: Action[AnyContent] = authenticate.async { implicit request =>
    import models.dto.AddressPageVisitedDto

    for {
      addressModel <- request.nino
                       .map { nino =>
                         editAddressLockRepository.get(nino.withoutSuffix)
                       }
                       .getOrElse(Future.successful(List[AddressJourneyTTLModel]()))

      personalDetailsCards: Seq[Html] = personalDetailsCardGenerator.getPersonalDetailsCards(addressModel)
      personDetails: Option[PersonDetails] = request.personDetails

      _ <- personDetails
            .map { details =>
              auditConnector.sendEvent(buildPersonDetailsEvent("personalDetailsPageLinkClicked", details))
            }
            .getOrElse(Future.successful(Unit))
      _ <- addToCache(AddressPageVisitedDtoId, AddressPageVisitedDto(true))

    } yield Ok(views.html.personaldetails.personalDetails(personalDetailsCards))
  }

  def cannotUseThisService(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        gettingCachedAddressPageVisitedDto { addressPageVisitedDto =>
          enforceDisplayAddressPageVisited(addressPageVisitedDto) {
            Future.successful(Ok(views.html.personaldetails.cannotUseService(typ)))
          }
        }
      }
    }

  def showAddressAlreadyUpdated(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        Future.successful(Ok(views.html.personaldetails.addressAlreadyUpdated()))
      }
    }
}
