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

package controllers.helpers

import config.ConfigDecorator
import models._
import org.scalatest.mockito.MockitoSugar
import play.api.i18n.{I18nSupport, Lang, Messages, MessagesApi}
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.SaUtr
import util.{BaseSpec, Fixtures}
import org.mockito.Matchers._
import org.mockito.Mockito._
import util.Fixtures.buildFakeAuthContext

class HomeCardGeneratorSpec extends BaseSpec {

  trait SpecSetup extends I18nSupport {

    override def messagesApi: MessagesApi = injected[MessagesApi]
    lazy val messages = new Messages(Lang("en"), messagesApi)

    val c = new HomeCardGenerator
  }


  "Calling getPayAsYouEarnCard" should {

    trait LocalSetup extends SpecSetup {

      def hasPertaxUser: Boolean
      def isPayeUser: Boolean
      def hasTaxSummary: Boolean
      def iabdType: Int

      lazy val taxSummary = if (hasTaxSummary) Some(Fixtures.buildTaxSummary.copy(companyBenefits = Seq(iabdType))) else None

      lazy val pertaxUser = if(hasPertaxUser)
        Some(PertaxUser(Fixtures.buildFakeAuthContext(withPaye = isPayeUser),UserDetails(UserDetails.GovernmentGatewayAuthProvider),None, true))
      else None

      lazy val cardBody = c.getPayAsYouEarnCard(pertaxUser, taxSummary)(messages = messages, messagesApi = messagesApi).map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n")) //remove empty lines
    }

    "return nothing when called with no Pertax user" in new LocalSetup {
      val hasPertaxUser = false
      val isPayeUser = false
      val hasTaxSummary = false
      val iabdType = 0

      cardBody shouldBe None
    }

    "return nothing when called with a Pertax user that is not PAYE" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = false
      val hasTaxSummary = false
      val iabdType = 0

      cardBody shouldBe None
    }

    "return nothing when called with with a Pertax user that is PAYE but has no tax summary" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = true
      val hasTaxSummary = false
      val iabdType = 0

      cardBody shouldBe None
    }

    "return correct markup when called with with a Pertax user that is PAYE with company benefits" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = true
      val hasTaxSummary = true
      val iabdType = 31

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link ga-track-anchor-click" href="/check-income-tax/paye" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='Pay As You Earn (PAYE)'>
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">Pay As You Earn (PAYE)</h3>
               |      <p>Your income from employers and private pensions that is taxed before it is paid to you.</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |    <ul>
               |      <li><a class="ga-track-anchor-click" href="/check-income-tax/income-tax" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='Make sure you are paying the right amount of tax'>Make sure you are paying the right amount of tax</a></li>
               |      <li><a class="ga-track-anchor-click" href="/check-income-tax/last-year-paye" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='Check how much tax you paid last year'>Check how much tax you paid last year</a></li>
               |      <li><a class="ga-track-anchor-click" href="/check-income-tax/taxable-income" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='View your company benefits'>View your company benefits</a></li>
               |    </ul>
               |  </div>
               |</div>""".stripMargin)
    }

    "return correct markup when called with with a Pertax user that is PAYE without company benefits" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = true
      val hasTaxSummary = true
      val iabdType = 0

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link ga-track-anchor-click" href="/check-income-tax/paye" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='Pay As You Earn (PAYE)'>
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">Pay As You Earn (PAYE)</h3>
               |      <p>Your income from employers and private pensions that is taxed before it is paid to you.</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |    <ul>
               |      <li><a class="ga-track-anchor-click" href="/check-income-tax/income-tax" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='Make sure you are paying the right amount of tax'>Make sure you are paying the right amount of tax</a></li>
               |      <li><a class="ga-track-anchor-click" href="/check-income-tax/last-year-paye" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='Check how much tax you paid last year'>Check how much tax you paid last year</a></li>
               |    </ul>
               |  </div>
               |</div>""".stripMargin)
    }
  }


