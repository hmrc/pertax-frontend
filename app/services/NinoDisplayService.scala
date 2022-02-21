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

package services

import com.google.inject.{Inject, Singleton}
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

/*
There is an issue with NINOs obtained from Auth/IV where the suffix is incorrect,
when we display a NINO to a user we always want to obtain the NINO from CID where it
matches the NINO ignoring the suffix (and audits the mismatch) and retrieves the
correct full NINO. We can't use this call downstream as we are not sure how the suffix
will be treated in the HODs/DES layer.
 */

@Singleton
class NinoDisplayService @Inject() (configDecorator: ConfigDecorator, citizenDetailsService: CitizenDetailsService)(
  implicit ec: ExecutionContext
) {

  def getNino(implicit request: UserRequest[_], hc: HeaderCarrier): Future[Option[Nino]] =
    if (configDecorator.getNinoFromCID) {
      request.nino match {
        case Some(nino) =>
          for {
            result <- citizenDetailsService.personDetails(nino)
          } yield result match {
            case PersonDetailsSuccessResponse(personDetails) => personDetails.person.nino
            case _                                           => None
          }
        case _ => Future.successful(None)
      }
    } else {
      Future.successful(request.nino)
    }
}
