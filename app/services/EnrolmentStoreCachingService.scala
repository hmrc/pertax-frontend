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
import connectors.{EnrolmentsConnector, UsersGroupsSearchConnector}
import models.enrolments.{AccountDetails, EnrolmentDoesNotExist, EnrolmentError, EnrolmentResult, UsersAssignedEnrolment}
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import models.{NonFilerSelfAssessmentUser, NotEnrolledSelfAssessmentUser, SelfAssessmentUserType, UserAnswers, WrongCredentialsSelfAssessmentUser}
import play.api.Logging
import repositories.JourneyCacheRepository
import routePages.SelfAssessmentUserTypePage
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class EnrolmentStoreCachingService @Inject() (
  val journeyCacheRepository: JourneyCacheRepository,
  enrolmentsConnector: EnrolmentsConnector,
  usersGroupsSearchConnector: UsersGroupsSearchConnector
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
            .getUserIdsWithEnrolments("IR-SA~UTR", saUtr.utr)
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

  def retrieveMTDEnrolment(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    enrolmentsConnector
      .getKnownFacts(nino)
      .fold(
        _ => None,
        {
          case Some(response) => response.getHMRCMTDIT
          case _              => None
        }
      )

  def checkEnrolmentId(key: String, value: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[String]] =
    enrolmentsConnector
      .getUserIdsWithEnrolments(key, value)
      .foldF(
        _ => Future.successful(None),
        ids => Future.successful(ids.headOption)
      )

  def checkEnrolmentExists(id: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[EnrolmentResult] =
    usersGroupsSearchConnector
      .getUserDetails(id)
      .foldF(
        _ => Future.successful(EnrolmentError()),
        {
          case Some(userDetails) =>
            Future.successful(
              UsersAssignedEnrolment(
                AccountDetails(
                  userDetails.identityProviderType,
                  id,
                  userDetails.obfuscatedUserId.getOrElse(""),
                  userDetails.email.map(SensitiveString.apply),
                  userDetails.lastAccessedTimestamp,
                  AccountDetails.additionalFactorsToMFADetails(userDetails.additionalFactors),
                  None
                )
              )
            )
          case None              => Future.successful(EnrolmentDoesNotExist())
        }
      )

  def checkEnrolmentStatus(key: String, value: String)(implicit
    hc: HeaderCarrier,
    executionContext: ExecutionContext
  ): Future[EnrolmentResult] =
    for {
      userIds     <- checkEnrolmentId(key, value)
      accountInfo <- checkEnrolmentExists(userIds.head)
    } yield accountInfo

}
