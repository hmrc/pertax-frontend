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

package models.admin

import play.api.mvc.{Request, WrappedRequest}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.internalauth.client.{AuthenticatedRequest, AuthorizationToken, Retrieval}

case class AuthRequest[A](
  request: Request[A],
  headerCarrier: HeaderCarrier,
  authorizationToken: AuthorizationToken,
  retrieval: Retrieval.Username
) extends WrappedRequest(request)

object AuthRequest {
  def apply[A, R](authenticatedRequest: AuthenticatedRequest[A, Retrieval.Username]): AuthRequest[A] =
    AuthRequest(
      authenticatedRequest.request,
      authenticatedRequest.headerCarrier,
      authenticatedRequest.authorizationToken,
      authenticatedRequest.retrieval
    )
}
