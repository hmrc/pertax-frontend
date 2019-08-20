/*
 * Copyright 2019 HM Revenue & Customs
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

package services

import error.GenericErrors
import models.PertaxContext
import play.api.i18n.Messages
import play.api.mvc.Result
import uk.gov.hmrc.http.HttpResponse

sealed trait UpdateAddressResponse {
  def response(successResponseBlock: () => Result)(implicit pertaxContext: PertaxContext, messages: Messages): Result =
    this match {
      case UpdateAddressBadRequestResponse    => GenericErrors.badRequest
      case UpdateAddressUnexpectedResponse(_) => GenericErrors.internalServerError
      case UpdateAddressErrorResponse(_)      => GenericErrors.internalServerError
      case UpdateAddressSuccessResponse       => successResponseBlock()
    }
}
case object UpdateAddressSuccessResponse extends UpdateAddressResponse
case object UpdateAddressBadRequestResponse extends UpdateAddressResponse
case class UpdateAddressUnexpectedResponse(r: HttpResponse) extends UpdateAddressResponse
case class UpdateAddressErrorResponse(cause: Exception) extends UpdateAddressResponse
