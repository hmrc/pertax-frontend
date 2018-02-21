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
import util.{BaseSpec, Fixtures, DateTimeTools}
import org.mockito.Matchers._
import org.mockito.Mockito._
import views.html.cards.home._


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
      def isCompanyBenefits: Boolean
      def displayCardActions: Boolean

      lazy val fakeTaxSummary = Fixtures.buildTaxSummary.copy(companyBenefits = Seq(iabdType))

      lazy val pertaxUser = if(hasPertaxUser)
        Some(PertaxUser(Fixtures.buildFakeAuthContext(withPaye = isPayeUser),UserDetails(UserDetails.GovernmentGatewayAuthProvider),None, true))
      else None

      lazy val cardBody = c.getPayAsYouEarnCard(pertaxUser, taxSummaryState)
    }

    "return nothing when called with no Pertax user" in new LocalSetup {
      val hasPertaxUser = false
      val isPayeUser = false
      val iabdType = 0
      val taxSummaryState = TaxSummaryUnreachableState
      val isCompanyBenefits = false
      val displayCardActions = false

      cardBody shouldBe None

    }

    "return nothing when called with a Pertax user that is not PAYE" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = false
      val iabdType = 0
      val taxSummaryState = TaxSummaryUnreachableState
      val isCompanyBenefits = false
      val displayCardActions = false

      cardBody shouldBe None
    }

    "return no content when called with with a Pertax user that is PAYE but has no tax summary" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = true
      val iabdType = 0
      val taxSummaryState = TaxSummaryNotAvailiableState
      val isCompanyBenefits = false
      val displayCardActions = false

      cardBody shouldBe None
    }

    "return the static version of the markup (no card actions) when called with with a Pertax user that is PAYE but there was an error calling the endpoint" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = true
      val iabdType = 0
      val taxSummaryState = TaxSummaryUnreachableState
      val isCompanyBenefits = false
      val displayCardActions = false

      cardBody shouldBe Some(payAsYouEarn(isCompanyBenefits, displayCardActions))

    }

    "return the static version of the markup (no card actions) when called with with a Pertax user that is PAYE but the tax summary call is disabled" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = true
      //      val hasTaxSummary = false
      val iabdType = 0
      val taxSummaryState = TaxSummaryDisabledState
      val isCompanyBenefits = false
      val displayCardActions = false

      cardBody shouldBe Some(payAsYouEarn(isCompanyBenefits, displayCardActions))

    }

    "return correct markup when called with with a Pertax user that is PAYE with company benefits" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = true
      val iabdType = 31
      val taxSummaryState = TaxSummaryAvailiableState(fakeTaxSummary)
      val isCompanyBenefits = true
      val displayCardActions = true

      cardBody shouldBe Some(payAsYouEarn(isCompanyBenefits, displayCardActions))

    }

    "return correct markup when called with with a Pertax user that is PAYE without company benefits" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = true
      val iabdType = 0
      val taxSummaryState = TaxSummaryAvailiableState(fakeTaxSummary)
      val isCompanyBenefits = false
      val displayCardActions = true

      cardBody shouldBe Some(payAsYouEarn(isCompanyBenefits, displayCardActions))

    }
  }


  "Calling getTaxCalculationCard" should {

    trait LocalSetup extends SpecSetup {

      implicit lazy val pertaxContext = PertaxContext(FakeRequest(), mockLocalPartialRetreiver, injected[ConfigDecorator])

      def taxCalcState: TaxCalculationState

      lazy val cardBody = c.getTaxCalculationCard(Some(taxCalcState))
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

      cardBody shouldBe Some(taxCalculation(taxCalcState))
    }

    "return correct markup when called with TaxCalculationOverpaidPaymentProcessingState" in new LocalSetup {
      val taxCalcState = TaxCalculationOverpaidPaymentProcessingState(100)

      cardBody shouldBe Some(taxCalculation(taxCalcState))
    }

    "return correct markup when called with TaxCalculationOverpaidPaymentPaidState" in new LocalSetup {

      val taxCalcState = TaxCalculationOverpaidPaymentPaidState(100, "01 Jan 2016")

      cardBody shouldBe Some(taxCalculation(taxCalcState))
    }

    "return correct markup when called with TaxCalculationOverpaidPaymentChequeSentState" in new LocalSetup {

      val taxCalcState = TaxCalculationOverpaidPaymentChequeSentState(100, "01 Jan 2016")

      cardBody shouldBe Some(taxCalculation(taxCalcState))
    }

    "return correct markup when called with TaxCalculationUnderpaidPaymentDueState with no SaDeadlineStatus or due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPaymentDueState(100, 2015, 2016, None, None)

      cardBody shouldBe Some(taxCalculation(taxCalcState))
    }

    "return correct markup when called with TaxCalculationUnderpaidPaymentDueState with no SaDeadlineStatus and due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPaymentDueState(100, 2015, 2016, Some("31 January 2016"), None)

      cardBody shouldBe Some(taxCalculation(taxCalcState))
    }

    "return correct markup when called with TaxCalculationUnderpaidPaymentDueState with SaDeadlineStatus of SaDeadlineApproaching and due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPaymentDueState(100, 2015, 2016, Some("31 January 2016"), Some(SaDeadlineApproachingStatus))

      cardBody shouldBe Some(taxCalculation(taxCalcState))
    }

    "return correct markup when called with TaxCalculationUnderpaidPaymentDueState with SaDeadlineStatus of SaDeadlinePassed and due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPaymentDueState(100, 2015, 2016, Some("31 January 2016"), Some(SaDeadlinePassedStatus))

      cardBody shouldBe Some(taxCalculation(taxCalcState))
    }

    "return correct markup when called with TaxCalculationUnderpaidPartPaidState with no SaDeadlineStatus or due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPartPaidState(100, 2015, 2016, None, None)

      cardBody shouldBe Some(taxCalculation(taxCalcState))
    }

    "return correct markup when called with TaxCalculationUnderpaidPartPaidState with no SaDeadlineStatus and due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPartPaidState(100, 2015, 2016, Some("31 January 2016"), None)

      cardBody shouldBe Some(taxCalculation(taxCalcState))
    }

    "return correct markup when called with TaxCalculationUnderpaidPartPaidState with SaDeadlineStatus of SaDeadlineApproaching and due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPartPaidState(100, 2015, 2016, Some("31 January 2016"), Some(SaDeadlineApproachingStatus))

      cardBody shouldBe Some(taxCalculation(taxCalcState))
    }

    "return correct markup when called with TaxCalculationUnderpaidPartPaidState with SaDeadlineStatus of SaDeadlinePassed and due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPartPaidState(100, 2015, 2016, Some("31 January 2016"), Some(SaDeadlinePassedStatus))

      cardBody shouldBe Some(taxCalculation(taxCalcState))
    }

    "return correct markup when called with TaxCalculationUnderpaidPaidAllState with no due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPaidAllState(2015, 2016, None)

      cardBody shouldBe Some(taxCalculation(taxCalcState))
    }

    "return correct markup when called with TaxCalculationUnderpaidPaidAllState with a due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPaidAllState(2015, 2016, Some("01/01/2016"))

      cardBody shouldBe Some(taxCalculation(taxCalcState))
    }

    "return correct markup when called with TaxCalculationDisabledState" in new LocalSetup {
      val taxCalcState = TaxCalculationDisabledState(2015, 2016)

      cardBody shouldBe Some(taxCalculation(taxCalcState))
    }
  }


  "Calling getSelfAssessmentCard" should {

    trait LocalSetup extends SpecSetup {

      lazy val configDecorator = {
        val cd = MockitoSugar.mock[ConfigDecorator]
        when(cd.completeYourTaxReturnUrl(any(), any(), any())).thenReturn("/submit/your/return/url")

        cd
      }

      implicit lazy val pertaxContext = PertaxContext(FakeRequest(), mockLocalPartialRetreiver, configDecorator, pertaxUser)

      def saUserType: SelfAssessmentUserType
      val taxYear = DateTimeTools.previousAndCurrentTaxYear

      lazy val pertaxUser = Some(PertaxUser(Fixtures.buildFakeAuthContext(),UserDetails(UserDetails.GovernmentGatewayAuthProvider),None, true))

      lazy val cardBody = c.getSelfAssessmentCard(saUserType)
    }

    "return correct markup when called with ActivatedOnlineFilerSelfAssessmentUser" in new LocalSetup {

      val saUserType = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))

      cardBody shouldBe Some(selfAssessment(saUserType, taxYear))
    }

    "return correct markup when called with NotYetActivatedOnlineFilerSelfAssessmentUser" in new LocalSetup {

      val saUserType = NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))

      cardBody shouldBe Some(selfAssessment(saUserType, taxYear))
    }


    "return correct markup when called with AmbiguousFilerSelfAssessmentUser" in new LocalSetup {

      val saUserType = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      cardBody shouldBe Some(selfAssessment(saUserType, taxYear))
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

      lazy val cardBody = c.getNationalInsuranceCard()
    }

    "always return the same markup" in new LocalSetup {

      cardBody shouldBe Some(nationalInsurance())
    }
  }


  "Calling getTaxCreditsCard" should {

    trait LocalSetup extends SpecSetup {

      lazy val cardBody = c.getTaxCreditsCard()
    }

    "always return the same markup" in new LocalSetup {

      cardBody shouldBe Some(taxCredits())
    }
  }


  "Calling getChildBenefitCard" should {

    trait LocalSetup extends SpecSetup {

      lazy val cardBody = c.getChildBenefitCard()
    }

    "always return the same markup" in new LocalSetup {

      cardBody shouldBe Some(childBenefit())
    }
  }


  "Calling getMarriageAllowanceCard" should {

    trait LocalSetup extends SpecSetup {

      def hasTaxSummary: Boolean
      def taxCodeEndsWith: String

      lazy val taxSummary = if (hasTaxSummary) Some(Fixtures.buildTaxSummary.copy(taxCodes = Seq("500"+taxCodeEndsWith))) else None
      lazy val cardBody = c.getMarriageAllowanceCard(taxSummary)
    }


    "return correct markup when called with a user who has tax summary and receives Marriage Allowance" in new LocalSetup {
      override val hasTaxSummary: Boolean = true
      override val taxCodeEndsWith = "M"

      cardBody shouldBe Some(marriageAllowance(taxSummary))
    }

    "return nothing when called with a user who has tax summary and transfers Marriage Allowance" in new LocalSetup {
      override val hasTaxSummary: Boolean = true
      override val taxCodeEndsWith = "N"

      cardBody shouldBe Some(marriageAllowance(taxSummary))
    }

    "return correct markup when called with a user who has no tax summary" in new LocalSetup {

      override val hasTaxSummary = false
      override val taxCodeEndsWith = "unused"

      cardBody shouldBe Some(marriageAllowance(taxSummary))
    }

    "return correct markup when called with a user who has tax summary but no marriage allowance" in new LocalSetup {

      override val hasTaxSummary = true
      override val taxCodeEndsWith = "T"

      cardBody shouldBe Some(marriageAllowance(taxSummary))
    }
  }


  "Calling getStatePensionCard" should {

    trait LocalSetup extends SpecSetup {

      lazy val cardBody = c.getStatePensionCard()
    }

    "always return the same markup" in new LocalSetup {

      cardBody shouldBe Some(statePension())
    }
  }
}
