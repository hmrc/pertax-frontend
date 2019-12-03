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

import com.google.inject.Inject
import connectors.EnrolmentsConnector
import models.{NonFilerSelfAssessmentUser, NotEnrolledSelfAssessmentUser, SelfAssessmentUserType, WrongCredentialsSelfAssessmentUser}
import play.api.Logger
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class EnrolmentStoreCachingService @Inject()(
  val sessionCache: LocalSessionCache,
  enrolmentsConnector: EnrolmentsConnector) {

  private def addSaUserTypeToCache(
    user: SelfAssessmentUserType)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[SelfAssessmentUserType] =
    sessionCache.cache[SelfAssessmentUserType](SelfAssessmentUserType.cacheId, user).map(_ => user)

  def getSaUserTypeFromCache(
    saUtr: SaUtr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[SelfAssessmentUserType] =
    sessionCache.fetchAndGetEntry[SelfAssessmentUserType](SelfAssessmentUserType.cacheId).flatMap {

      case Some(user) => Future.successful(user)

      case _ =>
        enrolmentsConnector
          .getUserIdsWithEnrolments(saUtr.utr)
          .flatMap[SelfAssessmentUserType](
            (response: Either[String, Seq[String]]) =>
              response.fold(
                error => {
                  Logger.warn(error)
                  addSaUserTypeToCache(NonFilerSelfAssessmentUser)
                },
                ids =>
                  if (ids.nonEmpty) {
                    addSaUserTypeToCache(WrongCredentialsSelfAssessmentUser(saUtr))
                  } else {
                    addSaUserTypeToCache(NotEnrolledSelfAssessmentUser(saUtr))
                }
            )
          )
    }

}
