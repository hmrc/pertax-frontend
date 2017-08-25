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
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.SaUtr
import util.{BaseSpec, Fixtures}
import org.mockito.Matchers._
import org.mockito.Mockito._

class HomeCardGeneratorSpec extends BaseSpec {

  trait SpecSetup extends I18nSupport {

    override def messagesApi: MessagesApi = injected[MessagesApi]

    val c = new HomeCardGenerator
  }


  "Calling getPayAsYouEarnCard" should {

    trait LocalSetup extends SpecSetup {

      def hasPertaxUser: Boolean
      def isPayeUser: Boolean
      def hasTaxSummary: Boolean

      lazy val taxSummary = if(hasTaxSummary) Some(Fixtures.buildTaxSummary) else None

      lazy val pertaxUser = if(hasPertaxUser)
        Some(PertaxUser(Fixtures.buildFakeAuthContext(withPaye = isPayeUser),UserDetails(UserDetails.GovernmentGatewayAuthProvider),None, true))
      else None

      lazy val cardBody = c.getPayAsYouEarnCard(pertaxUser, taxSummary).map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n"))  //remove empty lines
    }

    "return nothing when called with no Pertax user" in new LocalSetup {
      val hasPertaxUser = false
      val isPayeUser = false
      val hasTaxSummary = false

      cardBody shouldBe None
    }

    "return nothing when called with a Pertax user that is not PAYE" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = false
      val hasTaxSummary = false

      cardBody shouldBe None
    }

    "return nothing when called with with a Pertax user that is PAYE but has no tax summary" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = true
      val hasTaxSummary = false

