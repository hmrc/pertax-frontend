/*
 * Copyright 2025 HM Revenue & Customs
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

package controllers

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.auth.AuthJourney
import play.api.Logging
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, RequestHeader}
import views.html.interstitial.SPPInterstitialView
import models.SelectSABPPPaymentFormProvider

import scala.concurrent.ExecutionContext

class SaBppInterstitialPageController @Inject() (
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  view: SPPInterstitialView
)(implicit configDecorator: ConfigDecorator, ec: ExecutionContext)
    extends PertaxBaseController(cc)
    with Logging {

  def onPageLoad(isBta: String): Action[AnyContent] = authJourney.authWithPersonalDetails { implicit request =>
    val origin: String = isBta match {
      case "true" => "bta-sa"
      case _      => "pta-sa"
    }
    Ok(
      view(SelectSABPPPaymentFormProvider.form, origin)
    )
  }

  def onSubmit(origin: String): Action[AnyContent] = authJourney.authWithPersonalDetails { implicit request =>
    SelectSABPPPaymentFormProvider.form
      .bindFromRequest()
      .fold(
        hasErrors => BadRequest(view(hasErrors, origin)),
        success =>
          success.saBppWhatPaymentType match {
            case Some(SelectSABPPPaymentFormProvider.saBppOverduePayment) =>
              if (isEnabled(SaBppSstpNewUrlFeature)(appConfig))
                Redirect(
                  controllers.routes.SsttpController
                    .routeToSsTttpService(SaBppInterstitialPageFormKeys.saBppOverduePayment, origin)
                )
              else
                Redirect(s"${externalUrlConfig.getGovUrl("sa.bppSpreadTheCostOverduePaymentUrl")}?origin=$origin")
            case Some(SelectSABPPPaymentFormProvider.saBppAdvancePayment) =>
              Redirect(s"${appConfig.bppSpreadTheCostAdvancePaymentUrl(origin, messages.lang.code)}")
            case _                                                        => throw new IllegalArgumentException("No form option is selected. Empty value for form.")
          }
      )
  }
}
