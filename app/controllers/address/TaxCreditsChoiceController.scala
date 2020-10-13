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
import controllers.bindable.MainAddrType
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.SubmittedTaxCreditsChoiceId
import models.dto.TaxCreditsChoiceDto
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.EditAddressLockRepository
import uk.gov.hmrc.renderer.TemplateRenderer
import util.LocalPartialRetriever
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.TaxCreditsChoiceView

import scala.concurrent.{ExecutionContext, Future}

class TaxCreditsChoiceController @Inject()(
  authJourney: AuthJourney,
  withActiveTabAction: WithActiveTabAction,
  cc: MessagesControllerComponents,
  cachingHelper: AddressJourneyCachingHelper,
  taxCreditsChoiceView: TaxCreditsChoiceView,
  displayAddressInterstitialView: DisplayAddressInterstitialView,
  editAddressLockRepository: EditAddressLockRepository)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer,
  ec: ExecutionContext)
    extends AddressController(
      authJourney,
      withActiveTabAction,
      cc,
      displayAddressInterstitialView,
      editAddressLockRepository) {

  def onPageLoad: Action[AnyContent] = authenticate.async { implicit request =>
    addressJourneyEnforcer(MainAddrType) { _ => _ =>
      cachingHelper.gettingCachedAddressPageVisitedDto { addressPageVisitedDto =>
        cachingHelper.enforceDisplayAddressPageVisited(addressPageVisitedDto) {
          Future.successful(
            Ok(taxCreditsChoiceView(TaxCreditsChoiceDto.form, configDecorator.tcsChangeAddressUrl))
          )
        }
      }
    }
  }

  def onSubmit: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer(MainAddrType) { _ => _ =>
        TaxCreditsChoiceDto.form.bindFromRequest.fold(
          formWithErrors => {
            Future.successful(BadRequest(taxCreditsChoiceView(formWithErrors, configDecorator.tcsChangeAddressUrl)))
          },
          taxCreditsChoiceDto => {
            cachingHelper.addToCache(SubmittedTaxCreditsChoiceId, taxCreditsChoiceDto) map { _ =>
              if (taxCreditsChoiceDto.value) {
                Redirect(configDecorator.tcsChangeAddressUrl)
              } else {
                Redirect(routes.ResidencyChoiceController.onPageLoad())
              }
            }
          }
        )
      }
    }
}
