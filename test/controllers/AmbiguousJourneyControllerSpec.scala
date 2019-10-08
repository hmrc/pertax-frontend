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

package controllers

import config.ConfigDecorator
import connectors.{FrontEndDelegationConnector, PertaxAuditConnector, PertaxAuthConnector}
import controllers.auth.requests.UserRequest
import models._
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.i18n.{Messages, MessagesApi}
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services._
import services.partials.MessageFrontendService
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import util.Fixtures._
import util.{BaseSpec, Fixtures, LocalPartialRetriever, TaxYearRetriever}
import views.html.ViewSpec

import scala.concurrent.Future

class AmbiguousJourneyControllerSpec extends BaseSpec with ViewSpec with MockitoSugar {

  implicit lazy val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]

  lazy val fakeRequest = FakeRequest("", "")
  lazy val userRequest = UserRequest(
    None,
    None,
    None,
    AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111")),
    "SomeAuth",
    ConfidenceLevel.L200,
    None,
    None,
    None,
    None,
    fakeRequest)
  override val messages: Messages = messagesApi.preferred(fakeRequest)

  lazy val mockTaxYearRetriever = mock[TaxYearRetriever]

  val mockConfigDecorator = mock[ConfigDecorator]

  override implicit lazy val app: Application = localGuiceApplicationBuilder
    .overrides(
      bind[CitizenDetailsService].toInstance(mock[CitizenDetailsService]),
      bind[UserDetailsService].toInstance(mock[UserDetailsService]),
      bind[FrontEndDelegationConnector].toInstance(mock[FrontEndDelegationConnector]),
      bind[LocalSessionCache].toInstance(mock[LocalSessionCache]),
      bind[PertaxAuditConnector].toInstance(mock[PertaxAuditConnector]),
      bind[PertaxAuthConnector].toInstance(mock[PertaxAuthConnector]),
      bind[LocalPartialRetriever].toInstance(mock[LocalPartialRetriever]),
      bind[ConfigDecorator].toInstance(mockConfigDecorator),
      bind[SelfAssessmentService].toInstance(mock[SelfAssessmentService]),
      bind[MessageFrontendService].toInstance(mock[MessageFrontendService]),
      bind[TaxYearRetriever].toInstance(mockTaxYearRetriever)
    )
    .build()

  override def beforeEach: Unit =
    reset(injected[LocalSessionCache], injected[CitizenDetailsService], injected[PertaxAuditConnector])

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
      when(injected[CitizenDetailsService].personDetails(meq(nino))(any())) thenReturn {
        Future.successful(personDetailsResponse)
      }
      when(injected[UserDetailsService].getUserDetails(any())(any())) thenReturn {
        Future.successful(Some(UserDetails(UserDetails.GovernmentGatewayAuthProvider)))
      }
      when(injected[MessageFrontendService].getUnreadMessageCount(any())) thenReturn {
        Future.successful(None)
      }
      when(mockConfigDecorator.ssoUrl) thenReturn Some("ssoUrl")
      when(mockConfigDecorator.getFeedbackSurveyUrl(any())) thenReturn "/test"
      when(mockConfigDecorator.analyticsToken) thenReturn Some("N/A")
      when(mockConfigDecorator.saAmbigSkipUTRLetterEnabled) thenReturn saSkipLetterPage
      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn saAmbigSimplifiedJourney

      c
    }
  }

  trait LocalSetupJourney extends WithAmbiguousJourneyControllerSpecSetup {
    override lazy val nino = Fixtures.fakeNino
    override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetails)
  }

  "Calling AmbiguousJourneyController.processFileReturnOnlineChoice" should {

    "redirect to 'Have you de-enrolled from self assessment' page when supplied with value Yes (true) and not on simplified journey" in new LocalSetupJourney {
      val r = controller.processFileReturnOnlineChoice(
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "true"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/de-enrolled-sa")
    }

    "redirect to 'Have you filed your tax return by post' page when supplied with value No (false) and not on simplified journey" in new LocalSetupJourney {
      val r = controller.processFileReturnOnlineChoice(
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/sa-filed-post")
    }

    "redirect to 'Have you used your utr to enrol' page when supplied with value No (false) and on simplified journey" in new LocalSetupJourney {
      val r = controller.processFileReturnOnlineChoice(
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false"))
      override lazy val saAmbigSimplifiedJourney = true
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/have-you-enrolled")
    }

    "return a bad request when supplied no value" in new LocalSetupJourney {
      val r = controller.processFileReturnOnlineChoice(buildFakeRequestWithAuth("POST"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe BAD_REQUEST
    }
  }

  "Calling AmbiguousJourneyController.processDeEnroledFromSaChoice" should {

    "redirect to 'You need to enrol' page when supplied with value Yes (true)" in new LocalSetupJourney {
      val r = controller.processDeEnroledFromSaChoice(
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "true"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/need-to-enrol-again")
    }

    "redirect to 'You need to use the creds you've created' page when supplied with value No (false)" in new LocalSetupJourney {
      val r = controller.processDeEnroledFromSaChoice(
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false"))
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
      val r = controller.processFiledReturnByPostChoice(
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "true"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/used-utr-to-register")
    }

    "redirect to 'You need to use the creds you've created' page when supplied with value No (false) when skip sa page feature is set to false" in
      new LocalSetupJourney {
        val r = controller.processFiledReturnByPostChoice(
          buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false"))
        override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

        status(r) shouldBe SEE_OTHER
        redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/received-utr-letter")
      }

    "redirect to 'Have you used your utr to enrol' page when supplied with value No (false) when skip sa page feature is set to true" in
      new LocalSetupJourney {
        override val saSkipLetterPage = true
        val r = controller.processFiledReturnByPostChoice(
          buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false"))
        override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

        status(r) shouldBe SEE_OTHER
        redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/have-you-enrolled")
      }

    "return a bad request when supplied no value" in new LocalSetupJourney {
      val r = controller.processFiledReturnByPostChoice(buildFakeRequestWithAuth("POST"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe BAD_REQUEST
    }
  }

  "Calling AmbiguousJourneyController.processReceivedUtrLetterChoice" should {

    "redirect to 'Have you used your utr to enrol' page when supplied with value Yes (true)" in new LocalSetupJourney {
      val r = controller.processReceivedUtrLetterChoice(
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "true"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/have-you-enrolled")
    }

    "redirect to 'Your letter may still be in the post' page when supplied with value No (false)" in new LocalSetupJourney {
      val r = controller.processReceivedUtrLetterChoice(
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false"))
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

  "Calling AmbiguousJourneyController.usedUtrToEnrolBackLink" should {

    "have correct backlink when saAmbigSimplifiedJourneyEnabled is true" in
      new LocalSetupJourney {
        override lazy val saAmbigSimplifiedJourney = true
        override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))
        val r = controller.usedUtrToEnrolBackLink()
        r shouldBe "/personal-account/self-assessment/sa-filed-online"
      }

    "have correct backlink when saAmbigSimplifiedJourneyEnabled is false, and saSkipLetterPage is true" in
      new LocalSetupJourney {
        override lazy val saAmbigSimplifiedJourney = false
        override lazy val saSkipLetterPage = true
        override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))
        val r = controller.usedUtrToEnrolBackLink()
        r shouldBe "/personal-account/self-assessment/sa-filed-post"
      }

    "have correct backlink when saAmbigSimplifiedJourneyEnabled is false,and saSkipLetterPage is false" in
      new LocalSetupJourney {
        override lazy val saAmbigSimplifiedJourney = false
        override lazy val saSkipLetterPage = false
        override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))
        val r = controller.usedUtrToEnrolBackLink()
        r shouldBe "/personal-account/self-assessment/received-utr-letter"
      }
  }

  "Calling AmbiguousJourneyController.processUsedUtrToEnrolChoice" should {

    "redirect to 'Your pin has expired' page when supplied with value Yes (true)" in new LocalSetupJourney {
      val r = controller.processUsedUtrToEnrolChoice(
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "true"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/wrong-account")
    }

    "redirect to 'You need to enrol for sa' page when supplied with value No (false)" in new LocalSetupJourney {
      val r = controller.processUsedUtrToEnrolChoice(
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false"))
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
      val r = controller.processUsedUtrToRegisterChoice(
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "true"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/pin-expired-register")
    }

    "redirect to 'The deadline is' page when supplied with value No (false)" in new LocalSetupJourney {
      val r = controller.processUsedUtrToRegisterChoice(
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false"))
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

  "Calling AmbiguousJourneyController.usedUtrToRegisterChoice" should {
    "return 200 when AmbiguousJourneyController.usedUtrToRegisterChoice is called" in new LocalSetupJourney {
      val r = controller.usedUtrToRegisterChoice(buildFakeRequestWithAuth("GET"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe OK
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

    "return a result with tax year 2019 when supplied with value of 'need-to-enrol'" in new LocalSetupJourney {

      when(mockTaxYearRetriever.currentYear).thenReturn(2019)
      val page = "need-to-enrol"
      val result = controller.handleAmbiguousJourneyLandingPages(page)(buildFakeRequestWithAuth("GET"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))
      val doc = asDocument(contentAsString(result))

      assert(
        doc
          .getElementById("returnTaxReturnByPost")
          .text
          .equals("You can send your tax return by post before 31 October 2019."))
    }

    "return a result with tax year 2025 when supplied with value of 'need-to-enrol'" in new LocalSetupJourney {

      when(mockTaxYearRetriever.currentYear).thenReturn(2025)
      val page = "need-to-enrol"
      val result = controller.handleAmbiguousJourneyLandingPages(page)(buildFakeRequestWithAuth("GET"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      val doc = asDocument(contentAsString(result))

      assert(
        doc
          .getElementById("returnTaxReturnByPost")
          .text
          .equals("You can send your tax return by post before 31 October 2025."))
    }

    "return 200 when supplied with value of 'need-to-enrol-again' and SA user type is AmbiguousFilerSelfAssessmentUser" in new LocalSetupJourney {
      val page = "need-to-enrol-again"
      val r = controller.handleAmbiguousJourneyLandingPages(page)(buildFakeRequestWithAuth("GET"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe 200
    }

    "return a result with tax year 2019 when supplied with value of 'need-to-enrol-again'" in new LocalSetupJourney {

      when(mockTaxYearRetriever.currentYear).thenReturn(2019)
      val page = "need-to-enrol-again"
      val result = controller.handleAmbiguousJourneyLandingPages(page)(buildFakeRequestWithAuth("GET"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      val doc = asDocument(contentAsString(result))

      assert(
        doc
          .getElementById("returnTaxReturnByPost")
          .text
          .equals("You can send your tax return by post before 31 October 2019."))
    }

    "return a result with tax year 2025 when supplied with value of 'need-to-enrol-again'" in new LocalSetupJourney {

      when(mockTaxYearRetriever.currentYear).thenReturn(2025)
      val page = "need-to-enrol-again"
      val result = controller.handleAmbiguousJourneyLandingPages(page)(buildFakeRequestWithAuth("GET"))
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      val doc = asDocument(contentAsString(result))

      assert(
        doc
          .getElementById("returnTaxReturnByPost")
          .text
          .equals("You can send your tax return by post before 31 October 2025."))
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
}
