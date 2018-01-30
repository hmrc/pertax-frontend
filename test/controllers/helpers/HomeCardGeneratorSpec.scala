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
      def iabdType: Int
      def taxSummaryState: TaxSummaryState

      lazy val fakeTaxSummary = Fixtures.buildTaxSummary.copy(companyBenefits = Seq(iabdType))

      lazy val pertaxUser = if(hasPertaxUser)
        Some(PertaxUser(Fixtures.buildFakeAuthContext(withPaye = isPayeUser),UserDetails(UserDetails.GovernmentGatewayAuthProvider),None, true))
      else None

      lazy val cardBody = c.getPayAsYouEarnCard(pertaxUser, taxSummaryState).map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n")) //remove empty lines
    }

    "return nothing when called with no Pertax user" in new LocalSetup {
      val hasPertaxUser = false
      val isPayeUser = false
      val iabdType = 0
      val taxSummaryState = TaxSummaryUnreachableState

      cardBody shouldBe None

    }

    "return nothing when called with a Pertax user that is not PAYE" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = false
      val iabdType = 0
      val taxSummaryState = TaxSummaryUnreachableState

      cardBody shouldBe None
    }

    "return no content when called with with a Pertax user that is PAYE but has no tax summary" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = true
      val iabdType = 0
      val taxSummaryState = TaxSummaryNotAvailiableState

      cardBody shouldBe None
    }

    "return the static version of the markup (no card actions) when called with with a Pertax user that is PAYE but there was an error calling the endpoint" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = true
      val iabdType = 0
      val taxSummaryState = TaxSummaryUnreachableState

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/check-income-tax/what-do-you-want-to-do" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Pay As You Earn (PAYE)">
            |          Pay As You Earn (PAYE)
            |        </a>
            |    </h3>
            |    <p>Your income from employers and private pensions that is taxed before it is paid to you.</p>
            |  </div>
            |  <div class="card-action">
            |  </div>
            |</div>""".stripMargin)
    }

    "return the static version of the markup (no card actions) when called with with a Pertax user that is PAYE but the tax summary call is disabled" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = true
      //      val hasTaxSummary = false
      val iabdType = 0
      val taxSummaryState = TaxSummaryDisabledState

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/check-income-tax/what-do-you-want-to-do" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Pay As You Earn (PAYE)">
            |          Pay As You Earn (PAYE)
            |        </a>
            |    </h3>
            |    <p>Your income from employers and private pensions that is taxed before it is paid to you.</p>
            |  </div>
            |  <div class="card-action">
            |  </div>
            |</div>""".stripMargin)
    }

    "return correct markup when called with with a Pertax user that is PAYE with company benefits" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = true
      val iabdType = 31
      val taxSummaryState = TaxSummaryAvailiableState(fakeTaxSummary)

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/check-income-tax/what-do-you-want-to-do" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Pay As You Earn (PAYE)">
            |          Pay As You Earn (PAYE)
            |        </a>
            |    </h3>
            |    <p>Your income from employers and private pensions that is taxed before it is paid to you.</p>
            |  </div>
            |  <div class="card-action">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="/check-income-tax/income-tax" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="View your Income Tax estimate">View your Income Tax estimate</a></li>
            |      <li><a class="ga-track-anchor-click" href="/check-income-tax/last-year-paye" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Check how much tax you paid last year">Check how much tax you paid last year</a></li>
            |      <li><a class="ga-track-anchor-click" href="/check-income-tax/taxable-income" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="View your company benefits">View your company benefits</a></li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return correct markup when called with with a Pertax user that is PAYE without company benefits" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = true
      val iabdType = 0
      val taxSummaryState = TaxSummaryAvailiableState(fakeTaxSummary)

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/check-income-tax/what-do-you-want-to-do" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Pay As You Earn (PAYE)">
            |          Pay As You Earn (PAYE)
            |        </a>
            |    </h3>
            |    <p>Your income from employers and private pensions that is taxed before it is paid to you.</p>
            |  </div>
            |  <div class="card-action">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="/check-income-tax/income-tax" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="View your Income Tax estimate">View your Income Tax estimate</a></li>
            |      <li><a class="ga-track-anchor-click" href="/check-income-tax/last-year-paye" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Check how much tax you paid last year">Check how much tax you paid last year</a></li>
            |      <li><a class="ga-track-anchor-click" href="/check-income-tax/tax-codes" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Understand your tax code">Understand your tax code</a></li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }
  }


  "Calling getTaxCalculationCard" should {

    trait LocalSetup extends SpecSetup {

      implicit lazy val pertaxContext = PertaxContext(FakeRequest(), mockLocalPartialRetreiver, injected[ConfigDecorator])

      def taxCalcState: TaxCalculationState

      lazy val cardBody = c.getTaxCalculationCard(Some(taxCalcState)).map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n")) //remove empty lines
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
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="You paid too much tax last year">
            |          You paid too much tax last year
            |        </a>
            |    </h3>
            |    <p>HMRC owes you a £100 refund for the 2015 to 2016 tax year.</p>
            |  </div>
            |  <div class="card-action">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Claim your tax refund">Claim your tax refund</a></li>
            |      <li><a class="ga-track-anchor-click" href="/tax-you-paid/paid-too-much/reasons" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Find out why you paid too much">Find out why you paid too much</a></li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationOverpaidPaymentProcessingState" in new LocalSetup {

      val taxCalcState = TaxCalculationOverpaidPaymentProcessingState(100)

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="You paid too much tax last year">
            |          You paid too much tax last year
            |        </a>
            |    </h3>
            |    <p>HMRC is processing your £100 refund.</p>
            |  </div>
            |  <div class="card-action">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="/tax-you-paid/paid-too-much/reasons" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Find out why you paid too much">Find out why you paid too much</a></li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationOverpaidPaymentPaidState" in new LocalSetup {

      val taxCalcState = TaxCalculationOverpaidPaymentPaidState(100, "01 Jan 2016")

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="You paid too much tax last year">
            |          You paid too much tax last year
            |        </a>
            |    </h3>
            |    <p>HMRC has paid your £100 refund.</p>
            |  </div>
            |  <div class="card-action">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="/tax-you-paid/paid-too-much/reasons" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Find out why you paid too much">Find out why you paid too much</a></li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationOverpaidPaymentChequeSentState" in new LocalSetup {

      val taxCalcState = TaxCalculationOverpaidPaymentChequeSentState(100, "01 Jan 2016")

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="You paid too much tax last year">
            |          You paid too much tax last year
            |        </a>
            |    </h3>
            |    <p>HMRC sent you a cheque for £100.</p>
            |  </div>
            |  <div class="card-action">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="/tax-you-paid/paid-too-much/reasons" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Find out why you paid too much">Find out why you paid too much</a></li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationUnderpaidPaymentDueState with no SaDeadlineStatus or due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPaymentDueState(100, 2015, 2016, None, None)

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="You paid too little tax last year">
            |          You paid too little tax last year
            |        </a>
            |    </h3>
            |    <p>You owe HMRC £100 for the 2015 to 2016 tax year.</p>
            |  </div>
            |  <div class="card-action">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="/tax-you-paid/paid-too-little/reasons" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Find out why you paid too little">Find out why you paid too little</a></li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationUnderpaidPaymentDueState with no SaDeadlineStatus and due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPaymentDueState(100, 2015, 2016, Some("31 January 2016"), None)

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="You paid too little tax last year">
            |          You paid too little tax last year
            |        </a>
            |    </h3>
            |    <p>You owe HMRC £100 for the 2015 to 2016 tax year. You must pay by 31 January 2016.</p>
            |  </div>
            |  <div class="card-action">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="https://www.gov.uk/simple-assessment" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Make a payment">Make a payment</a></li>
            |      <li><a class="ga-track-anchor-click" href="/tax-you-paid/paid-too-little/reasons" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Find out why you paid too little">Find out why you paid too little</a></li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationUnderpaidPaymentDueState with SaDeadlineStatus of SaDeadlineApproaching and due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPaymentDueState(100, 2015, 2016, Some("31 January 2016"), Some(SaDeadlineApproachingStatus))

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="You paid too little tax last year">
            |          You paid too little tax last year
            |        </a>
            |    </h3>
            |    <p>You owe HMRC £100 for the 2015 to 2016 tax year. You must pay by 31 January 2016.</p>
            |  </div>
            |  <div class="card-action">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="https://www.gov.uk/simple-assessment" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Make a payment">Make a payment</a></li>
            |      <li><a class="ga-track-anchor-click" href="/tax-you-paid/paid-too-little/reasons" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Find out why you paid too little">Find out why you paid too little</a></li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationUnderpaidPaymentDueState with SaDeadlineStatus of SaDeadlinePassed and due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPaymentDueState(100, 2015, 2016, Some("31 January 2016"), Some(SaDeadlinePassedStatus))

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="You missed the deadline to pay your tax">
            |          You missed the deadline to pay your tax
            |        </a>
            |    </h3>
            |    <p>You owe HMRC £100 for the 2015 to 2016 tax year. You should have paid by 31 January 2016 but you can still make a payment now.</p>
            |  </div>
            |  <div class="card-action">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="https://www.gov.uk/simple-assessment" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="You missed the deadline to pay your tax">Make a payment</a></li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationUnderpaidPartPaidState with no SaDeadlineStatus or due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPartPaidState(100, 2015, 2016, None, None)

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="You paid too little tax last year">
            |          You paid too little tax last year
            |        </a>
            |    </h3>
            |    <p>You owe HMRC £100 for the 2015 to 2016 tax year.</p>
            |  </div>
            |  <div class="card-action">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="/tax-you-paid/paid-too-little/reasons" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Find out why you paid too little">Find out why you paid too little</a></li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationUnderpaidPartPaidState with no SaDeadlineStatus and due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPartPaidState(100, 2015, 2016, Some("31 January 2016"), None)

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="You paid too little tax last year">
            |          You paid too little tax last year
            |        </a>
            |    </h3>
            |    <p>You still owe HMRC £100 for the 2015 to 2016 tax year. You must pay by 31 January 2016.</p>
            |  </div>
            |  <div class="card-action">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="https://www.gov.uk/simple-assessment" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Make a payment">Make a payment</a></li>
            |      <li><a class="ga-track-anchor-click" href="/tax-you-paid/paid-too-little/reasons" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Find out why you paid too little">Find out why you paid too little</a></li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationUnderpaidPartPaidState with SaDeadlineStatus of SaDeadlineApproaching and due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPartPaidState(100, 2015, 2016, Some("31 January 2016"), Some(SaDeadlineApproachingStatus))
      
      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="You paid too little tax last year">
            |          You paid too little tax last year
            |        </a>
            |    </h3>
            |    <p>You still owe HMRC £100 for the 2015 to 2016 tax year. You must pay by 31 January 2016.</p>
            |  </div>
            |  <div class="card-action">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="https://www.gov.uk/simple-assessment" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Make a payment">Make a payment</a></li>
            |      <li><a class="ga-track-anchor-click" href="/tax-you-paid/paid-too-little/reasons" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Find out why you paid too little">Find out why you paid too little</a></li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationUnderpaidPartPaidState with SaDeadlineStatus of SaDeadlinePassed and due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPartPaidState(100, 2015, 2016, Some("31 January 2016"), Some(SaDeadlinePassedStatus))

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="You missed the deadline to pay your tax">
            |          You missed the deadline to pay your tax
            |        </a>
            |    </h3>
            |    <p>You still owe HMRC £100 for the 2015 to 2016 tax year. You should have paid by 31 January 2016 but you can still make a payment now.</p>
            |  </div>
            |  <div class="card-action">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="https://www.gov.uk/simple-assessment" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="You missed the deadline to pay your tax">Make a payment</a></li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationUnderpaidPaidAllState with no due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPaidAllState(2015, 2016, None)

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="You do not owe any more tax">
            |          You do not owe any more tax
            |        </a>
            |    </h3>
            |    <p>You have no payments to make to HMRC for the 2015 to 2016 tax year.</p>
            |  </div>
            |  <div class="card-action">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="/tax-you-paid/paid-too-little/reasons" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Find out why you paid too little">Find out why you paid too little</a></li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationUnderpaidPaidAllState with a due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPaidAllState(2015, 2016, Some("01/01/2016"))

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="You do not owe any more tax">
            |          You do not owe any more tax
            |        </a>
            |    </h3>
            |    <p>You have no payments to make to HMRC for the 2015 to 2016 tax year.</p>
            |  </div>
            |  <div class="card-action">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="/tax-you-paid/paid-too-little/reasons" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Find out why you paid too little">Find out why you paid too little</a></li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return correct markup when called with TaxCalculationDisabledState" in new LocalSetup {
      val taxCalcState = TaxCalculationDisabledState(2015, 2016)

      cardBody shouldBe
        Some("""<div class="card">
               |  <div class="card-body active">
               |    <h3 class="heading-small card-heading">
               |        <a class="card-link ga-track-anchor-click" href="/tax-you-paid/status" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Your Income Tax for last year">
               |          Your Income Tax for last year
               |        </a>
               |    </h3>
               |    <p>Check to see if you paid the right amount of tax from 6 April 2015 to 5 April 2016.</p>
               |  </div>
               |  <div class="card-action">
               |    <ul>
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

      implicit lazy val pertaxContext = PertaxContext(FakeRequest(), mockLocalPartialRetreiver, configDecorator, pertaxUser)

      def saUserType: SelfAssessmentUserType

      lazy val pertaxUser = Some(PertaxUser(Fixtures.buildFakeAuthContext(),UserDetails(UserDetails.GovernmentGatewayAuthProvider),None, true))

      lazy val cardBody = c.getSelfAssessmentCard(saUserType).map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n")) //remove empty lines
    }

    "return correct markup when called with ActivatedOnlineFilerSelfAssessmentUser" in new LocalSetup {

      val saUserType = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/personal-account/self-assessment-summary" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Self Assessment">
            |          Self Assessment
            |        </a>
            |    </h3>
            |    <p>View and manage your Self Assessment tax return. The deadline for online returns is 31 January 2018.</p>
            |  </div>
            |  <div class="card-action">
            |      <ul>
            |        <li><a class="ga-track-anchor-click" href="/pay-online/self-assessment/make-a-payment?mode=pta" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Make a payment">Make a payment</a></li>
            |      </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return correct markup when called with NotYetActivatedOnlineFilerSelfAssessmentUser" in new LocalSetup {

      val saUserType = NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/personal-account/self-assessment" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Self Assessment">
            |          Self Assessment
            |        </a>
            |    </h3>
            |    <p>Use your activation code to access this service. The code is on the letter we sent to you when you enrolled.</p>
            |  </div>
            |  <div class="card-action">
            |      <ul>
            |        <li><a class="ga-track-anchor-click" href="/personal-account/self-assessment" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Activate your Self Assessment">Activate your Self Assessment</a></li>
            |      </ul>
            |  </div>
            |</div>""".stripMargin)
    }


    "return correct markup when called with AmbiguousFilerSelfAssessmentUser" in new LocalSetup {

      val saUserType = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/personal-account/self-assessment" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Self Assessment">
            |          Self Assessment
            |        </a>
            |    </h3>
            |    <p>You cannot use this service. You may not be enrolled for Self Assessment, or you may have enrolled using a different account.</p>
            |  </div>
            |  <div class="card-action">
            |      <ul>
            |        <li><a class="ga-track-anchor-click" href="/personal-account/self-assessment" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Find out how to access Self Assessment">Find out how to access Self Assessment</a></li>
            |      </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return nothing when called with NonFilerSelfAssessmentUser" in new LocalSetup {

      val saUserType = NonFilerSelfAssessmentUser

      cardBody shouldBe None
    }

    "return nothing for a verify user" in new LocalSetup {
      val saUserType = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      override lazy val pertaxUser = Some(PertaxUser(Fixtures.buildFakeAuthContext(),UserDetails(UserDetails.VerifyAuthProvider),None, true))

      cardBody shouldBe None
    }
  }


  "Calling getNationalInsuranceCard" should {

    trait LocalSetup extends SpecSetup {

      lazy val cardBody = c.getNationalInsuranceCard().map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n")) //remove empty lines
    }

    "always return the same markup" in new LocalSetup {

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/personal-account/national-insurance-summary" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="National Insurance">
            |          National Insurance
            |        </a>
            |    </h3>
            |    <p>You have a National Insurance number to make sure your National Insurance contributions and tax are recorded against your name only.</p>
            |  </div>
            |  <div class="card-action">
            |      <ul>
            |        <li><a class="ga-track-anchor-click" href="/personal-account/national-insurance-summary/print-letter" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="Print your National Insurance number">Print your National Insurance number</a></li>
            |        <li><a class="ga-track-anchor-click" href="/check-your-state-pension/account/nirecord/pta" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="View gaps in your record">View gaps in your record</a></li>
            |      </ul>
            |  </div>
            |</div>""".stripMargin)
    }
  }


  "Calling getTaxCreditsCard" should {

    trait LocalSetup extends SpecSetup {

      lazy val cardBody = c.getTaxCreditsCard().map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n")) //remove empty lines
    }

    "always return the same markup" in new LocalSetup {

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/tax-credits-service/home" data-ga-event-category="link - click" data-ga-event-action="Benefits" data-ga-event-label="Tax credits">
            |          Tax credits
            |        </a>
            |    </h3>
            |    <p>View your next payments and the people on your claim, and make changes to your claim.</p>
            |  </div>
            |  <div class="card-action">
            |      <ul>
            |        <li><a class="ga-track-anchor-click" href="/tax-credits-service/home/payment-schedule" data-ga-event-category="link - click" data-ga-event-action="Benefits" data-ga-event-label="View your tax credits payments">View your tax credits payments</a></li>
            |      </ul>
            |  </div>
            |</div>""".stripMargin)
    }
  }


  "Calling getChildBenefitCard" should {

    trait LocalSetup extends SpecSetup {

      lazy val cardBody = c.getChildBenefitCard().map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n")) //remove empty lines
    }

    "always return the same markup" in new LocalSetup {

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/personal-account/child-benefit-forms" data-ga-event-category="link - click" data-ga-event-action="Benefits" data-ga-event-label="Child Benefit">
            |          Child Benefit
            |        </a>
            |    </h3>
            |    <p>A tax-free payment to help parents with the cost of bringing up children.</p>
            |  </div>
            |  <div class="card-action">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="/forms/form/Tell-Child-Benefit-about-your-child-staying-in-non-advanced-education-or-approved-training/guide" data-ga-event-category="link - click" data-ga-event-action="Benefits" data-ga-event-label="Tell us if your child is staying in full-time education">Tell us if your child is staying in full-time education</a></li>
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
      lazy val cardBody = c.getMarriageAllowanceCard(taxSummary).map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n")) //remove empty lines
    }


    "return correct markup when called with a user who has tax summary and receives Marriage Allowance" in new LocalSetup {
      override val hasTaxSummary: Boolean = true
      override val taxCodeEndsWith = "M"

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/marriage-allowance-application/history" data-ga-event-category="link - click" data-ga-event-action="Benefits" data-ga-event-label="Marriage Allowance">
            |          Marriage Allowance
            |        </a>
            |    </h3>
            |    <p>Your partner currently transfers part of their Personal Allowance to you.</p>
            |  </div>
            |  <div class="card-action">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="/marriage-allowance-application/make-changes" data-ga-event-category="link - click" data-ga-event-action="Benefits" data-ga-event-label="Manage your Marriage Allowance">Manage your Marriage Allowance</a></li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return nothing when called with a user who has tax summary and transfers Marriage Allowance" in new LocalSetup {
      override val hasTaxSummary: Boolean = true
      override val taxCodeEndsWith = "N"

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/marriage-allowance-application/history" data-ga-event-category="link - click" data-ga-event-action="Benefits" data-ga-event-label="Marriage Allowance">
            |          Marriage Allowance
            |        </a>
            |    </h3>
            |    <p>You currently transfer part of your Personal Allowance to your partner.</p>
            |  </div>
            |  <div class="card-action">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="/marriage-allowance-application/make-changes" data-ga-event-category="link - click" data-ga-event-action="Benefits" data-ga-event-label="Manage your Marriage Allowance">Manage your Marriage Allowance</a></li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return correct markup when called with a user who has no tax summary" in new LocalSetup {

      override val hasTaxSummary = false
      override val taxCodeEndsWith = "unused"

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/marriage-allowance-application/history" data-ga-event-category="link - click" data-ga-event-action="Benefits" data-ga-event-label="Marriage Allowance">
            |          Marriage Allowance
            |        </a>
            |    </h3>
            |    <p>Transfer part of your Personal Allowance to your partner so they pay less tax.</p>
            |  </div>
            |  <div class="card-action">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="/marriage-allowance-application/how-it-works" data-ga-event-category="link - click" data-ga-event-action="Benefits" data-ga-event-label="Find out if you qualify for Marriage Allowance">Find out if you qualify for Marriage Allowance</a></li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return correct markup when called with a user who has tax summary but no marriage allowance" in new LocalSetup {

      override val hasTaxSummary = true
      override val taxCodeEndsWith = "T"

      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/marriage-allowance-application/history" data-ga-event-category="link - click" data-ga-event-action="Benefits" data-ga-event-label="Marriage Allowance">
            |          Marriage Allowance
            |        </a>
            |    </h3>
            |    <p>Transfer part of your Personal Allowance to your partner so they pay less tax.</p>
            |  </div>
            |  <div class="card-action">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="/marriage-allowance-application/how-it-works" data-ga-event-category="link - click" data-ga-event-action="Benefits" data-ga-event-label="Find out if you qualify for Marriage Allowance">Find out if you qualify for Marriage Allowance</a></li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

  }


  "Calling getStatePensionCard" should {

    trait LocalSetup extends SpecSetup {

      lazy val cardBody = c.getStatePensionCard().map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n")) //remove empty lines
    }

    "always return the same markup" in new LocalSetup {
      
      cardBody shouldBe
        Some(
          """<div class="card">
            |  <div class="card-body active">
            |    <h3 class="heading-small card-heading">
            |        <a class="card-link ga-track-anchor-click" href="/check-your-state-pension/account/pta" data-ga-event-category="link - click" data-ga-event-action="Pensions" data-ga-event-label="State Pension">
            |          State Pension
            |        </a>
            |    </h3>
            |    <p>View your State Pension and National Insurance contributions.</p>
            |  </div>
            |  <div class="card-action">
            |    <ul>
            |      <li><a class="ga-track-anchor-click" href="/check-your-state-pension/account/pta" data-ga-event-category="link - click" data-ga-event-action="Pensions" data-ga-event-label="View your State Pension forecast">View your State Pension forecast</a></li>
            |      <li><a class="ga-track-anchor-click" href="/check-your-state-pension/account/nirecord/pta" data-ga-event-category="link - click" data-ga-event-action="Pensions" data-ga-event-label="View your National Insurance record">View your National Insurance record</a></li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }
  }
}
