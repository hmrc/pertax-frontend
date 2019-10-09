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
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, EnforceAmbiguousUserAction}
import models._
import org.jsoup.Jsoup
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.i18n.MessagesApi
import play.api.mvc.{ActionBuilder, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.domain.SaUtr
import util.Fixtures._
import util.{BaseSpec, Fixtures, LocalPartialRetriever, TaxYearRetriever}

import scala.concurrent.Future

class AmbiguousJourneyControllerSpec extends BaseSpec with MockitoSugar {

  implicit lazy val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]

  val mockTaxYearRetriever = mock[TaxYearRetriever]
  val mockConfigDecorator = mock[ConfigDecorator]
  val mockAuthJourney = mock[AuthJourney]
  val mockAmbiguousUserAction = mock[EnforceAmbiguousUserAction]

  override def beforeEach: Unit = reset(mockConfigDecorator)

  def saSkipLetterPage: Boolean = false

  def controller =
    new AmbiguousJourneyController(
      injected[MessagesApi],
      mockTaxYearRetriever,
      mockAuthJourney,
      mockAmbiguousUserAction
    )(mock[LocalPartialRetriever], mockConfigDecorator) {
      when(mockConfigDecorator.ssoUrl) thenReturn Some("ssoUrl")
      when(mockConfigDecorator.getFeedbackSurveyUrl(any())) thenReturn "/test"
      when(mockConfigDecorator.analyticsToken) thenReturn Some("N/A")
      when(mockConfigDecorator.saAmbigSkipUTRLetterEnabled) thenReturn saSkipLetterPage
    }

  "Calling AmbiguousJourneyController.processFileReturnOnlineChoice" should {

    "redirect to 'Have you de-enrolled from self assessment' page when supplied with value Yes (true) and not on simplified journey" in {
      val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        None,
        None,
        AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111")),
        "GovernmentGateway",
        ConfidenceLevel.L200,
        None,
        None,
        None,
        None,
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "true")
      )

      when(mockAuthJourney.auth).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(userRequest.asInstanceOf[UserRequest[A]])
      })

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      val result = controller.processFileReturnOnlineChoice(userRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(await(result)) shouldBe Some("/personal-account/self-assessment/de-enrolled-sa")
    }

    "redirect to 'Have you filed your tax return by post' page when supplied with value No (false) and not on simplified journey" in {
      val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        None,
        None,
        AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111")),
        "GovernmentGateway",
        ConfidenceLevel.L200,
        None,
        None,
        None,
        None,
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false")
      )

      when(mockAuthJourney.auth).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(userRequest.asInstanceOf[UserRequest[A]])
      })

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      val result = controller.processFileReturnOnlineChoice(userRequest)
      status(result) shouldBe SEE_OTHER
      redirectLocation(await(result)) shouldBe Some("/personal-account/self-assessment/sa-filed-post")
    }

    "redirect to 'Have you used your utr to enrol' page when supplied with value No (false) and on simplified journey" in {
      val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        None,
        None,
        AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111")),
        "GovernmentGateway",
        ConfidenceLevel.L200,
        None,
        None,
        None,
        None,
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false")
      )

      when(mockAuthJourney.auth).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(userRequest.asInstanceOf[UserRequest[A]])
      })

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn true

      val result = controller.processFileReturnOnlineChoice(userRequest)
      status(result) shouldBe SEE_OTHER
      redirectLocation(await(result)) shouldBe Some("/personal-account/self-assessment/have-you-enrolled")
    }

    "return a bad request when supplied no value" in {
      val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        None,
        None,
        AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111")),
        "GovernmentGateway",
        ConfidenceLevel.L200,
        None,
        None,
        None,
        None,
        buildFakeRequestWithAuth("POST")
      )

      when(mockAuthJourney.auth).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(userRequest.asInstanceOf[UserRequest[A]])
      })

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      val r: Future[Result] = controller.processFileReturnOnlineChoice(userRequest)

      status(r) shouldBe BAD_REQUEST
    }
  }

  "Calling AmbiguousJourneyController.processDeEnroledFromSaChoice" should {

    "redirect to 'You need to enrol' page when supplied with value Yes (true)" in {
      val r = controller.processDeEnroledFromSaChoice(
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "true"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/need-to-enrol-again")
    }

    "redirect to 'You need to use the creds you've created' page when supplied with value No (false)" in {
      val r = controller.processDeEnroledFromSaChoice(
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/need-to-use-created-creds")
    }

    "return a bad request when supplied no value" in {
      val r = controller.processDeEnroledFromSaChoice(buildFakeRequestWithAuth("POST"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe BAD_REQUEST
    }
  }

  "Calling AmbiguousJourneyController.processFiledReturnByPostChoice" should {
    "redirect to 'Have you used your utr to register' page when supplied with value Yes (true)" in {
      val r = controller.processFiledReturnByPostChoice(
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "true"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/used-utr-to-register")
    }

    "redirect to 'You need to use the creds you've created' page when supplied with value No (false) when skip sa page feature is set to false" in {
      val r = controller.processFiledReturnByPostChoice(
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/received-utr-letter")
    }

    "redirect to 'Have you used your utr to enrol' page when supplied with value No (false) when skip sa page feature is set to true" in {
      val saSkipLetterPage = true
      val r = controller.processFiledReturnByPostChoice(
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/have-you-enrolled")
    }

    "return a bad request when supplied no value" in {
      val r = controller.processFiledReturnByPostChoice(buildFakeRequestWithAuth("POST"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe BAD_REQUEST
    }
  }

  "Calling AmbiguousJourneyController.processReceivedUtrLetterChoice" should {

    "redirect to 'Have you used your utr to enrol' page when supplied with value Yes (true)" in {
      val r = controller.processReceivedUtrLetterChoice(
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "true"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/have-you-enrolled")
    }

    "redirect to 'Your letter may still be in the post' page when supplied with value No (false)" in {
      val r = controller.processReceivedUtrLetterChoice(
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/letter-in-post")
    }

    "return a bad request when supplied no value" in {
      val r = controller.processReceivedUtrLetterChoice(buildFakeRequestWithAuth("POST"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe BAD_REQUEST
    }
  }

  "Calling AmbiguousJourneyController.usedUtrToEnrolBackLink" should {

    "have correct backlink when saAmbigSimplifiedJourneyEnabled is true" in {
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn true

      val r = controller.usedUtrToEnrolBackLink()
      r shouldBe "/personal-account/self-assessment/sa-filed-online"
    }

    "have correct backlink when saAmbigSimplifiedJourneyEnabled is false, and saSkipLetterPage is true" in {
      val saSkipLetterPage = true
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      val r = controller.usedUtrToEnrolBackLink()
      r shouldBe "/personal-account/self-assessment/sa-filed-post"
    }

    "have correct backlink when saAmbigSimplifiedJourneyEnabled is false,and saSkipLetterPage is false" in {
      val saSkipLetterPage = false
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      val r: String = controller.usedUtrToEnrolBackLink()
      r shouldBe "/personal-account/self-assessment/received-utr-letter"
    }
  }

  "Calling AmbiguousJourneyController.processUsedUtrToEnrolChoice" should {

    "redirect to 'Your pin has expired' page when supplied with value Yes (true)" in {
      val r = controller.processUsedUtrToEnrolChoice(
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "true"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/wrong-account")
    }

    "redirect to 'You need to enrol for sa' page when supplied with value No (false)" in {
      val r = controller.processUsedUtrToEnrolChoice(
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/need-to-enrol")
    }

    "return a bad request when supplied no value" in {
      val r = controller.processUsedUtrToEnrolChoice(buildFakeRequestWithAuth("POST"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe BAD_REQUEST
    }
  }

  "Calling AmbiguousJourneyController.processUsedUtrToRegisterChoice" should {

    "redirect to 'Your pin has expired' page when supplied with value Yes (true)" in {
      val r = controller.processUsedUtrToRegisterChoice(
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "true"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/pin-expired-register")
    }

    "redirect to 'The deadline is' page when supplied with value No (false)" in {
      val r = controller.processUsedUtrToRegisterChoice(
        buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("ambiguousUserFormChoice" -> "false"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/self-assessment/result/deadline")
    }

    "return a bad request when supplied no value" in {
      val r = controller.processUsedUtrToRegisterChoice(buildFakeRequestWithAuth("POST"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe BAD_REQUEST
    }

  }

  "Calling AmbiguousJourneyController.usedUtrToRegisterChoice" should {
    "return 200 when AmbiguousJourneyController.usedUtrToRegisterChoice is called" in {
      val r = controller.usedUtrToRegisterChoice(buildFakeRequestWithAuth("GET"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe OK
    }
  }

  "Calling AmbiguousJourneyController.filedReturnOnlineChoice" should {
    "return 200 when self assessment user type is AmbiguousFilerSelfAssessmentUser" in {
      val r = controller.filedReturnOnlineChoice(buildFakeRequestWithAuth("GET"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe OK
    }
  }

  "Calling AmbiguousJourneyController.deEnrolledFromSaChoice " should {
    "return 200 when AmbiguousJourneyController.deEnrolledFromSaChoice is called" in {
      val r = controller.deEnrolledFromSaChoice(buildFakeRequestWithAuth("GET"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe OK
    }
  }

  "Calling AmbiguousJourneyController.filedReturnByPostChoice" should {
    "return 200 when AmbiguousJourneyController.filedReturnByPostChoice is called" in {
      val r = controller.filedReturnByPostChoice(buildFakeRequestWithAuth("GET"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe OK
    }
  }

  "Calling AmbiguousJourneyController.receivedUtrLetterChoice" should {
    "return 200 when AmbiguousJourneyController.receivedUtrLetterChoice is called" in {
      val r = controller.receivedUtrLetterChoice(buildFakeRequestWithAuth("GET"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe OK
    }
  }

  "Calling AmbiguousJourneyController.usedUtrToEnrolChoice" should {
    "return 200 when AmbiguousJourneyController.usedUtrToEnrolChoice is called" in {
      val r = controller.usedUtrToEnrolChoice(buildFakeRequestWithAuth("GET"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe OK
    }
  }

  "Calling AmbiguousJourneyController.handleAmbiguousJourneyLandingPages" should {
    "return 200 when supplied with value of 'need-to-enrol' and SA user type is AmbiguousFilerSelfAssessmentUser" in {
      val page = "need-to-enrol"
      val r = controller.handleAmbiguousJourneyLandingPages(page)(buildFakeRequestWithAuth("GET"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      status(r) shouldBe 200
    }

    "return a result with tax year 2019 when supplied with value of 'need-to-enrol'" in {

      when(mockTaxYearRetriever.currentYear).thenReturn(2019)
      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      val page = "need-to-enrol"
      val result = controller.handleAmbiguousJourneyLandingPages(page)(buildFakeRequestWithAuth("GET"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))
      val doc = Jsoup.parse(contentAsString(result))

      assert(
        doc
          .getElementById("returnTaxReturnByPost")
          .text
          .equals("You can send your tax return by post before 31 October 2019."))
    }

    "return a result with tax year 2025 when supplied with value of 'need-to-enrol'" in {

      when(mockTaxYearRetriever.currentYear).thenReturn(2025)
      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      val page = "need-to-enrol"
      val result = controller.handleAmbiguousJourneyLandingPages(page)(buildFakeRequestWithAuth("GET"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      val doc = Jsoup.parse(contentAsString(result))

      assert(
        doc
          .getElementById("returnTaxReturnByPost")
          .text
          .equals("You can send your tax return by post before 31 October 2025."))
    }

    "return 200 when supplied with value of 'need-to-enrol-again' and SA user type is AmbiguousFilerSelfAssessmentUser" in {
      val page = "need-to-enrol-again"
      val r = controller.handleAmbiguousJourneyLandingPages(page)(buildFakeRequestWithAuth("GET"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe 200
    }

    "return a result with tax year 2019 when supplied with value of 'need-to-enrol-again'" in {

      when(mockTaxYearRetriever.currentYear).thenReturn(2019)
      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      val page = "need-to-enrol-again"
      val result = controller.handleAmbiguousJourneyLandingPages(page)(buildFakeRequestWithAuth("GET"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      val doc = Jsoup.parse(contentAsString(result))

      assert(
        doc
          .getElementById("returnTaxReturnByPost")
          .text
          .equals("You can send your tax return by post before 31 October 2019."))
    }

    "return a result with tax year 2025 when supplied with value of 'need-to-enrol-again'" in {

      when(mockTaxYearRetriever.currentYear).thenReturn(2025)
      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      val page = "need-to-enrol-again"
      val result = controller.handleAmbiguousJourneyLandingPages(page)(buildFakeRequestWithAuth("GET"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      val doc = Jsoup.parse(contentAsString(result))

      assert(
        doc
          .getElementById("returnTaxReturnByPost")
          .text
          .equals("You can send your tax return by post before 31 October 2025."))
    }

    "return 200 when supplied with value of 'need-to-use-created-creds' and SA user type is AmbiguousFilerSelfAssessmentUser" in {
      val page = "need-to-use-created-creds"
      val r = controller.handleAmbiguousJourneyLandingPages(page)(buildFakeRequestWithAuth("GET"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe 200
    }

    "return 200 when supplied with value of 'deadline' and SA user type is AmbiguousFilerSelfAssessmentUser" in {
      val page = "deadline"
      val r = controller.handleAmbiguousJourneyLandingPages(page)(buildFakeRequestWithAuth("GET"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe 200
    }

    "return 200 when supplied with value of 'letter-in-post' and SA user type is AmbiguousFilerSelfAssessmentUser" in {
      val page = "letter-in-post"
      val r = controller.handleAmbiguousJourneyLandingPages(page)(buildFakeRequestWithAuth("GET"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe 200
    }

    "return 200 when supplied with value of 'pin-expired' and SA user type is AmbiguousFilerSelfAssessmentUser" in {
      val page = "pin-expired"
      val r = controller.handleAmbiguousJourneyLandingPages(page)(buildFakeRequestWithAuth("GET"))
      val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      when(mockConfigDecorator.saAmbigSimplifiedJourneyEnabled) thenReturn false

      status(r) shouldBe 200
    }
  }
}
