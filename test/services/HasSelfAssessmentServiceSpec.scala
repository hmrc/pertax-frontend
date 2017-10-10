/*
 * Copyright 2017 HM Revenue & Customs
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

import com.codahale.metrics.Timer
import com.kenshoo.play.metrics.Metrics
import config.ConfigDecorator
import models._
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import services.http.FakeSimpleHttp
import uk.gov.hmrc.domain.SaUtr
import util.BaseSpec
import util.Fixtures._

import scala.concurrent.Future
import uk.gov.hmrc.http.HttpResponse

class HasSelfAssessmentServiceSpec extends BaseSpec {

  trait LocalSetup {
    def saUser: Boolean
    def getAuthEnrolmentHttpResponse: HttpResponse
    def getMatchingDetailsResponse: MatchingDetailsResponse
    def simulateErrorCallingSelfAssessmentService: Boolean

    def authEnrolmentJson(state: String, utr: String) = {
      Json.arr(Json.obj(
        "key" -> "IR-SA",
        "identifiers" -> Json.arr(Json.obj("key" -> "UTR", "value" -> utr)),
        "state" -> state
      ))
    }

    lazy val context = PertaxContext(FakeRequest(), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(PertaxUser(buildFakeAuthContext(withSa = saUser),
      UserDetails(UserDetails.GovernmentGatewayAuthProvider),
      None, true)))

    val anException = new RuntimeException("Any")

    lazy val service = {

      val fakeSimpleHttp = {
        if(simulateErrorCallingSelfAssessmentService) new FakeSimpleHttp(Right(anException))
        else new FakeSimpleHttp(Left(getAuthEnrolmentHttpResponse))
      }

      val citizenDetailsService = MockitoSugar.mock[CitizenDetailsService]
      when(citizenDetailsService.getMatchingDetails(any())(any())) thenReturn Future.successful(getMatchingDetailsResponse)

      val timer = MockitoSugar.mock[Timer.Context]
      new SelfAssessmentService(fakeSimpleHttp, citizenDetailsService, MockitoSugar.mock[Metrics]) {
        override val metricsOperator: MetricsOperator = MockitoSugar.mock[MetricsOperator]
        when(metricsOperator.startTimer(any())) thenReturn timer
      }

    }

    lazy val saActionNeeded = service.getSelfAssessmentUserType(context.authContext)
  }

  "Calling HasSelfAssessmentServiceSpec.getSelfAssessmentActionNeeded" should {

    "Return FileReturnSelfAssessmentActionNeeded when called with a SA user" in new LocalSetup {
      override lazy val saUser = true
      override lazy val getMatchingDetailsResponse = MatchingDetailsSuccessResponse(MatchingDetails(Some(SaUtr(("1111111111")))))
      override lazy val simulateErrorCallingSelfAssessmentService = false
      override lazy val getAuthEnrolmentHttpResponse = HttpResponse(OK, Some(authEnrolmentJson("Activated", "1111111111")))

      await(saActionNeeded) shouldBe ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      verify(service.citizenDetailsService, times(0)).getMatchingDetails(any())(any())
    }

    "Return ActivateSelfAssessmentActionNeeded when the user does not have an active SA enrolment but does have a NotYetActivated enrolment" in new LocalSetup {
      override lazy val saUser = false
      override lazy val getMatchingDetailsResponse = MatchingDetailsSuccessResponse(MatchingDetails(Some(SaUtr("1111111111"))))
      override lazy val simulateErrorCallingSelfAssessmentService = false
      override lazy val getAuthEnrolmentHttpResponse = HttpResponse(OK, Some(authEnrolmentJson("NotYetActivated", "1111111111")))

      await(saActionNeeded) shouldBe NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      verify(service.citizenDetailsService, times(0)).getMatchingDetails(any())(any())
    }

    "Return WrongAccountSelfAssessmentActionNeeded when the user does not have an active SA nor an NotYetActivated enrolment but does have a matching record with a saUtr" in new LocalSetup {
      override lazy val saUser = false
      override lazy val getMatchingDetailsResponse = MatchingDetailsSuccessResponse(MatchingDetails(Some(SaUtr("1111111111"))))
      override lazy val simulateErrorCallingSelfAssessmentService = false
      override lazy val getAuthEnrolmentHttpResponse = HttpResponse(OK, Some(authEnrolmentJson("AnythingButNotYetActivated", "1111111111")))  //Simulate no sa enrolment

      await(saActionNeeded) shouldBe AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))
      verify(service.citizenDetailsService, times(1)).getMatchingDetails(any())(any())
    }

    "Return NoSelfAssessmentActionNeeded when the user does not have an active SA nor an NotYetActivated enrolment and no matching record with a saUtr " in new LocalSetup {
      override lazy val saUser = false
      override lazy val getMatchingDetailsResponse = MatchingDetailsSuccessResponse(MatchingDetails(None))
      override lazy val simulateErrorCallingSelfAssessmentService = false
      override lazy val getAuthEnrolmentHttpResponse = HttpResponse(OK, Some(authEnrolmentJson("AnythingButNotYetActivated", "1111111111")))  //Simulate no sa enrolment

      await(saActionNeeded) shouldBe NonFilerSelfAssessmentUser
      verify(service.citizenDetailsService, times(1)).getMatchingDetails(any())(any())
    }
  }
}
