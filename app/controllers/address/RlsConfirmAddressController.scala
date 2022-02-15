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
import controllers.PertaxBaseController
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import models.AddressJourneyTTLModel
import play.api.mvc.{Action, ActionBuilder, AnyContent, MessagesControllerComponents}
import repositories.EditAddressLockRepository
import uk.gov.hmrc.renderer.TemplateRenderer
import viewmodels.PersonalDetailsViewModel
import views.html.personaldetails.RlsConfirmYourAddressView

import scala.concurrent.{ExecutionContext, Future}

class RlsConfirmAddressController @Inject() (
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  val editAddressLockRepository: EditAddressLockRepository,
  personalDetailsViewModel: PersonalDetailsViewModel,
  checkYourAddressInterruptView: RlsConfirmYourAddressView
)(implicit
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer,
  ec: ExecutionContext
) extends PertaxBaseController(cc) {

  private val authenticate: ActionBuilder[UserRequest, AnyContent] =
    authJourney.authWithPersonalDetails
  def onPageLoad(isResidential: Boolean): Action[AnyContent] = authenticate.async { implicit request =>
    for {
      addressModel <- request.nino
                        .map { nino =>
                          editAddressLockRepository.get(nino.withoutSuffix)
                        }
                        .getOrElse(
                          Future.successful(List[AddressJourneyTTLModel]())
                        )
    } yield {
      val personalDetails = personalDetailsViewModel
        .getPersonDetailsTable(request.nino)
      val addressDetails = personalDetailsViewModel.getAddressRow(addressModel)
      Ok(checkYourAddressInterruptView(personalDetails, addressDetails, isResidential))
    }
  }
}
