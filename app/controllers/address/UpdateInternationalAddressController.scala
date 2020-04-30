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
import controllers.bindable.{AddrType, PostalAddrType}
import controllers.controllershelpers.CountryHelper
import models.dto.{AddressDto, DateDto}
import org.joda.time.LocalDate
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import services.LocalSessionCache
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.renderer.TemplateRenderer
import util.AuditServiceTools.buildAddressChangeEvent
import util.LocalPartialRetriever

import scala.concurrent.{ExecutionContext, Future}

class UpdateInternationalAddressController @Inject()(
  val countryHelper: CountryHelper,
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

  def onPageLoad(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      gettingCachedJourneyData[Result](typ) { journeyData =>
        addressJourneyEnforcer { _ => personDetails =>
          typ match {
            case PostalAddrType =>
              auditConnector.sendEvent(
                buildAddressChangeEvent("postalAddressChangeLinkClicked", personDetails, isInternationalAddress = true))
              enforceDisplayAddressPageVisited(journeyData.addressPageVisitedDto) {
                Future.successful(
                  Ok(
                    views.html.personaldetails.updateInternationalAddress(
                      journeyData.submittedAddressDto.fold(AddressDto.internationalForm)(
                        AddressDto.internationalForm.fill),
                      typ,
                      countryHelper.countries
                    )
                  )
                )
              }

            case _ =>
              auditConnector.sendEvent(
                buildAddressChangeEvent("mainAddressChangeLinkClicked", personDetails, isInternationalAddress = true))
              enforceResidencyChoiceSubmitted(journeyData) { _ =>
                Future.successful(
                  Ok(
                    views.html.personaldetails
                      .updateInternationalAddress(AddressDto.internationalForm, typ, countryHelper.countries)
                  )
                )
              }
          }
        }
      }
    }

  def onSubmit(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      gettingCachedJourneyData[Result](typ) { _ =>
        addressJourneyEnforcer { _ => _ =>
          {
            AddressDto.internationalForm.bindFromRequest.fold(
              formWithErrors => {
                Future.successful(BadRequest(
                  views.html.personaldetails.updateInternationalAddress(formWithErrors, typ, countryHelper.countries)))
              },
              addressDto => {
                addToCache(SubmittedAddressDtoId(typ), addressDto) flatMap { _ =>
                  typ match {
                    case PostalAddrType =>
                      addToCache(SubmittedStartDateId(typ), DateDto(LocalDate.now()))
                      Future.successful(Redirect(routes.AddressSubmissionController.onPageLoad(typ)))
                    case _ =>
                      Future.successful(Redirect(routes.StartDateController.onPageLoad(typ)))
                  }
                }
              }
            )
          }
        }
      }
    }
}
