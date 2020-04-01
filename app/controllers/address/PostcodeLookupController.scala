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
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.bindable.{AddrType, PostalAddrType}
import models.addresslookup.{AddressLookupErrorResponse, AddressLookupResponse, AddressLookupSuccessResponse, AddressLookupUnexpectedResponse, RecordSet}
import models.dto.{AddressFinderDto, InternationalAddressChoiceDto}
import play.api.Logger
import play.api.data.FormError
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import services.{AddressLookupService, LocalSessionCache}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.renderer.TemplateRenderer
import util.AuditServiceTools.{buildAddressChangeEvent, buildEvent}
import util.LocalPartialRetriever
import util.PertaxSessionKeys.{filter, postcode}

import scala.concurrent.{ExecutionContext, Future}

class PostcodeLookupController @Inject()(
  val addressLookupService: AddressLookupService,
  sessionCache: LocalSessionCache,
  authJourney: AuthJourney,
  withActiveTabAction: WithActiveTabAction,
  auditConnector: AuditConnector,
  cc: MessagesControllerComponents
)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer,
  ec: ExecutionContext)
    extends AddressBaseController(sessionCache, authJourney, withActiveTabAction, cc) {

  def onPageLoad(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personDetails =>
        gettingCachedJourneyData(typ) { journeyData =>
          addToCache(SubmittedInternationalAddressChoiceId, InternationalAddressChoiceDto(true))
          typ match {
            case PostalAddrType =>
              auditConnector.sendEvent(
                buildAddressChangeEvent(
                  "postalAddressChangeLinkClicked",
                  personDetails,
                  isInternationalAddress = false))
              enforceDisplayAddressPageVisited(journeyData.addressPageVisitedDto) {
                Future.successful(Ok(views.html.personaldetails.postcodeLookup(AddressFinderDto.form, typ)))
              }
            case _ =>
              auditConnector.sendEvent(
                buildAddressChangeEvent("mainAddressChangeLinkClicked", personDetails, isInternationalAddress = false))
              enforceResidencyChoiceSubmitted(journeyData) { _ =>
                Future.successful(Ok(views.html.personaldetails.postcodeLookup(AddressFinderDto.form, typ)))
              }
          }
        }
      }
    }

  def onSubmit(typ: AddrType, back: Option[Boolean] = None): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        AddressFinderDto.form.bindFromRequest.fold(
          formWithErrors => {
            Future.successful(BadRequest(views.html.personaldetails.postcodeLookup(formWithErrors, typ)))
          },
          addressFinderDto => {

            if (addressFinderDto.postcode.isEmpty)
              Logger.warn("post code is empty for processPostCodeLookupForm")

            for {
              _ <- addToCache(AddressFinderDtoId(typ), addressFinderDto)
              lookupDown <- gettingCachedAddressLookupServiceDown { lookup =>
                             lookup
                           }
              result <- lookingUpAddress(
                         typ,
                         addressFinderDto.postcode,
                         lookupDown.getOrElse(false),
                         addressFinderDto.filter,
                         forceLookup = true) {
                         case AddressLookupSuccessResponse(RecordSet(Seq())) => //No records returned by postcode lookup
                           auditConnector.sendEvent(
                             buildEvent(
                               "addressLookupNotFound",
                               "find_address",
                               Map(postcode -> Some(addressFinderDto.postcode), filter -> addressFinderDto.filter)))
                           Future.successful(
                             NotFound(views.html.personaldetails.postcodeLookup(
                               AddressFinderDto.form
                                 .fill(AddressFinderDto(addressFinderDto.postcode, addressFinderDto.filter))
                                 .withError(FormError(postcode, "error.address_doesnt_exist_try_to_enter_manually")),
                               typ
                             )))
                         case AddressLookupSuccessResponse(RecordSet(Seq(addressRecord))) => //One record returned by postcode lookup
                           if (back.getOrElse(false)) {
                             Future.successful(Redirect(routes.PostcodeLookupController.onPageLoad(typ)))
                           } else {
                             auditConnector.sendEvent(
                               buildEvent(
                                 "addressLookupResults",
                                 "find_address",
                                 Map(
                                   postcode -> Some(addressRecord.address.postcode),
                                   filter   -> addressFinderDto.filter)))
                             addToCache(SelectedAddressRecordId(typ), addressRecord) map { _ =>
                               updateAddressRedirect(typ)
                             }
                           }
                         case AddressLookupSuccessResponse(recordSet) => //More than one record returned by postcode lookup
                           auditConnector.sendEvent(
                             buildEvent(
                               "addressLookupResults",
                               "find_address",
                               Map(postcode -> Some(addressFinderDto.postcode), filter -> addressFinderDto.filter)))

                           addToCache(SelectedRecordSetId(typ), recordSet) map { _ =>
                             Redirect(routes.AddressSelectorController
                               .onPageLoad(typ))
                               .addingToSession(
                                 (postcode, addressFinderDto.postcode),
                                 (filter, addressFinderDto.filter.getOrElse("")))
                           }
                       }
            } yield result
          }
        )
      }
    }

  private def lookingUpAddress(
    typ: AddrType,
    postcode: String,
    lookupServiceDown: Boolean,
    filter: Option[String] = None,
    forceLookup: Boolean = false)(f: PartialFunction[AddressLookupResponse, Future[Result]])(
    implicit request: UserRequest[_]): Future[Result] =
    if (!forceLookup && lookupServiceDown) {
      Future.successful(updateAddressRedirect(typ))
    } else {
      val handleError: PartialFunction[AddressLookupResponse, Future[Result]] = {
        case AddressLookupErrorResponse(_) | AddressLookupUnexpectedResponse(_) =>
          cacheAddressLookupServiceDown() map { _ =>
            updateAddressRedirect(typ)
          }
      }
      addressLookupService.lookup(postcode, filter).flatMap(handleError orElse f)
    }

  private def updateAddressRedirect(typ: AddrType): Result =
    Redirect(controllers.routes.AddressController.showUpdateAddressForm(typ))
}
