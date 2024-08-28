/*
 * Copyright 2024 HM Revenue & Customs
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
import connectors.AddressLookupConnector
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.bindable.{AddrType, PostalAddrType}
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.addresslookup.RecordSet
import models.dto.{AddressFinderDto, InternationalAddressChoiceDto}
import play.api.Logging
import play.api.data.FormError
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import routePages.{AddressFinderPage, SelectedAddressRecordPage, SelectedRecordSetPage, SubmittedInternationalAddressChoicePage}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import util.AuditServiceTools.{buildAddressChangeEvent, buildEvent}
import util.PertaxSessionKeys.{filter, postcode}
import views.html.InternalServerErrorView
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.PostcodeLookupView

import scala.concurrent.{ExecutionContext, Future}

class PostcodeLookupController @Inject() (
  val addressLookupConnector: AddressLookupConnector,
  cachingHelper: AddressJourneyCachingHelper,
  auditConnector: AuditConnector,
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  postcodeLookupView: PostcodeLookupView,
  displayAddressInterstitialView: DisplayAddressInterstitialView,
  featureFlagService: FeatureFlagService,
  internalServerErrorView: InternalServerErrorView
)(implicit configDecorator: ConfigDecorator, ec: ExecutionContext)
    extends AddressController(
      authJourney,
      cc,
      displayAddressInterstitialView,
      featureFlagService,
      internalServerErrorView
    )
    with Logging {

  def onPageLoad(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personDetails =>
        cachingHelper.gettingCachedJourneyData(typ) { _ =>
          cachingHelper.addToCache(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto(true))
          typ match {
            case PostalAddrType =>
              auditConnector.sendEvent(
                buildAddressChangeEvent("postalAddressChangeLinkClicked", personDetails, isInternationalAddress = false)
              )
              cachingHelper.enforceDisplayAddressPageVisited(Ok(postcodeLookupView(AddressFinderDto.form, typ)))
            case _              =>
              auditConnector.sendEvent(
                buildAddressChangeEvent("mainAddressChangeLinkClicked", personDetails, isInternationalAddress = false)
              )
              cachingHelper.enforceDisplayAddressPageVisited(Ok(postcodeLookupView(AddressFinderDto.form, typ)))
          }
        }
      }
    }

  def onSubmit(typ: AddrType, back: Option[Boolean] = None): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        AddressFinderDto.form
          .bindFromRequest()
          .fold(
            formWithErrors => Future.successful(BadRequest(postcodeLookupView(formWithErrors, typ))),
            addressFinderDto => {

              if (addressFinderDto.postcode.isEmpty) {
                logger.warn("post code is empty for processPostCodeLookupForm")
              }

              for {
                _          <- cachingHelper.addToCache(AddressFinderPage(typ), addressFinderDto)
                lookupDown <- cachingHelper.gettingCachedAddressLookupServiceDown { lookup =>
                                lookup
                              }
                result     <- lookingUpAddress(
                                typ,
                                addressFinderDto.postcode,
                                lookupDown.getOrElse(false),
                                addressFinderDto.filter,
                                forceLookup = true
                              ) {
                                case addressList if addressList.addresses.isEmpty     =>
                                  auditConnector.sendEvent(
                                    buildEvent(
                                      "addressLookupNotFound",
                                      "find_address",
                                      Map(postcode -> Some(addressFinderDto.postcode), filter -> addressFinderDto.filter)
                                    )
                                  )
                                  Future.successful(
                                    NotFound(
                                      postcodeLookupView(
                                        AddressFinderDto.form
                                          .fill(AddressFinderDto(addressFinderDto.postcode, addressFinderDto.filter))
                                          .withError(
                                            FormError(postcode, "error.address_doesnt_exist_try_to_enter_manually")
                                          ),
                                        typ
                                      )
                                    )
                                  )
                                case addressList if addressList.addresses.length == 1 =>
                                  if (back.getOrElse(false)) {
                                    Future.successful(Redirect(routes.PostcodeLookupController.onPageLoad(typ)))
                                  } else {
                                    auditConnector.sendEvent(
                                      buildEvent(
                                        "addressLookupResults",
                                        "find_address",
                                        Map(
                                          postcode -> Some(addressList.addresses.head.address.postcode),
                                          filter   -> addressFinderDto.filter
                                        )
                                      )
                                    )
                                    cachingHelper
                                      .addToCache(SelectedAddressRecordPage(typ), addressList.addresses.head) map { _ =>
                                      Redirect(routes.UpdateAddressController.onPageLoad(typ))
                                    }
                                  }
                                case addressList                                      =>
                                  auditConnector.sendEvent(
                                    buildEvent(
                                      "addressLookupResults",
                                      "find_address",
                                      Map(postcode -> Some(addressFinderDto.postcode), filter -> addressFinderDto.filter)
                                    )
                                  )

                                  cachingHelper.addToCache(SelectedRecordSetPage(typ), addressList) map { _ =>
                                    Redirect(routes.AddressSelectorController.onPageLoad(typ))
                                      .addingToSession(
                                        (postcode, addressFinderDto.postcode),
                                        (filter, addressFinderDto.filter.getOrElse(""))
                                      )
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
    filter: Option[String],
    forceLookup: Boolean
  )(f: PartialFunction[RecordSet, Future[Result]])(implicit request: UserRequest[_]): Future[Result] =
    if (!forceLookup && lookupServiceDown) {
      Future.successful(Redirect(routes.UpdateAddressController.onPageLoad(typ)))
    } else {
      addressLookupConnector
        .lookup(postcode, filter)
        .foldF(
          _ =>
            cachingHelper.cacheAddressLookupServiceDown() map { _ =>
              Redirect(routes.UpdateAddressController.onPageLoad(typ))
            },
          response => f.apply(response)
        )
    }
}
