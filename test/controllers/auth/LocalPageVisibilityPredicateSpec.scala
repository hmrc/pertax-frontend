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

package controllers.auth

import config.ConfigDecorator
import models._
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.inject._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services._
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.binders.{ContinueUrl, Origin}
import uk.gov.hmrc.play.frontend.auth.PageIsVisible
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel
import util.{BaseSpec, Fixtures}

import scala.concurrent.Future

class LocalPageVisibilityPredicateSpec extends BaseSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder
    .overrides(bind[CitizenDetailsService].toInstance(MockitoSugar.mock[CitizenDetailsService]))
    .overrides(bind[SelfAssessmentService].toInstance(MockitoSugar.mock[SelfAssessmentService]))
    .build()


  override def beforeEach: Unit = {
  }

  trait Setup {

    def confidenceLevel: ConfidenceLevel
    def getSelfAssessmentAction:  SelfAssessmentUserType
    def allowLowConfidenceSA: Boolean

    lazy val ac = Fixtures.buildFakeAuthContext(withPaye = false, withSa = getSelfAssessmentAction == ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111")))
    lazy val authContext = ac.copy(user = ac.user.copy(confidenceLevel = confidenceLevel))

    lazy val predicate = {
      val fac = new LocalPageVisibilityPredicateFactory(
        MockitoSugar.mock[CitizenDetailsService],
        {
          val sas = MockitoSugar.mock[SelfAssessmentService]
          when (sas.getSelfAssessmentUserType(any())(any())) thenReturn Future.successful(getSelfAssessmentAction)
          sas
        },
        {
          val cd = MockitoSugar.mock[ConfigDecorator]
          when(cd.identityVerificationUpliftUrl) thenReturn "/mdtp/uplift"
          when(cd.allowLowConfidenceSAEnabled) thenReturn allowLowConfidenceSA
          when(cd.pertaxFrontendHost) thenReturn ""
          when(cd.companyAuthHost) thenReturn ""
          cd
        }
      )
      fac.build(Some(ContinueUrl("/personal-account/success-page")), Origin("PERTAX"))
    }

    def nonVisibleRedirectLocation: Option[String] = {
      val x = await(predicate.apply(authContext, FakeRequest()))
      redirectLocation(x.nonVisibleResult)
    }
  }

  "Calling LocalPageVisibilityPredicate" should {

    "always return a visible result if the user has a CL of 200" in new Setup {

      override val confidenceLevel = ConfidenceLevel.L200
      override val getSelfAssessmentAction = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      override val allowLowConfidenceSA = false

      nonVisibleRedirectLocation shouldBe None
      await(predicate.apply(authContext, FakeRequest())) shouldBe PageIsVisible

    }

    "return a blocked result redirecting to IV if the user has a CL of 100" in new Setup {

      override val confidenceLevel = ConfidenceLevel.L100
      override val getSelfAssessmentAction = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      override val allowLowConfidenceSA = false

      nonVisibleRedirectLocation shouldBe Some("/mdtp/uplift?origin=PERTAX&confidenceLevel=200" +
        "&completionURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account%252Fsuccess-page" +
        "&failureURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account%252Fsuccess-page")
    }

    "return a blocked result redirecting to IV if the user has a CL of 50" in new Setup {

      override val confidenceLevel = ConfidenceLevel.L50
      override val getSelfAssessmentAction = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      override val allowLowConfidenceSA = false

      nonVisibleRedirectLocation shouldBe Some("/mdtp/uplift?origin=PERTAX&confidenceLevel=200" +
        "&completionURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account%252Fsuccess-page" +
        "&failureURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account%252Fsuccess-page")
    }
  }

  "Calling LocalPageVisibilityPredicate with a confidence level of < 200" should {

    trait LowCLSetup extends Setup {
      override lazy val confidenceLevel = ConfidenceLevel.L100
    }

    "return a blocked result, redirecting the user to the continue to self assessment page if the user has an active SA enrolment and the 'sa allow low confidence' feature is on" in new LowCLSetup {

      override val allowLowConfidenceSA = true
      override val getSelfAssessmentAction = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      nonVisibleRedirectLocation shouldBe Some("/personal-account/sa-continue?continueUrl=%2Fpersonal-account%2Fsuccess-page")
    }

    "return a blocked result, redirecting the user to IV if the user has an active SA enrolment and the 'sa allow low confidence' feature is off" in new LowCLSetup {

      override val allowLowConfidenceSA = false
      override val getSelfAssessmentAction = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      nonVisibleRedirectLocation shouldBe Some("/mdtp/uplift?origin=PERTAX&confidenceLevel=200" +
        "&completionURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account%252Fsuccess-page" +
        "&failureURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account%252Fsuccess-page")
    }
  }
}