"Calling getTaxCalculationCard" should {

    trait LocalSetup extends SpecSetup {

      implicit lazy val pertaxContext = PertaxContext(FakeRequest(), mockLocalPartialRetreiver, injected[ConfigDecorator])

      def taxCalcState: TaxCalculationState

      lazy val cardBody = c.getTaxCalculationCard(taxCalcState)(pertaxContext = pertaxContext, messages = messages, messagesApi = messagesApi).map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n")) //remove empty lines
    }

    "return nothing when called with TaxCalculationUnderpaidPaymentsDownState" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPaymentsDownState(2015, 2016)
      cardBody shouldBe None
    }

    "return nothing when called with TaxCalculationUnkownState" in new LocalSetup {

      val taxCalcState = TaxCalculationUnkownState
      cardBody shouldBe None
    }

    "return correct markup when called with TaxCalculationOverpaidRefundState" in new LocalSetup {

      val taxCalcState = TaxCalculationOverpaidRefundState(100, 2015, 2016)

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='You paid too much tax last year'>
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">You paid too much tax last year</h3>
               |      <p>HMRC owes you a £100 refund for the 2015 to 2016 tax year.</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |    <ul>
               |        <li><a class="ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='Claim your tax refund'>Claim your tax refund</a></li>
               |    </ul>
               |  </div>
               |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationOverpaidPaymentProcessingState" in new LocalSetup {

      val taxCalcState = TaxCalculationOverpaidPaymentProcessingState(100)

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='You paid too much tax last year'>
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">You paid too much tax last year</h3>
               |      <p>HMRC is processing your £100 refund.</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |    <ul>
               |        <li><a class="ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='View your tax refund'>View your tax refund</a></li>
               |    </ul>
               |  </div>
               |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationOverpaidPaymentPaidState" in new LocalSetup {

      val taxCalcState = TaxCalculationOverpaidPaymentPaidState(100, "01 Jan 2016")

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='You paid too much tax last year'>
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">You paid too much tax last year</h3>
               |      <p>HMRC has paid your £100 refund.</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |    <ul>
               |        <li><a class="ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='View your tax refund'>View your tax refund</a></li>
               |    </ul>
               |  </div>
               |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationOverpaidPaymentChequeSentState" in new LocalSetup {

      val taxCalcState = TaxCalculationOverpaidPaymentChequeSentState(100, "01 Jan 2016")

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='You paid too much tax last year'>
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">You paid too much tax last year</h3>
               |      <p>HMRC sent you a cheque for £100.</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |    <ul>
               |        <li><a class="ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='View your tax refund'>View your tax refund</a></li>
               |    </ul>
               |  </div>
               |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationUnderpaidPaymentDueState" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPaymentDueState(100, 2015, 2016)

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='You paid too little tax last year'>
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">You paid too little tax last year</h3>
               |      <p>You owe HMRC £100 for the 2015 to 2016 tax year.</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |    <ul>
               |        <li><a class="ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='Find out why you paid too little'>Find out why you paid too little</a></li>
               |    </ul>
               |  </div>
               |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationUnderpaidPartPaidState" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPartPaidState(100, 2015, 2016)

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='You paid too little tax last year'>
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">You paid too little tax last year</h3>
               |      <p>You owe HMRC £100 for the 2015 to 2016 tax year.</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |    <ul>
               |        <li><a class="ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='Find out why you paid too little'>Find out why you paid too little</a></li>
               |    </ul>
               |  </div>
               |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationUnderpaidPaidAllState" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPaidAllState(2015, 2016)

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='You do not owe any more tax'>
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">You do not owe any more tax</h3>
               |      <p>You have no payments to make to HMRC for the 2015 to 2016 tax year.</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |    <ul>
               |        <li><a class="ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='View the tax you paid'>View the tax you paid</a></li>
               |    </ul>
               |  </div>
               |</div>""".stripMargin)
    }

  }


  "Calling getSelfAssessmentCard" should {

    trait LocalSetup extends SpecSetup {

      lazy val configDecorator = {
        val cd = MockitoSugar.mock[ConfigDecorator]
        when(cd.completeYourTaxReturnUrl(any(), any())).thenReturn("/submit/your/return/url")

        cd
      }

      implicit lazy val pertaxContext = PertaxContext(FakeRequest(), mockLocalPartialRetreiver, configDecorator)

      def saUserType: SelfAssessmentUserType

      lazy val cardBody = c.getSelfAssessmentCard(saUserType)(pertaxContext = pertaxContext, messages = messages, messagesApi = messagesApi).map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n")) //remove empty lines
    }

    "return correct markup when called with ActivatedOnlineFilerSelfAssessmentUser" in new LocalSetup {

      val saUserType = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link ga-track-anchor-click" href="/personal-account/self-assessment-summary" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='Self Assessment'>
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">Self Assessment</h3>
               |      <p>
               |          You need to complete a tax return once a year. Yours is due by 31 January 2018.
               |      </p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |    <ul>
               |        <li><a class="ga-track-anchor-click" href="/submit/your/return/url" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='Complete your tax return'>Complete your tax return</a></li>
               |        <li><a class="ga-track-anchor-click" href="/pay-online/self-assessment/make-a-payment?mode=pta" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='Make a payment'>Make a payment</a></li>
               |    </ul>
               |  </div>
               |</div>""".stripMargin)
    }

    "return correct markup when called with NotYetActivatedOnlineFilerSelfAssessmentUser" in new LocalSetup {

      val saUserType = NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link ga-track-anchor-click" href="/personal-account/self-assessment" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='Self Assessment'>
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">Self Assessment</h3>
               |      <p>
               |          Use your activation code to access this service.
               |      </p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |    <ul>
               |        <li><a class="ga-track-anchor-click" href="/personal-account/self-assessment" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='Activate your Self Assessment'>Activate your Self Assessment</a></li>
               |    </ul>
               |  </div>
               |</div>""".stripMargin)
    }


    "return correct markup when called with AmbiguousFilerSelfAssessmentUser" in new LocalSetup {

      val saUserType = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link ga-track-anchor-click" href="/personal-account/self-assessment" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='Self Assessment'>
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">Self Assessment</h3>
               |      <p>
               |          You cannot access this service right now.
               |      </p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |    <ul>
               |        <li><a class="ga-track-anchor-click" href="https://www.gov.uk/self-assessment-tax-returns" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='Understand Self Assessment'>Understand Self Assessment</a></li>
               |    </ul>
               |  </div>
               |</div>""".stripMargin)
    }

    "return nothing when called with NonFilerSelfAssessmentUser" in new LocalSetup {

      val saUserType = NonFilerSelfAssessmentUser

      cardBody shouldBe None
    }

  }


  "Calling getNationalInsuranceCard" should {

    trait LocalSetup extends SpecSetup {

      lazy val cardBody = c.getNationalInsuranceCard()(messages = messages, messagesApi = messagesApi).map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n")) //remove empty lines
    }

    "always return the same thing" in new LocalSetup {

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link ga-track-anchor-click" href="/personal-account/national-insurance-summary" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='National Insurance'>
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">National Insurance</h3>
               |      <p>You have a National Insurance number to make sure your National Insurance contributions and tax are recorded against your name only.</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |    <ul>
               |      <li><a class="ga-track-anchor-click" href="/personal-account/national-insurance-summary/print-letter" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='Print your National Insurance letter'>Print your National Insurance letter</a></li>
               |      <li><a class="ga-track-anchor-click" href="/check-your-state-pension/account/nirecord" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='View gaps in your record'>View gaps in your record</a></li>
               |      <li><a class="ga-track-anchor-click" href="/check-your-state-pension/account/pta" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label='Check your State Pension'>Check your State Pension</a></li>
               |    </ul>
               |  </div>
               |</div>""".stripMargin)
    }
  }


  "Calling getTaxCreditsCard" should {

    trait LocalSetup extends SpecSetup {

      lazy val cardBody = c.getTaxCreditsCard()(messages = messages, messagesApi = messagesApi).map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n")) //remove empty lines
    }

    "always return the same thing" in new LocalSetup {

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link ga-track-anchor-click" href="/tax-credits-service/home" data-ga-event-category="link - click" data-ga-event-action="Benefits" data-ga-event-label='Tax credits'>
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">Tax credits</h3>
               |      <p>View your next payments and the people on your claim, and make changes to your claim.</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |    <ul>
               |      <li><a class="ga-track-anchor-click" href="https://www.gov.uk/qualify-tax-credits" data-ga-event-category="link - click" data-ga-event-action="Benefits" data-ga-event-label='Find out if you qualify for tax credits'>Find out if you qualify for tax credits</a></li>
               |    </ul>
               |  </div>
               |</div>""".stripMargin)
    }
  }


  "Calling getChildBenefitCard" should {

    trait LocalSetup extends SpecSetup {

      lazy val cardBody = c.getChildBenefitCard()(messages = messages, messagesApi = messagesApi).map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n")) //remove empty lines
    }

    "always return the same thing" in new LocalSetup {

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link ga-track-anchor-click" href="/personal-account/child-benefit-forms" data-ga-event-category="link - click" data-ga-event-action="Benefits" data-ga-event-label='Child Benefit'>
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">Child Benefit</h3>
               |      <p>A tax-free payment to help parents with the cost of bringing up children.</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |    <ul>
               |      <li><a class="ga-track-anchor-click" href="/forms/form/Tell-Child-Benefit-about-your-child-staying-in-non-advanced-education-or-approved-training/guide" data-ga-event-category="link - click" data-ga-event-action="Benefits" data-ga-event-label='Tell us if your child is staying in full-time education'>Tell us if your child is staying in full-time education</a></li>
               |      <li><a class="ga-track-anchor-click" href="https://www.gov.uk/child-benefit/eligibility" data-ga-event-category="link - click" data-ga-event-action="Benefits" data-ga-event-label='Find out if you qualify for Child Benefit'>Find out if you qualify for Child Benefit</a></li>
               |    </ul>
               |  </div>
               |</div>""".stripMargin)
    }
  }


  "Calling getMarriageAllowanceCard" should {

    trait LocalSetup extends SpecSetup {

      def hasTaxSummary: Boolean
      def taxCodeEndsWith: String

      lazy val taxSummary = if (hasTaxSummary) Some(Fixtures.buildTaxSummary.copy(taxCodes = Seq("500"+taxCodeEndsWith))) else None
      lazy val cardBody = c.getMarriageAllowanceCard(taxSummary)(messages = messages, messagesApi = messagesApi).map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n")) //remove empty lines
    }


    "return nothing when called with a user who has tax summary and receives Marriage Allowance" in new LocalSetup {
      override val hasTaxSummary: Boolean = true
      override val taxCodeEndsWith = "M"

      cardBody shouldBe None
    }

    "return nothing when called with a user who has tax summary and transfers Marriage Allowance" in new LocalSetup {
      override val hasTaxSummary: Boolean = true
      override val taxCodeEndsWith = "N"

      cardBody shouldBe None
    }

    "return correct markup when called with a user who has no tax summary" in new LocalSetup {

      override val hasTaxSummary = false
      override val taxCodeEndsWith = "unused"

      cardBody shouldBe
        Some(
          """<div class="card column-half">
            |  <a class="card-link ga-track-anchor-click" href="/marriage-allowance-application/history" data-ga-event-category="link - click" data-ga-event-action="Benefits" data-ga-event-label='Marriage Allowance'>
            |    <div class="card-content" role="link">
            |      <h3 class="heading-small no-margin-top">Marriage Allowance</h3>
            |      <p>Transfer part of your Personal Allowance to your partner so they pay less tax.</p>
            |    </div>
            |  </a>
            |  <div class="card-actions">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="/marriage-allowance-application/history" data-ga-event-category="link - click" data-ga-event-action="Benefits" data-ga-event-label='Find out if you qualify for Marriage Allowance'>Find out if you qualify for Marriage Allowance</a></li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return correct markup when called with a user who has tax summary but no marriage allowance" in new LocalSetup {

      override val hasTaxSummary = true
      override val taxCodeEndsWith = "T"

      cardBody shouldBe
        Some(
          """<div class="card column-half">
            |  <a class="card-link ga-track-anchor-click" href="/marriage-allowance-application/history" data-ga-event-category="link - click" data-ga-event-action="Benefits" data-ga-event-label='Marriage Allowance'>
            |    <div class="card-content" role="link">
            |      <h3 class="heading-small no-margin-top">Marriage Allowance</h3>
            |      <p>Transfer part of your Personal Allowance to your partner so they pay less tax.</p>
            |    </div>
            |  </a>
            |  <div class="card-actions">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="/marriage-allowance-application/history" data-ga-event-category="link - click" data-ga-event-action="Benefits" data-ga-event-label='Find out if you qualify for Marriage Allowance'>Find out if you qualify for Marriage Allowance</a></li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }


  }


  "Calling getStatePensionCard" should {

    trait LocalSetup extends SpecSetup {

      lazy val cardBody = c.getStatePensionCard()(messages = messages, messagesApi = messagesApi).map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n")) //remove empty lines
    }

    "always return the same thing" in new LocalSetup {

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link ga-track-anchor-click" href="/check-your-state-pension/account/pta" data-ga-event-category="link - click" data-ga-event-action="Pensions" data-ga-event-label='State Pension'>
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">State Pension</h3>
               |      <p>You are still contributing to your State Pension. Get a forecast and find out when you can start claiming it.</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |    <ul>
               |      <li><a class="ga-track-anchor-click" href="/check-your-state-pension/account/pta" data-ga-event-category="link - click" data-ga-event-action="Pensions" data-ga-event-label='Check your State Pension'>Check your State Pension</a></li>
               |      <li><a class="ga-track-anchor-click" href="/check-your-state-pension/account/nirecord" data-ga-event-category="link - click" data-ga-event-action="Pensions" data-ga-event-label='Check your National Insurance record for gaps'>Check your National Insurance record for gaps</a></li>
               |    </ul>
               |  </div>
               |</div>""".stripMargin)
    }
  }

  "Calling getLifetimeAllowanceProtectionCard" should {

    trait LocalSetup extends SpecSetup {

      def hasLtaProtections: Boolean

      lazy val cardBody = c.getLifetimeAllowanceProtectionCard(hasLtaProtections)(messages = messages, messagesApi = messagesApi).map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n")) //remove empty lines
    }

    "return nothing when called with a user who does not have lta protections" in new LocalSetup {

      override val hasLtaProtections = false

      cardBody shouldBe None
    }

    "return the correct markup when called with a user who has lta protections" in new LocalSetup {

      override val hasLtaProtections = true

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link ga-track-anchor-click" href="/protect-your-lifetime-allowance/existing-protections" data-ga-event-category="link - click" data-ga-event-action="Pensions" data-ga-event-label='Lifetime allowance protection'>
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">Lifetime allowance protection</h3>
               |      <p>Your lifetime allowance is protected from additional tax charges.</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |    <ul>
               |      <li><a class="ga-track-anchor-click" href="/protect-your-lifetime-allowance/existing-protections" data-ga-event-category="link - click" data-ga-event-action="Pensions" data-ga-event-label='label.manage_your_protection'>Manage your protections</a></li>
               |    </ul>
               |  </div>
               |</div>""".stripMargin)
    }
  }

}
