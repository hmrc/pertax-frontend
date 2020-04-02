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

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.bindable._
import controllers.controllershelpers.AddressJourneyAuditingHelper._
import controllers.controllershelpers.{AddressJourneyCachingHelper, CountryHelper, PersonalDetailsCardGenerator}
import com.google.inject.Inject
import models._
import models.addresslookup.RecordSet
import models.dto._
import org.joda.time.LocalDate
import play.api.Logger
import play.api.data.{Form, FormError}
import play.api.mvc._
import play.twirl.api.Html
import repositories.EditAddressLockRepository
import services._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.renderer.{ActiveTabYourAccount, TemplateRenderer}
import util.AuditServiceTools._
import util.PertaxSessionKeys.{filter, postcode}
import util.{LanguageHelper, LocalPartialRetriever}

import scala.concurrent.{ExecutionContext, Future}

class AddressController @Inject()(
  val citizenDetailsService: CitizenDetailsService,
  val addressLookupService: AddressLookupService,
  val addressMovedService: AddressMovedService,
  val personalDetailsCardGenerator: PersonalDetailsCardGenerator,
  val countryHelper: CountryHelper,
  val editAddressLockRepository: EditAddressLockRepository,
  authJourney: AuthJourney,
  val sessionCache: LocalSessionCache,
  withActiveTabAction: WithActiveTabAction,
  auditConnector: AuditConnector,
  cc: MessagesControllerComponents)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer,
  ec: ExecutionContext)
    extends PertaxBaseController(cc) with AddressJourneyCachingHelper {

  def addressJourneyEnforcer(block: Nino => PersonDetails => Future[Result])(
    implicit request: UserRequest[_]): Future[Result] =
    (for {
      payeAccount   <- request.nino
      personDetails <- request.personDetails
    } yield {
      block(payeAccount)(personDetails)
    }).getOrElse {
      Future.successful {
        val continueUrl = configDecorator.pertaxFrontendHost + controllers.routes.AddressController
          .personalDetails()
          .url
        Ok(views.html.interstitial.displayAddressInterstitial(continueUrl))
      }
    }

  private val authenticate
    : ActionBuilder[UserRequest, AnyContent] = authJourney.authWithPersonalDetails andThen withActiveTabAction
    .addActiveTab(ActiveTabYourAccount)

  //todo move to PersonalDetailsController
  def personalDetails: Action[AnyContent] = authenticate.async { implicit request =>
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
