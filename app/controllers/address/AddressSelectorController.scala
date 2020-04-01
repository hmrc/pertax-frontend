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
import controllers.address
import models.dto.{AddressDto, AddressSelectorDto, DateDto}
import org.joda.time.LocalDate
import play.api.Logger
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.LocalSessionCache
import uk.gov.hmrc.renderer.TemplateRenderer
import util.LocalPartialRetriever
import util.PertaxSessionKeys.{filter, postcode}

import scala.concurrent.{ExecutionContext, Future}

class AddressSelectorController @Inject()(
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
      gettingCachedJourneyData(typ) { journeyData =>
        journeyData.recordSet match {
          case Some(set) =>
            Future.successful(
              Ok(
                views.html.personaldetails.addressSelector(
                  AddressSelectorDto.form,
                  set,
                  typ,
                  getFromRequest(postcode).getOrElse(""),
                  getFromRequest(filter)
                )
              )
            )
          case _ => Future.successful(Redirect(address.routes.PostcodeLookupController.onPageLoad(typ)))
        }
      }
    }

  def onSubmit(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      val errorPage = Future.successful(
        InternalServerError(
          views.html.error(
            "global.error.InternalServerError500.title",
            Some("global.error.InternalServerError500.title"),
            List("global.error.InternalServerError500.message")
          )
        )
      )

      addressJourneyEnforcer { _ => personDetails =>
        gettingCachedJourneyData(typ) { journeyData =>
          AddressSelectorDto.form.bindFromRequest.fold(
            formWithErrors => {

              journeyData.recordSet match {
                case Some(set) =>
                  Future.successful(
                    BadRequest(
                      views.html.personaldetails.addressSelector(
                        formWithErrors,
                        set,
                        typ,
                        getFromRequest(postcode).getOrElse(""),
                        getFromRequest(filter)
                      )
                    ))
                case _ =>
                  Logger.warn("Failed to retrieve Address Record Set from cache")
                  errorPage
              }
            },
            addressSelectorDto => {
              journeyData.recordSet
                .flatMap(_.addresses.find(_.id == addressSelectorDto.addressId.getOrElse(""))) match {
                case Some(addressRecord) =>
                  val addressDto = AddressDto.fromAddressRecord(addressRecord)

                  for {
                    _ <- addToCache(SelectedAddressRecordId(typ), addressRecord)
                    _ <- addToCache(SubmittedAddressDtoId(typ), addressDto)
                  } yield {
                    val postCodeHasChanged = !getFromRequest(postcode)
                      .getOrElse("")
                      .replace(" ", "")
                      .equalsIgnoreCase(personDetails.address.flatMap(_.postcode).getOrElse("").replace(" ", ""))
                    (typ, postCodeHasChanged) match {
                      case (PostalAddrType, false) =>
                        Redirect(routes.UpdateAddressController.onPageLoad(typ))
                      case (_, true) => Redirect(routes.StartDateController.onPageLoad(typ))
                      case (_, false) =>
                        addToCache(SubmittedStartDateId(typ), DateDto(LocalDate.now()))
                        Redirect(controllers.routes.AddressController.reviewChanges(typ))
                    }
                  }
                case _ =>
                  Logger.warn("Address selector was unable to find address using the id returned by a previous request")
                  errorPage
              }
            }
          )
        }
      }
    }

  private def getFromRequest(target: String)(implicit request: UserRequest[AnyContent]): Option[String] =
    request.body.asFormUrlEncoded.flatMap(_.get(target).flatMap(_.headOption))

}
