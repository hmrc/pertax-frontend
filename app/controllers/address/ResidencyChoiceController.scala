/*
 * Copyright 2021 HM Revenue & Customs
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
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.SubmittedResidencyChoiceDtoId
import models.dto.{ResidencyChoiceDto, TaxCreditsChoiceDto}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.renderer.TemplateRenderer
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.ResidencyChoiceView

import scala.concurrent.{ExecutionContext, Future}

class ResidencyChoiceController @Inject() (
  cachingHelper: AddressJourneyCachingHelper,
  authJourney: AuthJourney,
  withActiveTabAction: WithActiveTabAction,
  cc: MessagesControllerComponents,
  residencyChoiceView: ResidencyChoiceView,
  displayAddressInterstitialView: DisplayAddressInterstitialView
)(implicit
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer,
  ec: ExecutionContext
) extends AddressController(
      authJourney,
      withActiveTabAction,
      cc,
      displayAddressInterstitialView
    ) {

  def onPageLoad: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        cachingHelper.gettingCachedTaxCreditsChoiceDto {
          case Some(TaxCreditsChoiceDto(false)) =>
            Ok(residencyChoiceView(ResidencyChoiceDto.form))
          case _ =>
            if (configDecorator.taxCreditsEnabled)
              Redirect(routes.PersonalDetailsController.onPageLoad())
            else
              Ok(residencyChoiceView(ResidencyChoiceDto.form))
        }
      }
    }

  def onSubmit: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        ResidencyChoiceDto.form.bindFromRequest.fold(
          formWithErrors => Future.successful(BadRequest(residencyChoiceView(formWithErrors))),
          residencyChoiceDto =>
            cachingHelper
              .addToCache(
                SubmittedResidencyChoiceDtoId(
                  residencyChoiceDto.residencyChoice
                ),
                residencyChoiceDto
              ) map { _ =>
              Redirect(
                routes.InternationalAddressChoiceController
                  .onPageLoad(residencyChoiceDto.residencyChoice)
              )
            }
        )

      }
    }
}
