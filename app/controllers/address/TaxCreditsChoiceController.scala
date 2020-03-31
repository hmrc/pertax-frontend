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
import models.dto.TaxCreditsChoiceDto
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.LocalSessionCache
import uk.gov.hmrc.renderer.TemplateRenderer
import util.LocalPartialRetriever

import scala.concurrent.{ExecutionContext, Future}

class TaxCreditsChoiceController @Inject()(
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
    addressJourneyEnforcer { _ => _ =>
      gettingCachedAddressPageVisitedDto { addressPageVisitedDto =>
        enforceDisplayAddressPageVisited(addressPageVisitedDto) {
          Future.successful(
            Ok(
              views.html.personaldetails
                .taxCreditsChoice(TaxCreditsChoiceDto.form, configDecorator.tcsChangeAddressUrl))
          )
        }
      }
    }
  }

  def onSubmit: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        TaxCreditsChoiceDto.form.bindFromRequest.fold(
          formWithErrors => {
            Future.successful(
              BadRequest(
                views.html.personaldetails.taxCreditsChoice(formWithErrors, configDecorator.tcsChangeAddressUrl)))
          },
          taxCreditsChoiceDto => {
            addToCache(SubmittedTaxCreditsChoiceId, taxCreditsChoiceDto) map { _ =>
              if (taxCreditsChoiceDto.value) {
                Redirect(configDecorator.tcsChangeAddressUrl)
              } else {
                Redirect(controllers.routes.AddressController.residencyChoice())
              }
            }
          }
        )

      }
    }
}
