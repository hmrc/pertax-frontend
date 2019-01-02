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

    val c = new HomeCardGenerator(configDecorator = injected[ConfigDecorator])
  }


  "Calling getPayAsYouEarnCard" should {

    trait LocalSetup extends SpecSetup {

      def hasPertaxUser: Boolean
      def isPayeUser: Boolean
      def taxComponentsState: TaxComponentsState

      lazy val pertaxUser: Option[PertaxUser] = if(hasPertaxUser)
        Some(PertaxUser(Fixtures.buildFakeAuthContext(withPaye = isPayeUser), UserDetails(UserDetails.GovernmentGatewayAuthProvider), None, true))
      else
        None

      lazy val cardBody = c.getPayAsYouEarnCard(pertaxUser, taxComponentsState)
    }

    "return nothing when called with no Pertax user" in new LocalSetup {
      val hasPertaxUser = false
      val isPayeUser = false
      val taxComponentsState = TaxComponentsUnreachableState

      cardBody shouldBe None

    }

    "return nothing when called with a Pertax user that is not PAYE" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = false
      val taxComponentsState = TaxComponentsUnreachableState

      cardBody shouldBe None
    }

    "return no content when called with with a Pertax user that is PAYE but has no tax summary" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = true
      val taxComponentsState = TaxComponentsNotAvailableState

      cardBody shouldBe None
    }

    "return the static version of the markup (no card actions) when called with with a Pertax user that is PAYE but there was an error calling the endpoint" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = true
      val taxComponentsState = TaxComponentsUnreachableState

      cardBody shouldBe Some(payAsYouEarn())
    }

    "return the static version of the markup (no card actions) when called with with a Pertax user that is PAYE but the tax summary call is disabled" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = true
      val taxComponentsState = TaxComponentsDisabledState

      cardBody shouldBe Some(payAsYouEarn())
    }

    "return correct markup when called with with a Pertax user that is PAYE" in new LocalSetup {
      val hasPertaxUser = true
      val isPayeUser = true
      val taxComponentsState = TaxComponentsAvailableState(Fixtures.buildTaxComponents)

      cardBody shouldBe Some(payAsYouEarn())
    }
  }


  "Calling getTaxCalculationCard" should {

    trait LocalSetup extends SpecSetup {

      implicit lazy val pertaxContext = PertaxContext(FakeRequest(), mockLocalPartialRetreiver, injected[ConfigDecorator])

      def taxCalcState: TaxCalculationState

      lazy val cardBody = c.getTaxCalculationCard(Some(taxCalcState), 2015, 2016)
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

      cardBody shouldBe Some(taxCalculation(taxCalcState, 2015, 2016))
    }

    "return correct markup when called with TaxCalculationOverpaidPaymentProcessingState" in new LocalSetup {
      val taxCalcState = TaxCalculationOverpaidPaymentProcessingState(100)

      cardBody shouldBe Some(taxCalculation(taxCalcState, 2015, 2016))
    }

    "return correct markup when called with TaxCalculationOverpaidPaymentPaidState" in new LocalSetup {

      val taxCalcState = TaxCalculationOverpaidPaymentPaidState(100, "01 Jan 2016")

      cardBody shouldBe Some(taxCalculation(taxCalcState, 2015, 2016))
    }

    "return correct markup when called with TaxCalculationOverpaidPaymentChequeSentState" in new LocalSetup {

      val taxCalcState = TaxCalculationOverpaidPaymentChequeSentState(100, "01 Jan 2016")

      cardBody shouldBe Some(taxCalculation(taxCalcState, 2015, 2016))
    }

    "return correct markup when called with TaxCalculationUnderpaidPaymentDueState with no SaDeadlineStatus or due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPaymentDueState(100, 2015, 2016, None, None)

      cardBody shouldBe Some(taxCalculation(taxCalcState, 2015, 2016))
    }

    "return correct markup when called with TaxCalculationUnderpaidPaymentDueState with no SaDeadlineStatus and due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPaymentDueState(100, 2015, 2016, Some("31 January 2016"), None)

      cardBody shouldBe Some(taxCalculation(taxCalcState, 2015, 2016))
    }

    "return correct markup when called with TaxCalculationUnderpaidPaymentDueState with SaDeadlineStatus of SaDeadlineApproaching and due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPaymentDueState(100, 2015, 2016, Some("31 January 2016"), Some(SaDeadlineApproachingStatus))

      cardBody shouldBe Some(taxCalculation(taxCalcState, 2015, 2016))
    }

    "return correct markup when called with TaxCalculationUnderpaidPaymentDueState with SaDeadlineStatus of SaDeadlinePassed and due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPaymentDueState(100, 2015, 2016, Some("31 January 2016"), Some(SaDeadlinePassedStatus))

      cardBody shouldBe Some(taxCalculation(taxCalcState, 2015, 2016))
    }

    "return correct markup when called with TaxCalculationUnderpaidPartPaidState with no SaDeadlineStatus or due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPartPaidState(100, 2015, 2016, None, None)

      cardBody shouldBe Some(taxCalculation(taxCalcState, 2015, 2016))
    }

    "return correct markup when called with TaxCalculationUnderpaidPartPaidState with no SaDeadlineStatus and due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPartPaidState(100, 2015, 2016, Some("31 January 2016"), None)

      cardBody shouldBe Some(taxCalculation(taxCalcState, 2015, 2016))
    }

    "return correct markup when called with TaxCalculationUnderpaidPartPaidState with SaDeadlineStatus of SaDeadlineApproaching and due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPartPaidState(100, 2015, 2016, Some("31 January 2016"), Some(SaDeadlineApproachingStatus))

      cardBody shouldBe Some(taxCalculation(taxCalcState, 2015, 2016))
    }

    "return correct markup when called with TaxCalculationUnderpaidPartPaidState with SaDeadlineStatus of SaDeadlinePassed and due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPartPaidState(100, 2015, 2016, Some("31 January 2016"), Some(SaDeadlinePassedStatus))

      cardBody shouldBe Some(taxCalculation(taxCalcState, 2015, 2016))
    }

    "return correct markup when called with TaxCalculationUnderpaidPaidAllState with no due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPaidAllState(2015, 2016, None)

      cardBody shouldBe Some(taxCalculation(taxCalcState, 2015, 2016))
    }

    "return correct markup when called with TaxCalculationUnderpaidPaidAllState with a due date" in new LocalSetup {

      val taxCalcState = TaxCalculationUnderpaidPaidAllState(2015, 2016, Some("01/01/2016"))

      cardBody shouldBe Some(taxCalculation(taxCalcState, 2015, 2016))
    }

    "return correct markup when called with TaxCalculationDisabledState" in new LocalSetup {
      val taxCalcState = TaxCalculationDisabledState(2015, 2016)

      cardBody shouldBe Some(taxCalculation(taxCalcState, 2015, 2016))
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
      val taxYear = "1718"
      val nextDeadlineTaxYear = 2019

      lazy val pertaxUser = Some(PertaxUser(Fixtures.buildFakeAuthContext(),UserDetails(UserDetails.GovernmentGatewayAuthProvider),None, true))

      lazy val cardBody = c.getSelfAssessmentCard(saUserType, nextDeadlineTaxYear)
    }

    "return correct markup when called with ActivatedOnlineFilerSelfAssessmentUser" in new LocalSetup {

      val saUserType = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))

      cardBody shouldBe Some(selfAssessment(saUserType, taxYear, nextDeadlineTaxYear.toString))
    }

    "return correct markup when called with NotYetActivatedOnlineFilerSelfAssessmentUser" in new LocalSetup {

      val saUserType = NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))

      cardBody shouldBe Some(selfAssessment(saUserType, taxYear, nextDeadlineTaxYear.toString))
    }


    "return correct markup when called with AmbiguousFilerSelfAssessmentUser" in new LocalSetup {

      val saUserType = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      cardBody shouldBe Some(selfAssessment(saUserType, taxYear, nextDeadlineTaxYear.toString))
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

      def showTaxCreditsPaymentLink: Boolean
      lazy val cardBody = c.getTaxCreditsCard(showTaxCreditsPaymentLink)
    }

    "always return the same markup when taxCreditsPaymentLinkEnabled is enabled" in new LocalSetup {
      override lazy val showTaxCreditsPaymentLink = true
      cardBody shouldBe Some(taxCredits(showTaxCreditsPaymentLink))
    }

    "always return the same markup when taxCreditsPaymentLinkEnabled is disabled" in new LocalSetup {
      override lazy val showTaxCreditsPaymentLink = false
      cardBody shouldBe Some(taxCredits(showTaxCreditsPaymentLink))
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

      def hasTaxComponents: Boolean
      def taxComponents: Seq[String]

      lazy val tc = if (hasTaxComponents) Some(Fixtures.buildTaxComponents.copy(taxComponents = taxComponents)) else None
      lazy val cardBody = c.getMarriageAllowanceCard(tc)
    }


    "return correct markup when called with a user who has tax summary and receives Marriage Allowance" in new LocalSetup {
      override val hasTaxComponents: Boolean = true
      override val taxComponents = Seq("MarriageAllowanceReceived")

      cardBody shouldBe Some(marriageAllowance(tc))
    }

    "return nothing when called with a user who has tax summary and transfers Marriage Allowance" in new LocalSetup {
      override val hasTaxComponents: Boolean = true
      override val taxComponents = Seq("MarriageAllowanceTransferred")

      cardBody shouldBe Some(marriageAllowance(tc))
    }

    "return correct markup when called with a user who has no tax summary" in new LocalSetup {

      override val hasTaxComponents = false
      override val taxComponents = Seq()

      cardBody shouldBe Some(marriageAllowance(tc))
    }

    "return correct markup when called with a user who has tax summary but no marriage allowance" in new LocalSetup {

      override val hasTaxComponents = true
      override val taxComponents = Seq("MedicalInsurance")

      cardBody shouldBe Some(marriageAllowance(tc))
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
