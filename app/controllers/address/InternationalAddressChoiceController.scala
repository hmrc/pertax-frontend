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
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.SubmittedInternationalAddressChoiceId
import models.dto.InternationalAddressChoiceDto
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.EditAddressLockRepository
import uk.gov.hmrc.renderer.TemplateRenderer
import util.LocalPartialRetriever
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.{AddressAlreadyUpdatedView, InternationalAddressChoiceView}

import scala.concurrent.{ExecutionContext, Future}

class InternationalAddressChoiceController @Inject()(
  cachingHelper: AddressJourneyCachingHelper,
  authJourney: AuthJourney,
  withActiveTabAction: WithActiveTabAction,
  cc: MessagesControllerComponents,
  internationalAddressChoiceView: InternationalAddressChoiceView,
  displayAddressInterstitialView: DisplayAddressInterstitialView,
  editAddressLockRepository: EditAddressLockRepository,
  addressAlreadyUpdatedView: AddressAlreadyUpdatedView)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer,
  ec: ExecutionContext)
    extends AddressController(
      authJourney,
      withActiveTabAction,
      cc,
      displayAddressInterstitialView,
      editAddressLockRepository,
      addressAlreadyUpdatedView) {

  def onPageLoad(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer(typ) { _ => _ =>
        cachingHelper.gettingCachedAddressPageVisitedDto { addressPageVisitedDto =>
          cachingHelper.enforceDisplayAddressPageVisited(addressPageVisitedDto) {
            Future.successful(
              Ok(internationalAddressChoiceView(InternationalAddressChoiceDto.form, typ))
            )
          }
        }
      }
    }

  def onSubmit(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer(typ) { _ => _ =>
        InternationalAddressChoiceDto.form.bindFromRequest.fold(
          formWithErrors => {
            Future.successful(BadRequest(internationalAddressChoiceView(formWithErrors, typ)))
          },
          internationalAddressChoiceDto => {
            cachingHelper.addToCache(SubmittedInternationalAddressChoiceId, internationalAddressChoiceDto) map { _ =>
              if (internationalAddressChoiceDto.value) {
                Redirect(routes.PostcodeLookupController.onPageLoad(typ))
              } else {
                if (configDecorator.updateInternationalAddressInPta) {
                  Redirect(routes.UpdateInternationalAddressController.onPageLoad(typ))
                } else {
                  Redirect(routes.AddressErrorController.cannotUseThisService(typ))
                }
              }
            }
          }
        )

      }
    }
}
