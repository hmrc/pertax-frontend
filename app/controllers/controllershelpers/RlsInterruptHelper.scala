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

package controllers.controllershelpers

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.PertaxBaseController
import controllers.auth.requests.UserRequest
import controllers.bindable.{InvalidAddresses, ValidAddressesBothInterrupt, ValidAddressesCorrespondenceInterrupt, ValidAddressesNoInterrupt, ValidAddressesResidentialInterrupt}
import play.api.mvc.{MessagesControllerComponents, Result}
import play.api.mvc.Results.{Locked, Redirect}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.renderer.TemplateRenderer
import views.html.personaldetails.CheckYourAddressInterruptView

import scala.concurrent.{ExecutionContext, Future}

class RlsInterruptHelper @Inject() (
  cc: MessagesControllerComponents
)(implicit ec: ExecutionContext, templateRenderer: TemplateRenderer)
    extends PertaxBaseController(cc) {

  def enforceByRlsStatus(
    block: => Future[Result]
  )(implicit
    request: UserRequest[_],
    hc: HeaderCarrier,
    ec: ExecutionContext,
    configDecorator: ConfigDecorator
  ): Future[Result] =
    if (configDecorator.rlsInterruptToggle) {
      request.personDetails
        .map { details =>
          if (details.hasRls) {
            Future.successful(Redirect(controllers.routes.RlsController.rlsInterruptOnPageLoad()))
          } else {
            block
          }
        }
        .getOrElse(block)
    } else {
      block
    }
}
