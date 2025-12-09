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
import connectors.SsttpConnector
import controllers.auth.AuthJourney
import error.LocalErrorHandler
import play.api.Logging
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import views.html.interstitial.SPPInterstitialView
import models.SelectSABPPPaymentFormProvider
import play.api.data.FormError
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

class SaBppInterstitialPageController @Inject() (
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  view: SPPInterstitialView,
  appConfig: ConfigDecorator,
  ssttpConnector: SsttpConnector,
  errorHandler: LocalErrorHandler
) extends PertaxBaseController(cc)
    with Logging {

  implicit val ec: ExecutionContext = cc.executionContext

  def onPageLoad: Action[AnyContent] = authJourney.authWithPersonalDetails { implicit request =>
    Ok(
      view(SelectSABPPPaymentFormProvider.form)
    )
  }

  def onSubmit: Action[AnyContent] = authJourney.authWithPersonalDetails.async { implicit request =>

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    SelectSABPPPaymentFormProvider.form
      .bindFromRequest()
      .fold(
        hasErrors => Future.successful(BadRequest(view(hasErrors))),
        success =>
          success.saBppWhatPaymentType match {
            case Some(SelectSABPPPaymentFormProvider.saBppOverduePayment) =>
              ssttpConnector.startPtaJourney().value.flatMap {
                case Right(ssttpResponse) =>
                  logger.info(
                    s"[SaBppInterstitialPageController][onSubmit] ssttpResponse nextUrl: ${ssttpResponse.nextUrl}"
                  )
                  Future.successful(Redirect(ssttpResponse.nextUrl))

                case Left(upstreamError) =>
                  logger.error(
                    s"[SaBppInterstitialPageController][onSubmit] startPtaJourney returned upstream error: ${upstreamError.message}"
                  )
                  errorHandler.badRequestTemplate.map(ServiceUnavailable(_))
              }

            case Some(SelectSABPPPaymentFormProvider.saBppAdvancePayment) =>
              Future.successful(
                Redirect(
                  s"${appConfig.bppSpreadTheCostAdvancePaymentUrl}?calledFrom=pta-web&lang=${messagesApi.preferred(request).lang.code}"
                )
              )
            case _                                                        =>
              Future.successful(
                BadRequest(
                  view(
                    SelectSABPPPaymentFormProvider.form
                      .withError(FormError("saBppWhatPaymentType", "sa.message.selectSABPPPaymentType.error.required"))
                  )
                )
              )
          }
      )
  }
}
