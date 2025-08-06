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
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import views.html.interstitial.SPPInterstitialView
import models.SelectSABPPPaymentFormProvider
import play.api.data.FormError

class SaBppInterstitialPageController @Inject() (
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  view: SPPInterstitialView,
  appConfig: ConfigDecorator
) extends PertaxBaseController(cc)
    with Logging {

  def onPageLoad: Action[AnyContent] = authJourney.authWithPersonalDetails { implicit request =>
    Ok(
      view(SelectSABPPPaymentFormProvider.form)
    )
  }

  def onSubmit: Action[AnyContent] = authJourney.authWithPersonalDetails { implicit request =>
    val origin = "pta-sa"
    SelectSABPPPaymentFormProvider.form
      .bindFromRequest()
      .fold(
        hasErrors => BadRequest(view(hasErrors)),
        success =>
          success.saBppWhatPaymentType match {
            case Some(SelectSABPPPaymentFormProvider.saBppOverduePayment) =>
              Redirect(
                s"${appConfig.bppSpreadTheCostOverduePaymentUrl}?origin=$origin"
              )
            case Some(SelectSABPPPaymentFormProvider.saBppAdvancePayment) =>
              Redirect(
                s"${appConfig.bppSpreadTheCostAdvancePaymentUrl}?origin=$origin&lang=${messagesApi.preferred(request).lang.code}"
              )
            case _                                                        =>
              BadRequest(
                view(
                  SelectSABPPPaymentFormProvider.form
                    .withError(FormError("saBppWhatPaymentType", "sa.message.selectSABPPPaymentType.error.required"))
                )
              )
          }
      )
  }
}
