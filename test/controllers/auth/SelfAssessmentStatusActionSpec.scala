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

package controllers.auth

import cats.data.EitherT
import controllers.auth.requests._
import models._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results.Ok
import play.api.mvc.{AnyContent, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{CitizenDetailsService, EnrolmentStoreCachingService}
import testUtils.BaseSpec
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.{AffinityGroup, ConfidenceLevel, Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.domain.{Nino, SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.concurrent.Future

class SelfAssessmentStatusActionSpec extends BaseSpec {
  val mockCitizenDetailsService: CitizenDetailsService = mock[CitizenDetailsService]
  private val saUtr                                    = SaUtr(new SaUtrGenerator().nextSaUtr.utr)
  private val enrolmentsCachingService                 = mock[EnrolmentStoreCachingService]

  override implicit lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[CitizenDetailsService].toInstance(mockCitizenDetailsService))
    .overrides(bind[EnrolmentStoreCachingService].toInstance(enrolmentsCachingService))
    .configure(Map("metrics.enabled" -> false))
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockCitizenDetailsService)
  }

  def harness[A]()(implicit request: AuthenticatedRequest[A]): Future[Result] = {

    lazy val actionProvider = app.injector.instanceOf[SelfAssessmentStatusAction]

    actionProvider.invokeBlock(
      request,
      (userRequest: UserRequest[_]) =>
        Future.successful(
          Ok(s"Nino: ${userRequest.authNino.nino}, SaUtr: ${userRequest.saUserType.toString}")
        )
    )

  }

  def createAuthenticatedRequest(
    enrolments: Set[Enrolment] = Set.empty
  ): AuthenticatedRequest[AnyContent] =
    AuthenticatedRequest(
      Nino("AB123456D"),
      Credentials("", "GovernmentGateway"),
      ConfidenceLevel.L200,
      None,
      None,
      enrolments,
      FakeRequest(),
      Some(AffinityGroup.Agent),
      UserAnswers.empty
    )

  "An SA user with an activated enrolment must" must {
    "return ActivatedOnlineFilerSelfAssessmentUser" in {
      val saEnrolment                                        =
        Enrolment("IR-SA", identifiers = Seq(EnrolmentIdentifier("UTR", saUtr.utr)), state = "Activated")
      implicit val request: AuthenticatedRequest[AnyContent] = createAuthenticatedRequest(Set(saEnrolment))

      val result = harness()(request)
      contentAsString(result) must include(s"ActivatedOnlineFilerSelfAssessmentUser(${saUtr.utr})")
      verify(mockCitizenDetailsService, times(0)).getSaUtrFromMatchingDetails(any())(any(), any())
    }
  }

  "An SA user with a not yet activated enrolment must" must {
    "return NotYetActivatedOnlineFilerSelfAssessmentUser" in {
      val saEnrolment                                        =
        Enrolment("IR-SA", identifiers = Seq(EnrolmentIdentifier("UTR", saUtr.utr)), state = "NotYetActivated")
      implicit val request: AuthenticatedRequest[AnyContent] = createAuthenticatedRequest(Set(saEnrolment))

      val result = harness()(request)
      contentAsString(result) must include(s"NotYetActivatedOnlineFilerSelfAssessmentUser(${saUtr.utr})")
      verify(mockCitizenDetailsService, times(0)).getSaUtrFromMatchingDetails(any())(any(), any())
    }
  }

  "A user without an SA enrolment" when {
    "CitizenDetails has a matching SA account" must {

      val saUtr = SaUtr(new SaUtrGenerator().nextSaUtr.utr)

      val userTypeList: List[(SelfAssessmentUserType, String)] = List(
        (WrongCredentialsSelfAssessmentUser(saUtr), "a Wrong credentials SA user"),
        (NotEnrolledSelfAssessmentUser(saUtr), "a Not Enrolled SA user"),
        (NonFilerSelfAssessmentUser, "a Non Filer SA user")
      )

      implicit val request: AuthenticatedRequest[AnyContent] = createAuthenticatedRequest(Set.empty)

      userTypeList.foreach { case (userType, key) =>
        s"return $key when the enrolments caching service returns ${userType.toString}" in {

          when(mockCitizenDetailsService.getSaUtrFromMatchingDetails(any())(any(), any()))
            .thenReturn(
              EitherT.rightT[Future, UpstreamErrorResponse](Some(saUtr))
            )

          when(enrolmentsCachingService.getSaUserTypeFromCache(any())(any(), any()))
            .thenReturn(Future.successful(userType))

          val result = harness()(request)
          contentAsString(result) must include(s"${userType.toString}")
          verify(mockCitizenDetailsService, times(1)).getSaUtrFromMatchingDetails(any())(any(), any())
        }
      }
    }
  }

  "return NonFilerSelfAssessmentUser" when {
    "getSaUtrFromMatchingDetails returns None" in {
      implicit val request: AuthenticatedRequest[AnyContent] = createAuthenticatedRequest()

      when(mockCitizenDetailsService.getSaUtrFromMatchingDetails(any())(any(), any()))
        .thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](None)
        )

      val result = harness()(request)
      contentAsString(result) must include("NonFilerSelfAssessmentUser")
      verify(mockCitizenDetailsService, times(1)).getSaUtrFromMatchingDetails(any())(any(), any())
    }
  }
}
