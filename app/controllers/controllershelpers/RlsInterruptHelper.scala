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

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.bindable.{InvalidAddresses, ValidAddressesBothInterrupt, ValidAddressesCorrespondenceInterrupt, ValidAddressesNoInterrupt, ValidAddressesResidentialInterrupt}
import play.api.mvc.Result
import play.api.mvc.Results.Redirect
import services.CitizenDetailsService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait RlsInterruptHelper {

  def citizenDetailsService: CitizenDetailsService

  def enforceByRlsStatus(
    block: => Future[Result]
  )(implicit
    request: UserRequest[_],
    hc: HeaderCarrier,
    ec: ExecutionContext,
    configDecorator: ConfigDecorator
  ): Future[Result] =
    if (configDecorator.getAddressStatusFromCID) {
      citizenDetailsService.getAddressStatusFromPersonalDetails.flatMap {
        case ValidAddressesNoInterrupt          => block
        case ValidAddressesBothInterrupt        => Future.successful(Redirect("redirectUrl")) /// /confirm-your-address
        case ValidAddressesResidentialInterrupt => Future.successful(Redirect("redirectUrl")) /// /confirm-your-address
        case ValidAddressesCorrespondenceInterrupt =>
          Future.successful(Redirect("redirectUrl")) /// /confirm-your-address
        case InvalidAddresses => Future.failed(new Exception)
      }
    } else {
      block
    }
}
