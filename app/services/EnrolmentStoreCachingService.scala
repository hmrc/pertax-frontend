/*
 * Copyright 2023 HM Revenue & Customs
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

//import cats.data.EitherT
import com.google.inject.Inject
import connectors.{EnrolmentsConnector, UsersGroupsSearchConnector}
import models.enrolments.{AccountDetails, EnrolmentDoesNotExist, EnrolmentError, EnrolmentResult, UsersAssignedEnrolment}
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
//import models.enrolments.{EnrolmentDoesNotExist, EnrolmentError, EnrolmentResult, GroupAssignedEnrolment, UsersAssignedEnrolment}
import models.{NonFilerSelfAssessmentUser, NotEnrolledSelfAssessmentUser, SelfAssessmentUserType, WrongCredentialsSelfAssessmentUser}
import play.api.Logging
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class EnrolmentStoreCachingService @Inject() (
  val sessionCache: LocalSessionCache,
  enrolmentsConnector: EnrolmentsConnector,
  usersGroupsSearchConnector: UsersGroupsSearchConnector
) extends Logging {

  private def addSaUserTypeToCache(
    user: SelfAssessmentUserType
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[SelfAssessmentUserType] =
    sessionCache.cache[SelfAssessmentUserType](SelfAssessmentUserType.cacheId, user).map(_ => user)

  def getSaUserTypeFromCache(
    saUtr: SaUtr
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[SelfAssessmentUserType] =
    sessionCache.fetchAndGetEntry[SelfAssessmentUserType](SelfAssessmentUserType.cacheId).flatMap {

      case Some(user) => Future.successful(user)

      case _ =>
        enrolmentsConnector
          .getUserIdsWithEnrolments("IR-SA~UTR", saUtr.utr)
          .foldF(
            _ => addSaUserTypeToCache(NonFilerSelfAssessmentUser),
            response =>
              if (response.nonEmpty) {
                addSaUserTypeToCache(WrongCredentialsSelfAssessmentUser(saUtr))
              } else {
                addSaUserTypeToCache(NotEnrolledSelfAssessmentUser(saUtr))
              }
          )
    }

  def retrieveMTDEnrolment(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext) =
    enrolmentsConnector
      .getKnownFacts(nino)
      .fold(
        _ => None,
        _ match {
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
        groupDetails =>
          groupDetails match {
            case Some(userDetails) =>
              Future.successful(
                UsersAssignedEnrolment(
                  AccountDetails(
                    userDetails.identityProviderType,
                    id,
                    userDetails.obfuscatedUserId.getOrElse(""),
                    userDetails.email.map(SensitiveString),
                    userDetails.lastAccessedTimestamp,
                    AccountDetails.additionalFactorsToMFADetails(userDetails.additionalFactors),
                    None
                  )
                )
              )
            case None              => Future.successful(EnrolmentDoesNotExist())
          }
      )

  def checkEnrolmentStatus(key: String, value: String)(implicit hc: HeaderCarrier, executionContext: ExecutionContext) =
    for {
      userIds     <- checkEnrolmentId(key, value)
      accountInfo <- checkEnrolmentExists(userIds.head)
    } yield accountInfo

}
