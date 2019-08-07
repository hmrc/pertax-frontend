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

import java.net.URL

import javax.inject.{Inject, Singleton}
import com.kenshoo.play.metrics.Metrics
import metrics.HasMetrics
import models._
import play.api.{Configuration, Environment, Logger}
import play.api.Mode.Mode
import services.http.SimpleHttp
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent._
import uk.gov.hmrc.http.HeaderCarrier
@Singleton
class SelfAssessmentService @Inject()(
  environment: Environment,
  configuration: Configuration,
  val simpleHttp: SimpleHttp,
  val citizenDetailsService: CitizenDetailsService,
  val metrics: Metrics)
    extends ServicesConfig with HasMetrics {

  val mode: Mode = environment.mode
  val runModeConfiguration: Configuration = configuration
  lazy val authUrl = new URL(baseUrl("auth"))

  def getSelfAssessmentUserType(authContext: Option[AuthContext])(
    implicit hc: HeaderCarrier): Future[SelfAssessmentUserType] = {

    import AuthEnrolment._

    val context = new AuthContextDecorator(authContext)

    def getSaUtrIfUserHasNotYetActivatedSaEnrolment(implicit hc: HeaderCarrier): Future[Option[SaUtr]] =
      context.enrolmentUri.map { u =>
        withMetricsTimer("get-auth-enrolments") { t =>
          simpleHttp.get[Option[SaUtr]](new URL(authUrl, u).toString)(
            onComplete = {
              case r if r.status >= 200 && r.status < 300 =>
                t.completeTimerAndIncrementSuccessCounter()
                r.json.asOpt[AuthEnrolment] match {
                  case Some(AuthEnrolment(NotYetActivated, saUtr)) => Some(saUtr)
                  case _                                           => None
                }

              case r =>
                t.completeTimerAndIncrementFailedCounter()
                Logger.warn(s"Unexpected ${r.status} response getting SA enrolments from the self-assessment service")
                None
            },
            onError = {
              case e =>
                t.completeTimerAndIncrementFailedCounter()
                Logger.warn("Error getting SA enrolments from the self-assessment service", e)
                None
            }
          )
        }
      } getOrElse Future.successful(None)

    def getSaUtrFromCitizenDetailsService(nino: Nino)(implicit hc: HeaderCarrier): Future[Option[SaUtr]] =
      citizenDetailsService.getMatchingDetails(nino) map {
        case MatchingDetailsSuccessResponse(matchingDetails) => matchingDetails.saUtr
        case _                                               => None
      }

    context.nino.fold[Future[SelfAssessmentUserType]](Future.successful(NonFilerSelfAssessmentUser)) { nino =>
      context.saUtr.fold({
        getSaUtrIfUserHasNotYetActivatedSaEnrolment flatMap {
          case Some(saUtr) =>
            Future.successful(NotYetActivatedOnlineFilerSelfAssessmentUser(saUtr)) //NotYetActivated online filer
          case None => {
            getSaUtrFromCitizenDetailsService(nino) map {
              case Some(saUtr) => AmbiguousFilerSelfAssessmentUser(saUtr) //Ambiguous SA filer
              case None        => NonFilerSelfAssessmentUser //Non SA Filer
            }
          }
        }
      })({ saUtr =>
        Future.successful(ActivatedOnlineFilerSelfAssessmentUser(saUtr)) //Activated online filer
      })

    }
  }
}
