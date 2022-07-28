/*
 * Copyright 2022 HM Revenue & Customs
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
import controllers.auth.AuthJourney
import controllers.bindable.AddrType
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.TaxCreditsChoiceId
import models.dto.TaxCreditsChoiceDto
import play.api.Logging
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.EditAddressLockRepository
import services.TaxCreditsService
import views.html.InternalServerErrorView
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.TaxCreditsChoiceView

import scala.concurrent.{ExecutionContext, Future}

class TaxCreditsChoiceController @Inject() (
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  cachingHelper: AddressJourneyCachingHelper,
  editAddressLockRepository: EditAddressLockRepository,
  displayAddressInterstitialView: DisplayAddressInterstitialView,
  taxCreditsService: TaxCreditsService,
  internalServerErrorView: InternalServerErrorView,
  taxCreditsChoiceView: TaxCreditsChoiceView
)(implicit configDecorator: ConfigDecorator, ec: ExecutionContext)
    extends AddressController(authJourney, cc, displayAddressInterstitialView) with Logging {

  def onPageLoad: Action[AnyContent] = authenticate.async { implicit request =>
    addressJourneyEnforcer { nino => _ =>
      cachingHelper.gettingCachedAddressPageVisitedDto { addressPageVisitedDto =>
        cachingHelper.enforceDisplayAddressPageVisited(addressPageVisitedDto) {
          if (configDecorator.addressChangeTaxCreditsQuestionEnabled) {
            Future.successful(
              Ok(taxCreditsChoiceView(TaxCreditsChoiceDto.form, configDecorator.tcsChangeAddressUrl))
            )
          } else {
            taxCreditsService.checkForTaxCredits(Some(nino)).map {
              case Some(true) =>
                cachingHelper.addToCache(TaxCreditsChoiceId, TaxCreditsChoiceDto(true))
                Redirect(configDecorator.tcsChangeAddressUrl)
              case Some(false) =>
                cachingHelper.addToCache(TaxCreditsChoiceId, TaxCreditsChoiceDto(false))
                Redirect(routes.DoYouLiveInTheUKController.onPageLoad)
              case None =>
                InternalServerError(internalServerErrorView())
            }
          }
        }
      }
    }
  }

  def onSubmit: Action[AnyContent] =
    authenticate.async { implicit request =>
      if (configDecorator.addressChangeTaxCreditsQuestionEnabled) {
        addressJourneyEnforcer { _ => _ =>
          TaxCreditsChoiceDto.form.bindFromRequest.fold(
            formWithErrors =>
              Future.successful(BadRequest(taxCreditsChoiceView(formWithErrors, configDecorator.tcsChangeAddressUrl))),
            taxCreditsChoiceDto =>
              cachingHelper.addToCache(TaxCreditsChoiceId, taxCreditsChoiceDto) map { _ =>
                if (taxCreditsChoiceDto.hasTaxCredits) {
                  editAddressLockRepository
                    .insert(
                      request.nino.get.withoutSuffix,
                      AddrType.apply("residential").get
                    )
                    .map {
                      case true => logger.warn("Address locked for tcs users")
                      case _    => logger.error(s"Could not insert address lock for user $request.nino.get.withoutSuffix")
                    }
                  Redirect(configDecorator.tcsChangeAddressUrl)
                } else {
                  Redirect(routes.DoYouLiveInTheUKController.onPageLoad)
                }
              }
          )
        }
      } else {
        Future.successful(Redirect(routes.PersonalDetailsController.onPageLoad))
      }
    }
}
