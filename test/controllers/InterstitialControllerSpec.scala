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

package controllers

import config.ConfigDecorator
import connectors.{FrontEndDelegationConnector, PertaxAuditConnector, PertaxAuthConnector}
import controllers.auth.PertaxRegime
import error.LocalErrorHandler
import models.UserDetails
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.i18n.MessagesApi
import play.api.test.Helpers._
import play.twirl.api.Html
import services._
import services.partials.{FormPartialService, SaPartialService}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel
import uk.gov.hmrc.play.partials.HtmlPartial
import util.Fixtures._
import util.{BaseSpec, Fixtures, LocalPartialRetriever}

import scala.concurrent.Future

class InterstitialControllerSpec extends BaseSpec {

  trait LocalSetup {

    lazy val request = buildFakeRequestWithAuth("GET")

    def authProviderType: String
    def withPaye: Boolean
    def withSa: Boolean
    def confidenceLevel: ConfidenceLevel
    def simulateFormPartialServiceFailure: Boolean
    def simulateSaPartialServiceFailure: Boolean
    def paperlessResponse: ActivatePaperlessResponse

    lazy val authority = buildFakeAuthority(withPaye, withSa, confidenceLevel)

    lazy val c = new InterstitialController(
      injected[MessagesApi],
      MockitoSugar.mock[FormPartialService],
      MockitoSugar.mock[SaPartialService],
      MockitoSugar.mock[CitizenDetailsService],
      MockitoSugar.mock[UserDetailsService],
      MockitoSugar.mock[FrontEndDelegationConnector],
      MockitoSugar.mock[PreferencesFrontendService],
      MockitoSugar.mock[PertaxAuditConnector],
      MockitoSugar.mock[PertaxAuthConnector],
      MockitoSugar.mock[LocalPartialRetriever],
      MockitoSugar.mock[ConfigDecorator],
      injected[PertaxRegime],
      injected[LocalErrorHandler]
    ) {
      private def formPartialServiceResponse = Future.successful {
        if(simulateFormPartialServiceFailure) HtmlPartial.Failure()
        else HtmlPartial.Success(Some("Success"), Html("any"))
      }
      when(formPartialService.getSelfAssessmentPartial(any())) thenReturn formPartialServiceResponse
      when(formPartialService.getNationalInsurancePartial(any())) thenReturn formPartialServiceResponse

      when(saPartialService.getSaAccountSummary(any())) thenReturn {
        Future.successful {
          if(simulateSaPartialServiceFailure) HtmlPartial.Failure()
          else HtmlPartial.Success(Some("Success"), Html("any"))
        }
      }

      when(citizenDetailsService.personDetails(meq(Fixtures.fakeNino))(any())) thenReturn {
        Future.successful(PersonDetailsSuccessResponse(Fixtures.buildPersonDetails))
      }
      when(authConnector.currentAuthority(any(), any())) thenReturn {
        Future.successful(Some(authority))
      }
      when(userDetailsService.getUserDetails(any())(any())) thenReturn {
        Future.successful(Some(UserDetails(authProviderType)))
      }
      when(preferencesFrontendService.getPaperlessPreference(any())(any())) thenReturn {
        Future.successful(paperlessResponse)
      }
      when(configDecorator.taxCreditsEnabled) thenReturn true
      when(configDecorator.ssoUrl) thenReturn Some("ssoUrl")
      when(configDecorator.getFeedbackSurveyUrl(any())) thenReturn "/test"
      when(configDecorator.analyticsToken) thenReturn Some("N/A")

    }
  }

  "Calling displayNationalInsurance" should {

    "call FormPartialService.getNationalInsurancePartial and return 200 when called by authorised user who is high gg" in new LocalSetup {

      lazy val withPaye = true
      lazy val withSa = false
      lazy val confidenceLevel = ConfidenceLevel.L200
      lazy val authProviderType = UserDetails.GovernmentGatewayAuthProvider
      lazy val simulateFormPartialServiceFailure = false
      lazy val simulateSaPartialServiceFailure = false
      lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse


      val r = c.displayNationalInsurance(request)
      status(r) shouldBe OK
      verify(c.formPartialService, times(1)).getNationalInsurancePartial(any())
      verify(c.citizenDetailsService, times(1)).personDetails(meq(Fixtures.fakeNino))(any())

    }
  }

