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

package controllers.auth

import config.ConfigDecorator
import controllers.bindable.Origin
import models._
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services._
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.frontend.auth.PageIsVisible
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{ConfidenceLevel, CredentialStrength}
import util.{BaseSpec, Fixtures}

import scala.concurrent.Future

class LocalPageVisibilityPredicateSpec extends BaseSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder
    .overrides(bind[EnrolmentExceptionListService].toInstance(MockitoSugar.mock[EnrolmentExceptionListService]))
    .overrides(bind[CitizenDetailsService].toInstance(MockitoSugar.mock[CitizenDetailsService]))
    .overrides(bind[SelfAssessmentService].toInstance(MockitoSugar.mock[SelfAssessmentService]))
    .build()


  override def beforeEach: Unit = {
  }

  trait Setup {

    def confidenceLevel: ConfidenceLevel
    def credentialStrength: CredentialStrength
    def simulateAccountPresentInExceptionList: Boolean
    def getSelfAssessmentAction:  SelfAssessmentUserType
    def allowIvExceptions: Boolean
    def allowLowConfidenceSA: Boolean

    lazy val ac = Fixtures.buildFakeAuthContext(withPaye = false, withSa = getSelfAssessmentAction == ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111")))

    lazy val authContext = ac.copy(user = ac.user.copy(confidenceLevel = confidenceLevel, credentialStrength = credentialStrength))

    lazy val predicate = {
      val fac = new LocalPageVisibilityPredicateFactory(
        {
          val eels = MockitoSugar.mock[EnrolmentExceptionListService]
          when(eels.isAccountIdentityVerificationExempt(any())(any())) thenReturn Future.successful(simulateAccountPresentInExceptionList)
          eels
        },
        MockitoSugar.mock[CitizenDetailsService],
        {
          val sas = MockitoSugar.mock[SelfAssessmentService]
          when (sas.getSelfAssessmentUserType(any())(any())) thenReturn Future.successful(getSelfAssessmentAction)
          sas
        },
        {
          val cd = MockitoSugar.mock[ConfigDecorator]
          when(cd.identityVerificationUpliftUrl) thenReturn "/mdtp/uplift"
          when(cd.ivExeptionsEnabled) thenReturn allowIvExceptions
          when(cd.allowLowConfidenceSAEnabled) thenReturn allowLowConfidenceSA
          when(cd.pertaxFrontendHost) thenReturn ""
          when(cd.companyAuthHost) thenReturn ""
          cd
        }
      )
      fac.build(Some("/personal-account/success-page"), Origin("PERTAX"))
    }

    def nonVisibleRedirectLocation: Option[String] = {
      val x = await(predicate.apply(authContext, FakeRequest()))
      redirectLocation(x.nonVisibleResult)
    }
  }

  "Calling LocalPageVisibilityPredicate" should {

    "always return a visible result if the user has a CL of 200 and strong credential strength" in new Setup {

      override val confidenceLevel = ConfidenceLevel.L200
      override val credentialStrength = CredentialStrength.Strong
      override val getSelfAssessmentAction = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      override val allowIvExceptions = true
      override val simulateAccountPresentInExceptionList = false
      override val allowLowConfidenceSA = false

      nonVisibleRedirectLocation shouldBe None
      await(predicate.apply(authContext, FakeRequest())) shouldBe PageIsVisible

    }

    "return a blocked result redirecting to IV if the user has a CL of 200 and weak credential strength" in new Setup {

      override val confidenceLevel = ConfidenceLevel.L200
      override val credentialStrength = CredentialStrength.Weak
      override val getSelfAssessmentAction = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      override val allowIvExceptions = true
      override val simulateAccountPresentInExceptionList = false
      override val allowLowConfidenceSA = false

      nonVisibleRedirectLocation shouldBe Some("/coafe/two-step-verification/register" +
        "?continue=%2Fpersonal-account%2Fdo-uplift%3FredirectUrl%3D%252Fpersonal-account%252Fsuccess-page" +
        "&failure=%2Fpersonal-account%2Fidentity-check-complete")
    }

    "return a blocked result redirecting to IV if the user has a CL of 200 and no credential strength" in new Setup {

      override val confidenceLevel = ConfidenceLevel.L200
      override val credentialStrength = CredentialStrength.None
      override val getSelfAssessmentAction = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      override val allowIvExceptions = true
      override val simulateAccountPresentInExceptionList = false
      override val allowLowConfidenceSA = false

      nonVisibleRedirectLocation shouldBe Some("/coafe/two-step-verification/register" +
        "?continue=%2Fpersonal-account%2Fdo-uplift%3FredirectUrl%3D%252Fpersonal-account%252Fsuccess-page" +
        "&failure=%2Fpersonal-account%2Fidentity-check-complete")
    }

    "return a blocked result redirecting to IV if the user has a CL of 100 and strong credentials" in new Setup {

      override val confidenceLevel = ConfidenceLevel.L100
      override val credentialStrength = CredentialStrength.Strong
      override val getSelfAssessmentAction = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      override val allowIvExceptions = true
      override val simulateAccountPresentInExceptionList = false
      override val allowLowConfidenceSA = false

      nonVisibleRedirectLocation shouldBe Some("/mdtp/uplift?origin=PERTAX&confidenceLevel=200" +
        "&completionURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account%252Fsuccess-page" +
        "&failureURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account%252Fsuccess-page")
    }

    "return a blocked result redirecting to IV if the user has a CL of 50 and strong credentials" in new Setup {

      override val confidenceLevel = ConfidenceLevel.L50
      override val credentialStrength = CredentialStrength.Strong
      override val getSelfAssessmentAction = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      override val allowIvExceptions = true
      override val simulateAccountPresentInExceptionList = false
      override val allowLowConfidenceSA = false

      nonVisibleRedirectLocation shouldBe Some("/mdtp/uplift?origin=PERTAX&confidenceLevel=200" +
        "&completionURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account%252Fsuccess-page" +
        "&failureURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account%252Fsuccess-page")
    }

    "return a blocked result, redirecting the user to the continue to self assessment page for users on the IV exception list if the user has a CL of 100, strong credentials and is on the exception list" in new Setup {

      override val confidenceLevel = ConfidenceLevel.L100
      override val credentialStrength = CredentialStrength.Strong
      override val getSelfAssessmentAction = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      override val allowIvExceptions = true
      override val simulateAccountPresentInExceptionList = true
      override val allowLowConfidenceSA = false

      nonVisibleRedirectLocation shouldBe Some("/personal-account/sa-continue?continueUrl=%2Fpersonal-account%2Fsuccess-page")
    }

    "return a blocked result, redirecting to IV for users on the IV exception list if the user has a CL of 100, strong credentials and is on the exception list, but IV exceptions are toggled off" in new Setup {

      override val confidenceLevel = ConfidenceLevel.L100
      override val credentialStrength = CredentialStrength.Strong
      override val getSelfAssessmentAction = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      override val allowIvExceptions = false
      override val simulateAccountPresentInExceptionList = true
      override val allowLowConfidenceSA = false

      nonVisibleRedirectLocation shouldBe Some("/mdtp/uplift?origin=PERTAX&confidenceLevel=200" +
        "&completionURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account%252Fsuccess-page" +
        "&failureURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account%252Fsuccess-page")
    }

    "always return a visible result if the user has a CL of 200 and strong credential strength, even if they are on the IV exception list" in new Setup {

      override val confidenceLevel = ConfidenceLevel.L200
      override val credentialStrength = CredentialStrength.Strong
      override val getSelfAssessmentAction = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      override val allowIvExceptions = true
      override val simulateAccountPresentInExceptionList = true
      override val allowLowConfidenceSA = false

      nonVisibleRedirectLocation shouldBe None
      await(predicate.apply(authContext, FakeRequest())) shouldBe PageIsVisible
    }
  }

  "Calling LocalPageVisibilityPredicate with strong credentials and a confidence level of < 200" should {

    trait LowCLSetup extends Setup {
      override lazy val confidenceLevel = ConfidenceLevel.L100
      override lazy val credentialStrength = CredentialStrength.Strong
    }

    "return a blocked result, redirecting the user to the continue to self assessment page if the user has a not yet Activated SA enrolment and is on the IV exmption list." in new LowCLSetup {

      override val allowIvExceptions = true
      override val simulateAccountPresentInExceptionList = true
      override val allowLowConfidenceSA = false
      override val getSelfAssessmentAction = NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      nonVisibleRedirectLocation shouldBe Some("/personal-account/sa-continue?continueUrl=%2Fpersonal-account%2Fsuccess-page")
    }

    "return a blocked result, redirecting the user to the continue to self assessment page if the user has an active SA enrolment and is on the IV exmption list." in new LowCLSetup {

      override val allowIvExceptions = true
      override val simulateAccountPresentInExceptionList = true
      override val allowLowConfidenceSA = false
      override val getSelfAssessmentAction = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      nonVisibleRedirectLocation shouldBe Some("/personal-account/sa-continue?continueUrl=%2Fpersonal-account%2Fsuccess-page")
    }

    "return a blocked result, redirecting the user to IV if the user is a Non SA filer and they are on the exception list. (This is not possible currently as the exception list requires a SaUtr)" in new LowCLSetup {

      override val allowIvExceptions = true
      override val simulateAccountPresentInExceptionList = true
      override val allowLowConfidenceSA = false
      override val getSelfAssessmentAction = NonFilerSelfAssessmentUser
      nonVisibleRedirectLocation shouldBe Some("/mdtp/uplift?origin=PERTAX&confidenceLevel=200" +
        "&completionURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account%252Fsuccess-page" +
        "&failureURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account%252Fsuccess-page")
    }

    "return a blocked result, redirecting the user to the continue to self assessment page if the user has no SA enrolment and is on the IV exemption list." in new LowCLSetup {

      override val allowIvExceptions = true
      override val simulateAccountPresentInExceptionList = true
      override val allowLowConfidenceSA = false
      override val getSelfAssessmentAction = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))
      nonVisibleRedirectLocation shouldBe Some("/personal-account/sa-continue?continueUrl=%2Fpersonal-account%2Fsuccess-page")
    }

    "return a blocked result, redirecting the user to the continue to self assessment page if the user has an active SA enrolment and the 'sa allow low confidence' feature is on" in new LowCLSetup {

      override val allowIvExceptions = false
      override val simulateAccountPresentInExceptionList = false
      override val allowLowConfidenceSA = true
      override val getSelfAssessmentAction = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      nonVisibleRedirectLocation shouldBe Some("/personal-account/sa-continue?continueUrl=%2Fpersonal-account%2Fsuccess-page")
    }

    "return a blocked result, redirecting the user to IV if the user has an active SA enrolment and the 'sa allow low confidence' feature is off" in new LowCLSetup {

      override val allowIvExceptions = false
      override val simulateAccountPresentInExceptionList = false
      override val allowLowConfidenceSA = false
      override val getSelfAssessmentAction = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      nonVisibleRedirectLocation shouldBe Some("/mdtp/uplift?origin=PERTAX&confidenceLevel=200" +
        "&completionURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account%252Fsuccess-page" +
        "&failureURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account%252Fsuccess-page")
    }
  }
}
