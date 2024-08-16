/*
 * Copyright 2024 HM Revenue & Customs
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

import com.google.inject.Inject
import connectors.EnrolmentsConnector
import models.{NonFilerSelfAssessmentUser, NotEnrolledSelfAssessmentUser, SelfAssessmentUserType, UserAnswers, WrongCredentialsSelfAssessmentUser}
import play.api.Logging
import repositories.JourneyCacheRepository
import routePages.SelfAssessmentUserTypePage
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class EnrolmentStoreCachingService @Inject() (
  val journeyCacheRepository: JourneyCacheRepository,
  enrolmentsConnector: EnrolmentsConnector
) extends Logging {

  private def addSaUserTypeToCache(
    userAnswers: UserAnswers,
    userType: SelfAssessmentUserType
  )(implicit ec: ExecutionContext): Future[SelfAssessmentUserType] = {
    val updatedUserAnswers = userAnswers.setOrException(SelfAssessmentUserTypePage, userType)
    journeyCacheRepository.set(updatedUserAnswers).map(_ => userType)
  }

  def getSaUserTypeFromCache(
    saUtr: SaUtr
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[SelfAssessmentUserType] =
    journeyCacheRepository.get(hc).flatMap { userAnswers =>
      userAnswers.get[SelfAssessmentUserType](SelfAssessmentUserTypePage) match {
        case Some(userType) => Future.successful(userType)
        case None           =>
          enrolmentsConnector
            .getUserIdsWithEnrolments(saUtr.utr)
            .foldF(
              _ => addSaUserTypeToCache(userAnswers, NonFilerSelfAssessmentUser),
              response =>
                if (response.nonEmpty) {
                  addSaUserTypeToCache(userAnswers, WrongCredentialsSelfAssessmentUser(saUtr))
                } else {
                  addSaUserTypeToCache(userAnswers, NotEnrolledSelfAssessmentUser(saUtr))
                }
            )
      }
    }
}