  "Calling displayChildBenefits" should {

    "call FormPartialService.getChildBenefitPartial and return 200 when called by authorised user who is high gg" in new LocalSetup {

      lazy val withPaye = false
      lazy val withSa = false
      lazy val confidenceLevel = ConfidenceLevel.L200
      lazy val authProviderType = UserDetails.GovernmentGatewayAuthProvider
      lazy val simulateFormPartialServiceFailure = false
      lazy val simulateSaPartialServiceFailure = false
      lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse


      val r = c.displayChildBenefits(request)
      status(r) shouldBe OK
      verify(c.citizenDetailsService, times(0)).personDetails(meq(Fixtures.fakeNino))(any())
    }
  }

  "Calling viewSelfAssessmentSummary" should {

    "call FormPartialService.getSelfAssessmentPartial and return 200 when called by a high GG user" in new LocalSetup {

      lazy val withPaye = false
      lazy val withSa = true
      lazy val confidenceLevel = ConfidenceLevel.L200
      lazy val authProviderType = UserDetails.GovernmentGatewayAuthProvider
      lazy val simulateFormPartialServiceFailure = false
      lazy val simulateSaPartialServiceFailure = false
      lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse


      val r = c.displaySelfAssessment(request)
      status(r) shouldBe OK
      verify(c.formPartialService, times(1)).getSelfAssessmentPartial(any()) //Not called at the min due to DFS bug
      verify(c.citizenDetailsService, times(0)).personDetails(any())(any())
    }

    "call FormPartialService.getSelfAssessmentPartial and return return 401 for a high GG user not enrolled in SA" in new LocalSetup {

      lazy val withPaye = false
      lazy val withSa = false
      lazy val confidenceLevel = ConfidenceLevel.L200
      lazy val authProviderType = UserDetails.GovernmentGatewayAuthProvider
      lazy val simulateFormPartialServiceFailure = true
      lazy val simulateSaPartialServiceFailure = true
      lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse


      val r = c.displaySelfAssessment(request)
      status(r) shouldBe UNAUTHORIZED
      verify(c.formPartialService, times(1)).getSelfAssessmentPartial(any())
      verify(c.citizenDetailsService, times(0)).personDetails(any())(any())
    }

    "call FormPartialService.getSelfAssessmentPartial and return 401 for a user not logged in via GG" in new LocalSetup {

      lazy val withPaye = true
      lazy val withSa = false
      lazy val confidenceLevel = ConfidenceLevel.L0
      lazy val authProviderType = UserDetails.VerifyAuthProvider
      lazy val simulateFormPartialServiceFailure = true
      lazy val simulateSaPartialServiceFailure = true
      lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse


      val r = c.displaySelfAssessment(request)
      status(r) shouldBe UNAUTHORIZED
      verify(c.formPartialService, times(1)).getSelfAssessmentPartial(any())
      verify(c.citizenDetailsService, times(1)).personDetails(meq(Fixtures.fakeNino))(any())
    }

    "Calling getSa302" should {
      "should return OK response when accessing with an SA user with a valid tax year" in new LocalSetup {
        lazy val withPaye = true
        lazy val withSa = true
        lazy val confidenceLevel = ConfidenceLevel.L200
        lazy val authProviderType = UserDetails.GovernmentGatewayAuthProvider
        lazy val simulateFormPartialServiceFailure = false
        lazy val simulateSaPartialServiceFailure = false
        lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse

        val r = c.displaySa302Interrupt(2014)(request)
        status(r) shouldBe OK
      }

      "should return UNAUTHORIZED response when accessing with a none SA user with a valid tax year" in new LocalSetup {
        lazy val withPaye = true
        lazy val withSa = false
        lazy val confidenceLevel = ConfidenceLevel.L200
        lazy val authProviderType = UserDetails.GovernmentGatewayAuthProvider
        lazy val simulateFormPartialServiceFailure = false
        lazy val simulateSaPartialServiceFailure = false
        lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse

        val r = c.displaySa302Interrupt(2014)(request)
        status(r) shouldBe UNAUTHORIZED
      }
    }
  }
}
