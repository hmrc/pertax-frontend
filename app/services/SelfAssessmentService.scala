/*
 * Copyright 2021 HM Revenue & Customs
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

import config.ConfigDecorator
import connectors.SelfAssessmentConnector
import controllers.auth.requests.UserRequest
import javax.inject.Inject
import models.{SaEnrolmentRequest, SelfAssessmentUser}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class SelfAssessmentService @Inject() (
  selfAssessmentConnector: SelfAssessmentConnector,
  configDecorator: ConfigDecorator
)(implicit ec: ExecutionContext) {

  def getSaEnrolmentUrl(implicit
    request: UserRequest[_],
    hc: HeaderCarrier
  ): Future[Option[String]] = {
    def saEnrolmentRequest: SaEnrolmentRequest =
      request.saUserType match {
        case saEnrolment: SelfAssessmentUser =>
          SaEnrolmentRequest(
            configDecorator.addTaxesPtaOrigin,
            Some(saEnrolment.saUtr),
            request.credentials.providerId
          )
      }
    selfAssessmentConnector.enrolForSelfAssessment(saEnrolmentRequest) map {
      case Some(response) => Some(response.redirectUrl)
      case _              => None
    }
  }
}
