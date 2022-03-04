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
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.bindable.{AddrType, ResidentialAddrType}
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.SubmittedTaxCreditsChoiceId
import models.dto.TaxCreditsChoiceDto
import play.api.Logging
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.EditAddressLockRepository
import uk.gov.hmrc.renderer.TemplateRenderer
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.TaxCreditsChoiceView

import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant
import java.time.LocalDate

class TaxCreditsChoiceController @Inject() (
  authJourney: AuthJourney,
  withActiveTabAction: WithActiveTabAction,
  cc: MessagesControllerComponents,
  cachingHelper: AddressJourneyCachingHelper,
  editAddressLockRepository: EditAddressLockRepository,
  taxCreditsChoiceView: TaxCreditsChoiceView,
  displayAddressInterstitialView: DisplayAddressInterstitialView
)(implicit configDecorator: ConfigDecorator, templateRenderer: TemplateRenderer, ec: ExecutionContext)
    extends AddressController(authJourney, withActiveTabAction, cc, displayAddressInterstitialView) with Logging {

  def onPageLoad: Action[AnyContent] = authenticate.async { implicit request =>
    addressJourneyEnforcer { _ => _ =>
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
      addressJourneyEnforcer { _ => _ =>
        TaxCreditsChoiceDto.form.bindFromRequest.fold(
          formWithErrors =>
            Future.successful(BadRequest(taxCreditsChoiceView(formWithErrors, configDecorator.tcsChangeAddressUrl))),
          taxCreditsChoiceDto =>
            cachingHelper.addToCache(SubmittedTaxCreditsChoiceId, taxCreditsChoiceDto) map { _ =>
              if (taxCreditsChoiceDto.value) {
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
    }
}