      cardBody shouldBe None
    }

    "return correct markup when called with with a Pertax user that is PAYE and has tax summary" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = true
      val hasTaxSummary = true

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link" href="/check-income-tax/paye">
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">Pay As You Earn (PAYE)</h3>
               |      <p>Your income from employers and private pensions that is taxed before it is paid to you.</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |      <a href="/check-income-tax/last-year-paye">Check how much tax I paid last year</a>
               |  </div>
               |</div>""".stripMargin)
    }
  }


  "Calling getTaxCalculationCard" should {

    trait LocalSetup extends SpecSetup {

      def taxCalcState: TaxCalculationState

      lazy val cardBody = c.getTaxCalculationCard(taxCalcState).map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n"))  //remove empty lines
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
               |  <a class="card-link" href="/check-income-tax/paye">
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">You paid too much tax last year</h3>
               |      <p>HMRC owes you a £100 refund for the 2015 to 2016 year</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |      <a href="/check-income-tax/income-tax">Claim your tax refund</a>
               |    <a href="/check-income-tax/income-tax">Get more information about the tax you paid</a>
               |  </div>
               |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationOverpaidPaymentProcessingState" in new LocalSetup {

      val taxCalcState = TaxCalculationOverpaidPaymentProcessingState(100)

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link" href="/check-income-tax/paye">
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">You paid too much tax last year</h3>
               |      <p>HMRC is processing your £100 refund</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |      <a href="/check-income-tax/income-tax">View your tax refund</a>
               |    <a href="/check-income-tax/income-tax">Get more information about the tax you paid</a>
               |  </div>
               |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationOverpaidPaymentPaidState" in new LocalSetup {

      val taxCalcState = TaxCalculationOverpaidPaymentPaidState(100, "01 Jan 2016")

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link" href="/check-income-tax/paye">
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">You paid too much tax last year</h3>
               |      <p>HMRC has paid your £100 refund</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |      <a href="/check-income-tax/income-tax">View your tax refund</a>
               |    <a href="/check-income-tax/income-tax">Get more information about the tax you paid</a>
               |  </div>
               |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationOverpaidPaymentChequeSentState" in new LocalSetup {

      val taxCalcState = TaxCalculationOverpaidPaymentChequeSentState(100, "01 Jan 2016")

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link" href="/check-income-tax/paye">
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">You paid too much tax last year</h3>
               |      <p>HMRC sent you a cheque for £100</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |      <a href="/check-income-tax/income-tax">View your tax refund</a>
               |    <a href="/check-income-tax/income-tax">Get more information about the tax you paid</a>
               |  </div>
               |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationUnderpaidPaymentDueState" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPaymentDueState(100, 2015, 2016)

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link" href="/check-income-tax/paye">
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">You paid too little tax last year</h3>
               |      <p>You owe HMRC £100 for the 2015 to 2016 tax year</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |      <a href="/check-income-tax/income-tax">Find out why you paid too little</a>
               |    <a href="/check-income-tax/income-tax">Get more information about the tax you paid</a>
               |  </div>
               |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationUnderpaidPartPaidState" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPartPaidState(100, 2015, 2016)

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link" href="/check-income-tax/paye">
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">You paid too little tax last year</h3>
               |      <p>You owe HMRC £100 for the 2015 to 2016 tax year</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |      <a href="/check-income-tax/income-tax">Find out why you paid too little</a>
               |    <a href="/check-income-tax/income-tax">Get more information about the tax you paid</a>
               |  </div>
               |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationUnderpaidPaidAllState" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPaidAllState(2015, 2016)

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link" href="/check-income-tax/paye">
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">You do not owe any more tax</h3>
               |      <p>You have no payments to make to HMRC for the 2015 to 2016 tax year</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |      <a href="/check-income-tax/income-tax">View the tax you paid</a>
               |    <a href="/check-income-tax/income-tax">Get more information about the tax you paid</a>
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

      lazy val cardBody = c.getSelfAssessmentCard(saUserType).map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n"))  //remove empty lines
    }

    "return correct markup when called with ActivatedOnlineFilerSelfAssessmentUser" in new LocalSetup {

      val saUserType = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link" href="/personal-account/self-assessment-summary">
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">Self Assessment</h3>
               |      <p>You need to complete a tax return once a year. Yours is due by the 31 January 2018. The last day you can do this online is 28 January 2018.</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |        <a href="/submit/your/return/url">Complete your tax return</a>
               |  </div>
               |</div>""".stripMargin)
    }

    "return correct markup when called with NotYetActivatedOnlineFilerSelfAssessmentUser" in new LocalSetup {

      val saUserType = NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link" href="/personal-account/self-assessment">
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">Self Assessment</h3>
               |      <p>You need to complete a tax return once a year. Yours is due by the 31 January 2018. The last day you can do this online is 28 January 2018.</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |        <a href="/pay-online/self-assessment/make-a-payment?mode=pta">Make a payment</a>
               |  </div>
               |</div>""".stripMargin)
    }


    "return correct markup when called with AmbiguousFilerSelfAssessmentUser" in new LocalSetup {

      val saUserType = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link" href="/personal-account/self-assessment">
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">Self Assessment</h3>
               |      <p>You need to complete a tax return once a year. Yours is due by the 31 January 2018. The last day you can do this online is 28 January 2018.</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |        <a href="https://www.gov.uk/self-assessment-tax-returns">Understand Self Assessment</a>
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

      lazy val cardBody = c.getNationalInsuranceCard().map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n"))  //remove empty lines
    }

    "always return the same thing" in new LocalSetup {

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link" href="/personal-account/national-insurance-summary">
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">National Insurance</h3>
               |      <p>You have a National Insurance number to make sure our National Insurance contributions and tax are recorded against your name only.</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |      <a href="/personal-account/national-insurance-summary/print-letter">Print your National Insurance letter</a>
               |      <a href="/personal-account/self-assessment">Make a payment</a>
               |      <a href="/personal-account/self-assessment">Understand Self Assessment</a>
               |  </div>
               |</div>""".stripMargin)
    }
  }


  "Calling getTaxCreditsCard" should {

    trait LocalSetup extends SpecSetup {

      lazy val cardBody = c.getTaxCreditsCard().map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n"))  //remove empty lines
    }

    "always return the same thing" in new LocalSetup {

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link" href="/tax-credits-service/home">
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">Tax credits</h3>
               |      <p>View your next payments and the people on your claim, and make changes to your claim.</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |      <a href="https://www.gov.uk/qualify-tax-credits">Find out if you qualify for tax credits</a>
               |  </div>
               |</div>""".stripMargin)
    }
  }


  "Calling getChildBenefitCard" should {

    trait LocalSetup extends SpecSetup {

      lazy val cardBody = c.getChildBenefitCard().map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n"))  //remove empty lines
    }

    "always return the same thing" in new LocalSetup {

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link" href="/personal-account/child-benefit-forms">
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">Child Benefit</h3>
               |      <p>A tax-free payment to help parents with the cost of bringing up children.</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |      <a href="/forms/form/Tell-Child-Benefit-about-your-child-staying-in-non-advanced-education-or-approved-training/guide">Tell us if your child is staying in full-time education</a>
               |      <a href="https://www.gov.uk/child-benefit/eligibility">Find out if you qualify for Child Benefit</a>
               |  </div>
               |</div>""".stripMargin)
    }
  }


  "Calling getMarriageAllowanceCard" should {

    trait LocalSetup extends SpecSetup {

      def hasTaxSummary: Boolean
      def taxCodeEndsWith: String

      lazy val taxSummary = if (hasTaxSummary) Some(Fixtures.buildTaxSummary.copy(taxCodes = Seq("500"+taxCodeEndsWith))) else None
      lazy val cardBody = c.getMarriageAllowanceCard(taxSummary).map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n")) //remove empty lines
    }


    "return nothing when called with a user who has Tax Summary and receives Marriage Allowance" in new LocalSetup {
      override val hasTaxSummary: Boolean = true
      override val taxCodeEndsWith = "M"

      cardBody shouldBe None
    }

    "return nothing when called with a user who has Tax Summary and transfers Marriage Allowance" in new LocalSetup {
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
            |  <a class="card-link" href="/marriage-allowance-application/history">
            |    <div class="card-content" role="link">
            |      <h3 class="heading-small no-margin-top">Marriage Allowance</h3>
            |      <p>Transfer part of your Personal Allowance to your partner so they pay less tax.</p>
            |    </div>
            |  </a>
            |  <div class="card-actions">
            |    <a href="/marriage-allowance-application/history">Find out if you qualify for Marriage Allowance</a>
            |  </div>
            |</div>""".stripMargin)
    }

    "return correct markup when called with a user who has tax summary but no marriage allowance" in new LocalSetup {

      override val hasTaxSummary = true
      override val taxCodeEndsWith = "T"

      cardBody shouldBe
        Some(
          """<div class="card column-half">
            |  <a class="card-link" href="/marriage-allowance-application/history">
            |    <div class="card-content" role="link">
            |      <h3 class="heading-small no-margin-top">Marriage Allowance</h3>
            |      <p>Transfer part of your Personal Allowance to your partner so they pay less tax.</p>
            |    </div>
            |  </a>
            |  <div class="card-actions">
            |    <a href="/marriage-allowance-application/history">Find out if you qualify for Marriage Allowance</a>
            |  </div>
            |</div>""".stripMargin)
    }


  }


  "Calling getStatePensionCard" should {

    trait LocalSetup extends SpecSetup {

      lazy val cardBody = c.getStatePensionCard().map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n"))  //remove empty lines
    }

    "always return the same thing" in new LocalSetup {

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link" href="/check-your-state-pension/account/pta">
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">State Pension</h3>
               |      <p>You are still contributing to your State Pension. Get a forecast and find out when you can start claiming it.</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |      <a href="/check-your-state-pension/account/pta">Check your State Pension</a>
               |      <a href="/check-your-state-pension/account/nirecord">Check your National Insurance record for gaps</a>
               |  </div>
               |</div>""".stripMargin)
    }
  }

  "Calling getLifetimeAllowanceProtectionCard" should {

    trait LocalSetup extends SpecSetup {

      def hasLtaProtections: Boolean

      lazy val cardBody = c.getLifetimeAllowanceProtectionCard(hasLtaProtections).map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n"))  //remove empty lines
    }

    "return nothing when called with a user who does not have lta protections" in new LocalSetup {

      override val hasLtaProtections = false

      cardBody shouldBe None
    }

    "return the correct markup when called with a user who has lta protections" in new LocalSetup {

      override val hasLtaProtections = true

      cardBody shouldBe
        Some("""<div class="card column-half">
               |  <a class="card-link" href="/check-your-state-pension/account/pta">
               |    <div class="card-content" role="link">
               |      <h3 class="heading-small no-margin-top">Lifetime allowance protection</h3>
               |      <p>Your Lifetime Allowance is protected from additional tax charges.</p>
               |    </div>
               |  </a>
               |  <div class="card-actions">
               |      <a href="/protect-your-lifetime-allowance/existing-protections">Manage your protections</a>
               |  </div>
               |</div>""".stripMargin)
    }
  }

}
