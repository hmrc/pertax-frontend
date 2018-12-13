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
import models._
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services._
import services.partials.MessageFrontendService
import uk.gov.hmrc.domain.{Nino, SaUtr}
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
      .overrides(bind[SelfAssessmentService].toInstance(MockitoSugar.mock[SelfAssessmentService]))
      .overrides(bind[MessageFrontendService].toInstance(MockitoSugar.mock[MessageFrontendService]))
    .build()

    override def beforeEach: Unit = {
      reset(injected[LocalSessionCache], injected[CitizenDetailsService], injected[PertaxAuditConnector])
    }

    trait WithAmbiguousJourneyControllerSpecSetup {

      def nino: Nino
      def personDetailsResponse: PersonDetailsResponse
      def getSelfAssessmentServiceResponse: SelfAssessmentUserType
      def saSkipLetterPage: Boolean = false
      def saAmbigSimplifiedJourney: Boolean = false

      lazy val personDetails = Fixtures.buildPersonDetails

      lazy val controller = {
        val c = injected[AmbiguousJourneyController]

        when(c.selfAssessmentService.getSelfAssessmentUserType(any())(any())) thenReturn {
          Future.successful(getSelfAssessmentServiceResponse)
        }
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
        when(injected[MessageFrontendService].getUnreadMessageCount(any())) thenReturn {
          Future.successful(None)
        }
        when(c.configDecorator.ssoUrl) thenReturn Some("ssoUrl")
        when(c.configDecorator.getFeedbackSurveyUrl(any())) thenReturn "/test"
        when(c.configDecorator.analyticsToken) thenReturn Some("N/A")
        when(c.configDecorator.saAmbigSkipUTRLetterEnabled) thenReturn saSkipLetterPage
        when(c.configDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn saAmbigSimplifiedJourney

        c
      }
    }

  trait LocalSetupJourney extends WithAmbiguousJourneyControllerSpecSetup {
    override lazy val nino = Fixtures.fakeNino
    override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetails)
  }

  "Calling AmbiguousJourneyController.processFileReturnOnlineChoice" should {

    "redirect to 'Have you de-enrolled from self assessment' page when supplied with value Yes (true) and not on simplified journey" in new LocalSetupJourney {
      val r = controller.processFileReturnOnlineChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "true"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/de-enrolled-sa")
    }

    "redirect to 'Have you filed your tax return by post' page when supplied with value No (false) and not on simplified journey" in new LocalSetupJourney {
      val r = controller.processFileReturnOnlineChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/sa-filed-post")
    }

    "redirect to 'Have you used your utr to enrol' page when supplied with value No (false) and on simplified journey" in new LocalSetupJourney {
      val r = controller.processFileReturnOnlineChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false"))
      override lazy val saAmbigSimplifiedJourney = true
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/used-utr-to-enrol")
    }

    "return a bad request when supplied no value" in new LocalSetupJourney {
      val r = controller.processFileReturnOnlineChoice(buildFakeRequestWithAuth("POST"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe BAD_REQUEST
    }
  }

  "Calling AmbiguousJourneyController.processDeEnroledFromSaChoice" should {

    "redirect to 'You need to enrol' page when supplied with value Yes (true)" in new LocalSetupJourney {
      val r = controller.processDeEnroledFromSaChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "true"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/need-to-enrol-again")
    }

    "redirect to 'You need to use the creds you've created' page when supplied with value No (false)" in new LocalSetupJourney {
      val r = controller.processDeEnroledFromSaChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/need-to-use-created-creds")
    }

    "return a bad request when supplied no value" in new LocalSetupJourney {
      val r = controller.processDeEnroledFromSaChoice(buildFakeRequestWithAuth("POST"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe BAD_REQUEST
    }
  }

  "Calling AmbiguousJourneyController.processFiledReturnByPostChoice" should {

    "redirect to 'Have you used your utr to register' page when supplied with value Yes (true)" in new LocalSetupJourney {
      val r = controller.processFiledReturnByPostChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "true"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/used-utr-to-register")
    }

    "redirect to 'You need to use the creds you've created' page when supplied with value No (false) when skip sa page feature is set to false" in
      new LocalSetupJourney {
        val r = controller.processFiledReturnByPostChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false"))
        override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

        status(r) shouldBe SEE_OTHER
        redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/received-utr-letter")
    }

    "redirect to 'Have you used your utr to enrol' page when supplied with value No (false) when skip sa page feature is set to true" in
      new LocalSetupJourney {
        override val saSkipLetterPage = true
        val r = controller.processFiledReturnByPostChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false"))
        override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

        status(r) shouldBe SEE_OTHER
        redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/used-utr-to-enrol")
    }

    "return a bad request when supplied no value" in new LocalSetupJourney {
      val r = controller.processFiledReturnByPostChoice(buildFakeRequestWithAuth("POST"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe BAD_REQUEST
    }
  }

  "Calling AmbiguousJourneyController.processReceivedUtrLetterChoice" should {

    "redirect to 'Have you used your utr to enrol' page when supplied with value Yes (true)" in new LocalSetupJourney {
      val r = controller.processReceivedUtrLetterChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "true"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/used-utr-to-enrol")
    }

    "redirect to 'Your letter may still be in the post' page when supplied with value No (false)" in new LocalSetupJourney {
      val r = controller.processReceivedUtrLetterChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/letter-in-post")
    }

    "return a bad request when supplied no value" in new LocalSetupJourney {
      val r = controller.processReceivedUtrLetterChoice(buildFakeRequestWithAuth("POST"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe BAD_REQUEST
    }
  }

  "Calling AmbiguousJourneyController.processUsedUtrToEnrolChoice" should {

    "redirect to 'Your pin has expired' page when supplied with value Yes (true)" in new LocalSetupJourney {
      val r = controller.processUsedUtrToEnrolChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "true"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/pin-expired-enrol")
    }

    "redirect to 'You need to enrol for sa' page when supplied with value No (false)" in new LocalSetupJourney {
      val r = controller.processUsedUtrToEnrolChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/need-to-enrol")
    }

    "return a bad request when supplied no value" in new LocalSetupJourney {
      val r = controller.processUsedUtrToEnrolChoice(buildFakeRequestWithAuth("POST"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe BAD_REQUEST
    }
  }

  "Calling AmbiguousJourneyController.processUsedUtrToRegisterChoice" should {

    "redirect to 'Your pin has expired' page when supplied with value Yes (true)" in new LocalSetupJourney {
      val r = controller.processUsedUtrToRegisterChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "true"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/pin-expired-register")
    }

    "redirect to 'The deadline is' page when supplied with value No (false)" in new LocalSetupJourney {
      val r = controller.processUsedUtrToRegisterChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/deadline")
    }

    "return a bad request when supplied no value" in new LocalSetupJourney {
      val r = controller.processUsedUtrToRegisterChoice(buildFakeRequestWithAuth("POST"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe BAD_REQUEST
    }
  }

  "Calling AmbiguousJourneyController.filedReturnOnlineChoice" should {
    "return 200 when self assessment user type is AmbiguousFilerSelfAssessmentUser" in new LocalSetupJourney {
      val r = controller.filedReturnOnlineChoice(buildFakeRequestWithAuth("GET"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe OK
    }
  }

  "Calling AmbiguousJourneyController.deEnrolledFromSaChoice " should {
    "return 200 when AmbiguousJourneyController.deEnrolledFromSaChoice is called" in new LocalSetupJourney {
      val r = controller.deEnrolledFromSaChoice(buildFakeRequestWithAuth("GET"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe OK
    }
  }

  "Calling AmbiguousJourneyController.filedReturnByPostChoice" should {
    "return 200 when AmbiguousJourneyController.filedReturnByPostChoice is called" in new LocalSetupJourney {
      val r = controller.filedReturnByPostChoice(buildFakeRequestWithAuth("GET"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe OK
    }
  }

  "Calling AmbiguousJourneyController.receivedUtrLetterChoice" should {
    "return 200 when AmbiguousJourneyController.receivedUtrLetterChoice is called" in new LocalSetupJourney {
      val r = controller.receivedUtrLetterChoice(buildFakeRequestWithAuth("GET"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe OK
    }
  }

  "Calling AmbiguousJourneyController.usedUtrToEnrolChoice" should {
    "return 200 when AmbiguousJourneyController.usedUtrToEnrolChoice is called" in new LocalSetupJourney {
      val r = controller.usedUtrToEnrolChoice(buildFakeRequestWithAuth("GET"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe OK
    }
  }

  "Calling AmbiguousJourneyController.handleAmbiguousJourneyLandingPages" should {
    "return 200 when supplied with value of 'need-to-enrol' and SA user type is AmbiguousFilerSelfAssessmentUser" in new LocalSetupJourney {
      val page = "need-to-enrol"
      val r = controller.handleAmbiguousJourneyLandingPages(page)(buildFakeRequestWithAuth("GET"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe 200
    }

    "return 200 when supplied with value of 'need-to-enrol-again' and SA user type is AmbiguousFilerSelfAssessmentUser" in new LocalSetupJourney {
      val page = "need-to-enrol-again"
      val r = controller.handleAmbiguousJourneyLandingPages(page)(buildFakeRequestWithAuth("GET"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe 200
    }

    "return 200 when supplied with value of 'need-to-use-created-creds' and SA user type is AmbiguousFilerSelfAssessmentUser" in new LocalSetupJourney {
      val page = "need-to-use-created-creds"
      val r = controller.handleAmbiguousJourneyLandingPages(page)(buildFakeRequestWithAuth("GET"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe 200
    }

    "return 200 when supplied with value of 'deadline' and SA user type is AmbiguousFilerSelfAssessmentUser" in new LocalSetupJourney {
      val page = "deadline"
      val r = controller.handleAmbiguousJourneyLandingPages(page)(buildFakeRequestWithAuth("GET"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe 200
    }

    "return 200 when supplied with value of 'letter-in-post' and SA user type is AmbiguousFilerSelfAssessmentUser" in new LocalSetupJourney {
      val page = "letter-in-post"
      val r = controller.handleAmbiguousJourneyLandingPages(page)(buildFakeRequestWithAuth("GET"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe 200
    }

    "return 200 when supplied with value of 'pin-expired' and SA user type is AmbiguousFilerSelfAssessmentUser" in new LocalSetupJourney {
      val page = "pin-expired"
      val r = controller.handleAmbiguousJourneyLandingPages(page)(buildFakeRequestWithAuth("GET"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe 200
    }
  }

  "Calling AmbiguousJourneyController.enforceAmbiguousUser" should {

    trait AmbiguousUserEnforcerSetup extends LocalSetupJourney {
      implicit lazy val context = PertaxContext(FakeRequest(), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(PertaxUser(buildFakeAuthContext(),
        UserDetails(UserDetails.GovernmentGatewayAuthProvider),
        None, true)))

      implicit lazy val messages = Messages(Lang("en"), injected[MessagesApi])

      val result = controller.deEnrolledFromSaChoice(buildFakeRequestWithAuth("GET"))

      val block = { _: SaUtr => result }
    }

    "return 200 when SA user type is AmbiguousFilerSelfAssessmentUser" in new AmbiguousUserEnforcerSetup {
      val r = controller.enforceAmbiguousUser(block)
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe 200
    }

    "redirect to 'personal-account' when SA user type is ActivatedOnlineFilerSelfAssessmentUser" in new AmbiguousUserEnforcerSetup {
      val r = controller.enforceAmbiguousUser(block)
      override lazy val getSelfAssessmentServiceResponse = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))

      redirectLocation(await(r)) shouldBe Some("/personal-account")
    }

    "redirect to 'personal-account' when SA user type is NotYetActivatedOnlineFilerSelfAssessmentUser" in new AmbiguousUserEnforcerSetup {
      val r = controller.enforceAmbiguousUser(block)
      override lazy val getSelfAssessmentServiceResponse = NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))

      redirectLocation(await(r)) shouldBe Some("/personal-account")
    }

    "redirect to 'personal-account' when SA user type is NonFilerSelfAssessmentUser" in new AmbiguousUserEnforcerSetup {
      val r = controller.enforceAmbiguousUser(block)
      override lazy val getSelfAssessmentServiceResponse = NonFilerSelfAssessmentUser

      redirectLocation(await(r)) shouldBe Some("/personal-account")
    }
  }
}
