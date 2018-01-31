/*
 * Copyright 2018 HM Revenue & Customs
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

package controllers

import config.ConfigDecorator
import connectors.{FrontEndDelegationConnector, PertaxAuditConnector, PertaxAuthConnector}
import models.UserDetails
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.inject.bind
import play.api.test.Helpers._
import services._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel
import util.Fixtures._
import util.{BaseSpec, Fixtures, LocalPartialRetriever}

import scala.concurrent.Future

class AmbiguousJourneyControllerSpec extends BaseSpec {
  override implicit lazy val app: Application = localGuiceApplicationBuilder
      .overrides(bind[CitizenDetailsService].toInstance(MockitoSugar.mock[CitizenDetailsService]))
      .overrides(bind[UserDetailsService].toInstance(MockitoSugar.mock[UserDetailsService]))
      .overrides(bind[FrontEndDelegationConnector].toInstance(MockitoSugar.mock[FrontEndDelegationConnector]))
      .overrides(bind[LocalSessionCache].toInstance(MockitoSugar.mock[LocalSessionCache]))
      .overrides(bind[PertaxAuditConnector].toInstance(MockitoSugar.mock[PertaxAuditConnector]))
      .overrides(bind[PertaxAuthConnector].toInstance(MockitoSugar.mock[PertaxAuthConnector]))
      .overrides(bind[LocalPartialRetriever].toInstance(MockitoSugar.mock[LocalPartialRetriever]))
      .overrides(bind[ConfigDecorator].toInstance(MockitoSugar.mock[ConfigDecorator]))
      .build()


    override def beforeEach: Unit = {
      reset(injected[LocalSessionCache], injected[CitizenDetailsService], injected[PertaxAuditConnector])
    }

    trait WithAmbiguousJourneyControllerSpecSetup {

      def nino: Nino
      def personDetailsResponse: PersonDetailsResponse

      lazy val personDetails = Fixtures.buildPersonDetails

      lazy val controller = {
        val c = injected[AmbiguousJourneyController]

        when(injected[PertaxAuditConnector].sendEvent(any())(any(), any())) thenReturn {
          Future.successful(AuditResult.Success)
        }
        when(injected[PertaxAuthConnector].currentAuthority(any(), any())) thenReturn {
          Future.successful(Some(buildFakeAuthority(confidenceLevel = ConfidenceLevel.L200)))
        }
        when(injected[CitizenDetailsService].personDetails(meq(nino))(any())) thenReturn {
          Future.successful(personDetailsResponse)
        }
        when(injected[UserDetailsService].getUserDetails(any())(any())) thenReturn {
          Future.successful(Some(UserDetails(UserDetails.GovernmentGatewayAuthProvider)))
        }
        when(c.configDecorator.ssoUrl) thenReturn Some("ssoUrl")
        when(c.configDecorator.getFeedbackSurveyUrl(any())) thenReturn "/test"
        when(c.configDecorator.analyticsToken) thenReturn Some("N/A")

        c
      }
    }

  trait LocalSetupJourney extends WithAmbiguousJourneyControllerSpecSetup {
    override lazy val nino = Fixtures.fakeNino
    override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetails)

  }

  "Calling AmbiguousJourneyController.processFileReturnOnlineChoice" should {

    "redirect to 'Have you de-enrolled from self assessment' page when supplied with value Yes (true)" in new LocalSetupJourney {
      val r = controller.processFileReturnOnlineChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("value" -> "true"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/de-enrolled-sa")
    }

    "redirect to 'Have you filed your tax return by post' page when supplied with value No (false)" in new LocalSetupJourney {
      val r = controller.processFileReturnOnlineChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("value" -> "false"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/sa-filed-post")
    }

    "return a bad request when supplied no value" in new LocalSetupJourney {
      val r = controller.processFileReturnOnlineChoice(buildFakeRequestWithAuth("POST"))

      status(r) shouldBe BAD_REQUEST
    }

  }

  "Calling AmbiguousJourneyController.processDeEnroleedFromSaChoice" should {

    "redirect to 'You need to enrol' page when supplied with value Yes (true)" in new LocalSetupJourney {
      val r = controller.processDeEnroleedFromSaChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("value" -> "true"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/need-to-enrol-again")
    }

    "redirect to 'You need to use the creds you've created' page when supplied with value No (false)" in new LocalSetupJourney {
      val r = controller.processDeEnroleedFromSaChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("value" -> "false"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/need-to-use-created-creds")
    }

    "return a bad request when supplied no value" in new LocalSetupJourney {
      val r = controller.processDeEnroleedFromSaChoice(buildFakeRequestWithAuth("POST"))

      status(r) shouldBe BAD_REQUEST
    }

  }

  "Calling AmbiguousJourneyController.processFiledReturnByPostChoice" should {

    "redirect to 'Have you used your utr to register' page when supplied with value Yes (true)" in new LocalSetupJourney {
      val r = controller.processFiledReturnByPostChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("value" -> "true"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/used-utr-to-register")
    }

    "redirect to 'You need to use the creds you've created' page when supplied with value No (false)" in new LocalSetupJourney {
      val r = controller.processFiledReturnByPostChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("value" -> "false"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/received-utr-letter")
    }

    "return a bad request when supplied no value" in new LocalSetupJourney {
      val r = controller.processFiledReturnByPostChoice(buildFakeRequestWithAuth("POST"))

      status(r) shouldBe BAD_REQUEST
    }
  }

  "Calling AmbiguousJourneyController.processReceivedUtrLetterChoice" should {

    "redirect to 'Have you used your utr to enrol' page when supplied with value Yes (true)" in new LocalSetupJourney {
      val r = controller.processReceivedUtrLetterChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("value" -> "true"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/used-utr-to-enrol")
    }

    "redirect to 'Your letter may still be in the post' page when supplied with value No (false)" in new LocalSetupJourney {
      val r = controller.processReceivedUtrLetterChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("value" -> "false"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/letter-in-post")
    }

    "return a bad request when supplied no value" in new LocalSetupJourney {
      val r = controller.processReceivedUtrLetterChoice(buildFakeRequestWithAuth("POST"))

      status(r) shouldBe BAD_REQUEST
    }
  }

  "Calling AmbiguousJourneyController.processUsedUtrToEnrolChoice" should {

    "redirect to 'Your pin has expired' page when supplied with value Yes (true)" in new LocalSetupJourney {
      val r = controller.processUsedUtrToEnrolChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("value" -> "true"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/pin-expired")
    }

    "redirect to 'You need to enrol for sa' page when supplied with value No (false)" in new LocalSetupJourney {
      val r = controller.processUsedUtrToEnrolChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("value" -> "false"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/need-to-enrol")
    }

    "return a bad request when supplied no value" in new LocalSetupJourney {
      val r = controller.processUsedUtrToEnrolChoice(buildFakeRequestWithAuth("POST"))

      status(r) shouldBe BAD_REQUEST
    }
  }

  "Calling AmbiguousJourneyController.processUsedUtrToRegisterChoice" should {

    "redirect to 'Your pin has expired' page when supplied with value Yes (true)" in new LocalSetupJourney {
      val r = controller.processUsedUtrToRegisterChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("value" -> "true"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/pin-expired")
    }

    "redirect to 'The deadline is' page when supplied with value No (false)" in new LocalSetupJourney {
      val r = controller.processUsedUtrToRegisterChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("value" -> "false"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/deadline")
    }

    "return a bad request when supplied no value" in new LocalSetupJourney {
      val r = controller.processUsedUtrToRegisterChoice(buildFakeRequestWithAuth("POST"))

      status(r) shouldBe BAD_REQUEST
    }
  }

  "Calling AmbiguousJourneyController" should {
    "return 200 when AmbiguousJourneyController.filedReturnOnlineChoice is called" in new LocalSetupJourney {
      val r = controller.filedReturnOnlineChoice(buildFakeRequestWithAuth("GET"))

      status(r) shouldBe OK
    }

    "return 200 when AmbiguousJourneyController.deEnrolledFromSaChoice is called" in new LocalSetupJourney {
      val r = controller.deEnrolledFromSaChoice(buildFakeRequestWithAuth("GET"))

      status(r) shouldBe OK
    }

    "return 200 when AmbiguousJourneyController.filedReturnByPostChoice is called" in new LocalSetupJourney {
      val r = controller.filedReturnByPostChoice(buildFakeRequestWithAuth("GET"))

      status(r) shouldBe OK
    }

    "return 200 when AmbiguousJourneyController.receivedUtrLetterChoice is called" in new LocalSetupJourney {
      val r = controller.receivedUtrLetterChoice(buildFakeRequestWithAuth("GET"))

      status(r) shouldBe OK
    }

    "return 200 when AmbiguousJourneyController.usedUtrToEnrolChoice is called" in new LocalSetupJourney {
      val r = controller.usedUtrToEnrolChoice(buildFakeRequestWithAuth("GET"))

      status(r) shouldBe OK
    }
  }
}