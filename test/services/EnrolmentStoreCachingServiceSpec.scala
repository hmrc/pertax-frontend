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

import cats.data.EitherT
import connectors.EnrolmentsConnector
import controllers.auth.requests.{AuthenticatedRequest, UserRequest}
import models.*
import models.MtdUserType.*
import models.enrolments.{EACDEnrolment, IdentifiersOrVerifiers, KnownFactsResponse}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.BaseSpec
import uk.gov.hmrc.auth.core.ConfidenceLevel.L200
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.concurrent.Future

class EnrolmentStoreCachingServiceSpec extends BaseSpec {

  val mockEnrolmentsConnector: EnrolmentsConnector = mock[EnrolmentsConnector]
  val saUtr: SaUtr                                 = SaUtr(new SaUtrGenerator().nextSaUtr.utr)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockEnrolmentsConnector)
  }

  implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type]                                = FakeRequest()
  def userRequest(enrolments: Set[Enrolment] = Set.empty): UserRequest[AnyContentAsEmpty.type] = UserRequest(
    AuthenticatedRequest(
      generatedNino,
      Credentials("credId", "provider id"),
      L200,
      None,
      None,
      enrolments,
      fakeRequest,
      None,
      UserAnswers("id")
    ),
    NonFilerSelfAssessmentUser,
    None
  )
  implicit val defaultUserRequest: UserRequest[AnyContentAsEmpty.type]                         = userRequest()

  trait LocalSetup {

    lazy val sut: EnrolmentStoreProxyService =
      new EnrolmentStoreProxyService(
        mockEnrolmentsConnector
      )
  }

  "getMtdUserType" when {

    "return NonFilerMtdUser" in new LocalSetup {
      when(mockEnrolmentsConnector.getKnownFacts(any())(any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](KnownFactsResponse("service", List.empty))
      )

      val result = sut.getMtdUserType(generatedNino).value.futureValue
      result mustBe Right(NonFilerMtdUser)

      verify(mockEnrolmentsConnector, times(0)).getUserIdsWithEnrolments(any())(any(), any())
    }

    "return NotEnrolledMtdUser" in new LocalSetup {
      when(mockEnrolmentsConnector.getKnownFacts(any())(any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](
          KnownFactsResponse(
            "service",
            List(
              EACDEnrolment(identifiers = List(IdentifiersOrVerifiers("MTDITID", "mtditid")), verifiers = List.empty)
            )
          )
        )
      )
      when(mockEnrolmentsConnector.getUserIdsWithEnrolments(any())(any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](Seq.empty)
      )

      val result = sut.getMtdUserType(generatedNino).value.futureValue
      result mustBe Right(NotEnrolledMtdUser)

      verify(mockEnrolmentsConnector, times(1)).getUserIdsWithEnrolments(any())(any(), any())
    }

    "return WrongCredentialsMtdUser" in new LocalSetup {
      when(mockEnrolmentsConnector.getKnownFacts(any())(any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](
          KnownFactsResponse(
            "service",
            List(
              EACDEnrolment(identifiers = List(IdentifiersOrVerifiers("MTDITID", "mtditid")), verifiers = List.empty)
            )
          )
        )
      )
      when(mockEnrolmentsConnector.getUserIdsWithEnrolments(any())(any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](Seq("wrongCredId"))
      )

      val result = sut.getMtdUserType(generatedNino).value.futureValue
      result mustBe Right(WrongCredentialsMtdUser("mtditid", "wrongCredId"))

      verify(mockEnrolmentsConnector, times(1)).getUserIdsWithEnrolments(any())(any(), any())
    }

    "return UnknownMtdUser for multiple enrolments" in new LocalSetup {
      when(mockEnrolmentsConnector.getKnownFacts(any())(any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](
          KnownFactsResponse(
            "service",
            List(
              EACDEnrolment(identifiers = List(IdentifiersOrVerifiers("MTDITID", "mtditid")), verifiers = List.empty),
              EACDEnrolment(identifiers = List(IdentifiersOrVerifiers("MTDITID", "mtditid2")), verifiers = List.empty)
            )
          )
        )
      )
      when(mockEnrolmentsConnector.getUserIdsWithEnrolments(any())(any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](Seq("wrongCredId"))
      )

      val result = sut.getMtdUserType(generatedNino).value.futureValue
      result mustBe Right(UnknownMtdUser)

      verify(mockEnrolmentsConnector, times(0)).getUserIdsWithEnrolments(any())(any(), any())
    }

    "return UnknownMtdUser for multiple creds" in new LocalSetup {
      when(mockEnrolmentsConnector.getKnownFacts(any())(any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](
          KnownFactsResponse(
            "service",
            List(
              EACDEnrolment(identifiers = List(IdentifiersOrVerifiers("MTDITID", "mtditid")), verifiers = List.empty)
            )
          )
        )
      )
      when(mockEnrolmentsConnector.getUserIdsWithEnrolments(any())(any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](Seq("credId", "wrongCredId"))
      )

      val result = sut.getMtdUserType(generatedNino).value.futureValue
      result mustBe Right(UnknownMtdUser)

      verify(mockEnrolmentsConnector, times(1)).getUserIdsWithEnrolments(any())(any(), any())
    }

    "return EnrolledMtdUser" in new LocalSetup {
      when(mockEnrolmentsConnector.getKnownFacts(any())(any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](
          KnownFactsResponse(
            "service",
            List(
              EACDEnrolment(identifiers = List(IdentifiersOrVerifiers("MTDITID", "mtditid")), verifiers = List.empty)
            )
          )
        )
      )
      when(mockEnrolmentsConnector.getUserIdsWithEnrolments(any())(any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](Seq("credId"))
      )

      val result = sut
        .getMtdUserType(generatedNino)(
          implicitly,
          userRequest(Set(Enrolment("HMRC-MTD-IT", Seq(EnrolmentIdentifier("MTDITID", "mtditid")), "activated", None)))
        )
        .value
        .futureValue
      result mustBe Right(EnrolledMtdUser("mtditid"))

      verify(mockEnrolmentsConnector, times(1)).getUserIdsWithEnrolments(any())(any(), any())
    }

  }

  "findCredentialsWithIrSaForUtr" when {}
}
