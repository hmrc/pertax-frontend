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

package controllers.tempAddressFix

import com.google.inject.Inject
import models.tempAddressFix.AddressFixRecord
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.TempAddressFixRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext

class FixController @Inject() (
  cc: MessagesControllerComponents,
  tempAddressFixRepository: TempAddressFixRepository
)(implicit ec: ExecutionContext)
    extends FrontendController(cc) {

  def putData: Action[AnyContent] = Action.async { implicit request =>
    tempAddressFixRepository
      .insert(
        AddressFixRecord(
          nino = "AA123456A",
          postcode = "AA1 1AA",
          status = "todo"
        )
      )
      .map(_ => Ok(""))
  }

  def getData(key: String): Action[AnyContent] = Action.async { implicit request =>
    tempAddressFixRepository
      .findByKey(key)
      .map(r => Ok(r.toString))
  }

}
