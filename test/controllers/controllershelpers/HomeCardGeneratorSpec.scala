/*
 * Copyright 2022 HM Revenue & Customs
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

package controllers.controllershelpers

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models._
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.i18n.Langs
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import util.DateTimeTools.{current, previousAndCurrentTaxYear}
import util.{EnrolmentsHelper, Fixtures}
import util.UserRequestFixture.buildUserRequest
import views.html.ViewSpec
import views.html.cards.home._

class HomeCardGeneratorSpec extends ViewSpec with MockitoSugar {

  implicit val configDecorator = config

  val payAsYouEarn = injected[PayAsYouEarnView]
  val taxCalculation = injected[TaxCalculationView]
  val selfAssessment = injected[SelfAssessmentView]
  val nationalInsurance = injected[NationalInsuranceView]
  val taxCredits = injected[TaxCreditsView]
  val childBenefit = injected[ChildBenefitView]
  val marriageAllowance = injected[MarriageAllowanceView]
  val statePension = injected[StatePensionView]
  val taxSummaries = injected[TaxSummariesView]
  val seissView = injected[SeissView]
  val itsaView = injected[ItsaView]
  val enrolmentsHelper = injected[EnrolmentsHelper]

  val stubConfigDecorator = new ConfigDecorator(
    injected[Configuration],
    injected[Langs],
    injected[ServicesConfig]
  ) {
    override lazy val saItsaTileEnabled: Boolean = false
  }

  val homeCardGenerator =
    new HomeCardGenerator(
      payAsYouEarn,
      taxCalculation,
      selfAssessment,
      nationalInsurance,
      taxCredits,
      childBenefit,
      marriageAllowance,
      statePension,
      taxSummaries,
      seissView,
      itsaView,
      enrolmentsHelper
    )(stubConfigDecorator)
  val testUtr = SaUtr(new SaUtrGenerator().nextSaUtr.utr)

  "Calling getIncomeCards" must {
    "contains a Seiss card" when {
      "isSeissTileEnabled is true" in {
        implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
          nino = None,
          saUser = NonFilerSelfAssessmentUser,
          confidenceLevel = ConfidenceLevel.L50,
          personDetails = None,
          request = FakeRequest()
        )

        lazy val cardBody =
          homeCardGenerator.getIncomeCards(
            TaxComponentsDisabledState,
            None,
            None,
            NonFilerSelfAssessmentUser,
            true
          )

        cardBody.map(_.toString()).mkString("") must include("seiss-card")
      }
    }

    "not contain a Seiss card" when {
      "saItsaTileEnabled is true" in {
        implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
          nino = None,
          saUser = NonFilerSelfAssessmentUser,
          confidenceLevel = ConfidenceLevel.L50,
          personDetails = None,
          request = FakeRequest()
        )

        val stubConfigDecorator = new ConfigDecorator(
          injected[Configuration],
          injected[Langs],
          injected[ServicesConfig]
        ) {
          override lazy val saItsaTileEnabled: Boolean = true
        }

        def sut: HomeCardGenerator =
          new HomeCardGenerator(
            payAsYouEarn,
            taxCalculation,
            selfAssessment,
            nationalInsurance,
            taxCredits,
            childBenefit,
            marriageAllowance,
            statePension,
            taxSummaries,
            seissView,
            itsaView,
            enrolmentsHelper
          )(stubConfigDecorator)

        lazy val cardBody =
          sut.getIncomeCards(
            TaxComponentsDisabledState,
            None,
            None,
            NonFilerSelfAssessmentUser,
            true
          )

        cardBody.map(_.toString()).mkString("") mustNot include("seiss-card")
      }

      "isSeissTileEnabled is false" in {
        implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
          nino = None,
          saUser = NonFilerSelfAssessmentUser,
          confidenceLevel = ConfidenceLevel.L50,
          personDetails = None,
          request = FakeRequest()
        )

        val stubConfigDecorator = new ConfigDecorator(
          injected[Configuration],
          injected[Langs],
          injected[ServicesConfig]
        ) {
          override lazy val isSeissTileEnabled: Boolean = false
        }

        def sut: HomeCardGenerator =
          new HomeCardGenerator(
            payAsYouEarn,
            taxCalculation,
            selfAssessment,
            nationalInsurance,
            taxCredits,
            childBenefit,
            marriageAllowance,
            statePension,
            taxSummaries,
            seissView,
            itsaView,
            enrolmentsHelper
          )(stubConfigDecorator)

        lazy val cardBody =
          sut.getIncomeCards(
            TaxComponentsDisabledState,
            None,
            None,
            NonFilerSelfAssessmentUser,
            true
          )

        cardBody.map(_.toString()).mkString("") mustNot include("seiss-card")
      }
    }
  }

  "Calling getPayAsYouEarnCard" must {
    "return nothing when called with no Pertax user" in {

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
        nino = None,
        saUser = NonFilerSelfAssessmentUser,
        confidenceLevel = ConfidenceLevel.L50,
        personDetails = None,
        request = FakeRequest()
      )

      lazy val cardBody = homeCardGenerator.getPayAsYouEarnCard(TaxComponentsUnreachableState)

      cardBody mustBe None
    }

    "return no content when called with with a Pertax user that is PAYE but has no tax summary" in {

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
        saUser = NonFilerSelfAssessmentUser,
        credentials = Credentials("", "Verify"),
        confidenceLevel = ConfidenceLevel.L500,
        request = FakeRequest()
      )

      lazy val cardBody = homeCardGenerator.getPayAsYouEarnCard(TaxComponentsNotAvailableState)

      cardBody mustBe None
    }

    "return the static version of the markup (no card actions) when called with with a user that is PAYE but there was an error calling the endpoint" in {

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
        saUser = NonFilerSelfAssessmentUser,
        credentials = Credentials("", "Verify"),
        confidenceLevel = ConfidenceLevel.L500,
        request = FakeRequest()
      )

      lazy val cardBody = homeCardGenerator.getPayAsYouEarnCard(TaxComponentsUnreachableState)

      cardBody mustBe Some(payAsYouEarn(config))
    }

    "return the static version of the markup (no card actions) when called with with a Pertax user that is PAYE but the tax summary call is disabled" in {

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
        saUser = NonFilerSelfAssessmentUser,
        credentials = Credentials("", "Verify"),
        confidenceLevel = ConfidenceLevel.L500,
        request = FakeRequest()
      )

      lazy val cardBody = homeCardGenerator.getPayAsYouEarnCard(TaxComponentsDisabledState)

      cardBody mustBe Some(payAsYouEarn(config))
    }

    "return correct markup when called with with a Pertax user that is PAYE" in {

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
        saUser = NonFilerSelfAssessmentUser,
        credentials = Credentials("", "Verify"),
        confidenceLevel = ConfidenceLevel.L500,
        request = FakeRequest()
      )

      lazy val cardBody =
        homeCardGenerator.getPayAsYouEarnCard(TaxComponentsAvailableState(Fixtures.buildTaxComponents))

      cardBody mustBe Some(payAsYouEarn(config))
    }
  }

  "Calling getSelfAssessmentCard" must {
    val taxYear = previousAndCurrentTaxYear
    val nextDeadlineTaxYear = 2019

    "return None if saItsaTileEnabled is true" in {
      val stubConfigDecorator = new ConfigDecorator(
        injected[Configuration],
        injected[Langs],
        injected[ServicesConfig]
      ) {
        override lazy val saItsaTileEnabled: Boolean = true
      }

      def sut: HomeCardGenerator =
        new HomeCardGenerator(
          payAsYouEarn,
          taxCalculation,
          selfAssessment,
          nationalInsurance,
          taxCredits,
          childBenefit,
          marriageAllowance,
          statePension,
          taxSummaries,
          seissView,
          itsaView,
          enrolmentsHelper
        )(stubConfigDecorator)

      val saUserType = ActivatedOnlineFilerSelfAssessmentUser(testUtr)

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(request = FakeRequest())

      lazy val cardBody = sut.getSelfAssessmentCard(saUserType)

      cardBody mustBe None
    }

    "return correct markup when called with ActivatedOnlineFilerSelfAssessmentUser" in {
      val saUserType = ActivatedOnlineFilerSelfAssessmentUser(testUtr)

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(request = FakeRequest())

      lazy val cardBody = homeCardGenerator.getSelfAssessmentCard(saUserType)

      cardBody mustBe Some(selfAssessment(saUserType, taxYear, nextDeadlineTaxYear.toString))
    }

    "return correct markup when called with NotYetActivatedOnlineFilerSelfAssessmentUser" in {
      val saUserType = NotYetActivatedOnlineFilerSelfAssessmentUser(testUtr)

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
        saUser = saUserType,
        request = FakeRequest()
      )

      lazy val cardBody = homeCardGenerator.getSelfAssessmentCard(saUserType)

      cardBody mustBe Some(selfAssessment(saUserType, taxYear, nextDeadlineTaxYear.toString))
    }

    "return correct markup when called with WrongCredentialsSelfAssessmentUser" in {
      val saUserType = WrongCredentialsSelfAssessmentUser(testUtr)

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
        saUser = saUserType,
        request = FakeRequest()
      )

      lazy val cardBody = homeCardGenerator.getSelfAssessmentCard(saUserType)

      cardBody mustBe Some(selfAssessment(saUserType, taxYear, nextDeadlineTaxYear.toString))
    }

    "return correct markup when called with NotEnrolledSelfAssessmentUser" in {
      val saUserType = NotEnrolledSelfAssessmentUser(testUtr)

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
        saUser = saUserType,
        request = FakeRequest()
      )

      lazy val cardBody = homeCardGenerator.getSelfAssessmentCard(saUserType)

      cardBody mustBe Some(selfAssessment(saUserType, taxYear, nextDeadlineTaxYear.toString))
    }

    "return nothing when called with NonFilerSelfAssessmentUser" in {
      val saUserType = NonFilerSelfAssessmentUser

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
        saUser = saUserType,
        request = FakeRequest()
      )

      lazy val cardBody = homeCardGenerator.getSelfAssessmentCard(saUserType)

      cardBody mustBe None
    }

    "return nothing for a verify user" in {
      val saUserType = ActivatedOnlineFilerSelfAssessmentUser(testUtr)

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
        saUser = saUserType,
        credentials = Credentials("", "Verify"),
        confidenceLevel = ConfidenceLevel.L500,
        request = FakeRequest()
      )

      lazy val cardBody = homeCardGenerator.getSelfAssessmentCard(saUserType)

      cardBody mustBe None
    }
  }

  "Calling getNationalInsuranceCard" must {
    "return NI Card when toggled on" in {

      lazy val cardBody = homeCardGenerator.getNationalInsuranceCard()

      cardBody mustBe Some(nationalInsurance())
    }

    "return nothing when toggled off" in {
      val stubConfigDecorator = new ConfigDecorator(
        injected[Configuration],
        injected[Langs],
        injected[ServicesConfig]
      ) {
        override lazy val isNationalInsuranceCardEnabled: Boolean = false
        override lazy val saItsaTileEnabled: Boolean = false
      }

      def sut: HomeCardGenerator =
        new HomeCardGenerator(
          payAsYouEarn,
          taxCalculation,
          selfAssessment,
          nationalInsurance,
          taxCredits,
          childBenefit,
          marriageAllowance,
          statePension,
          taxSummaries,
          seissView,
          itsaView,
          enrolmentsHelper
        )(stubConfigDecorator)

      sut.getNationalInsuranceCard() mustBe None
    }
  }

  "Calling getTaxCreditsCard" must {
    "always return the same markup when taxCreditsPaymentLinkEnabled is enabled" in {
      lazy val showTaxCreditsPaymentLink = true

      lazy val cardBody = homeCardGenerator.getTaxCreditsCard(showTaxCreditsPaymentLink)

      cardBody mustBe Some(taxCredits(showTaxCreditsPaymentLink))
    }

    "always return the same markup when taxCreditsPaymentLinkEnabled is disabled" in {
      lazy val showTaxCreditsPaymentLink = false

      lazy val cardBody = homeCardGenerator.getTaxCreditsCard(showTaxCreditsPaymentLink)

      cardBody mustBe Some(taxCredits(showTaxCreditsPaymentLink))
    }
  }

  "Calling getChildBenefitCard" must {
    "always return the same markup" in {

      lazy val cardBody = homeCardGenerator.getChildBenefitCard()

      cardBody mustBe Some(childBenefit())
    }
  }

  "Calling getMarriageAllowanceCard" must {
    "return correct markup when called with a user who has tax summary and receives Marriage Allowance" in {
      val hasTaxComponents: Boolean = true
      val taxComponents = Seq("MarriageAllowanceReceived")

      lazy val tc =
        if (hasTaxComponents) Some(Fixtures.buildTaxComponents.copy(taxComponents = taxComponents)) else None

      lazy val cardBody = homeCardGenerator.getMarriageAllowanceCard(tc)

      cardBody mustBe Some(marriageAllowance(tc))
    }

    "return nothing when called with a user who has tax summary and transfers Marriage Allowance" in {
      val hasTaxComponents: Boolean = true
      val taxComponents = Seq("MarriageAllowanceTransferred")

      lazy val tc =
        if (hasTaxComponents) Some(Fixtures.buildTaxComponents.copy(taxComponents = taxComponents)) else None

      lazy val cardBody = homeCardGenerator.getMarriageAllowanceCard(tc)

      cardBody mustBe Some(marriageAllowance(tc))
    }

    "return correct markup when called with a user who has no tax summary" in {
      val hasTaxComponents = false
      val taxComponents = Seq()

      lazy val tc =
        if (hasTaxComponents) Some(Fixtures.buildTaxComponents.copy(taxComponents = taxComponents)) else None

      lazy val cardBody = homeCardGenerator.getMarriageAllowanceCard(tc)

      cardBody mustBe Some(marriageAllowance(tc))
    }

    "return correct markup when called with a user who has tax summary but no marriage allowance" in {
      val hasTaxComponents = true
      val taxComponents = Seq("MedicalInsurance")

      lazy val tc =
        if (hasTaxComponents) Some(Fixtures.buildTaxComponents.copy(taxComponents = taxComponents)) else None

      lazy val cardBody = homeCardGenerator.getMarriageAllowanceCard(tc)

      cardBody mustBe Some(marriageAllowance(tc))
    }
  }

  "Calling getStatePensionCard" must {
    "always return the same markup" in {

      lazy val cardBody = homeCardGenerator.getStatePensionCard()

      cardBody mustBe Some(statePension())
    }
  }

  "Calling getAnnualTaxSummaryCard" when {

    "the tax summaries card is enabled" must {
      "always return the same markup for a SA user" in {
        implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
          buildUserRequest(
            saUser = ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
            request = FakeRequest()
          )

        lazy val cardBody = homeCardGenerator.getAnnualTaxSummaryCard

        cardBody mustBe Some(taxSummaries(configDecorator.annualTaxSaSummariesTileLink))
      }

      val saUtr: SaUtr = SaUtr("test utr")
      val incorrectSaUsers = Seq(
        NonFilerSelfAssessmentUser,
        NotYetActivatedOnlineFilerSelfAssessmentUser(saUtr),
        WrongCredentialsSelfAssessmentUser(saUtr),
        NotEnrolledSelfAssessmentUser(saUtr)
      )

      incorrectSaUsers.foreach { saType =>
        s"always return the same markup for a $saType user" in {

          implicit val payeRequest: UserRequest[AnyContentAsEmpty.type] =
            buildUserRequest(saUser = saType, request = FakeRequest())

          lazy val cardBody = homeCardGenerator.getAnnualTaxSummaryCard
          cardBody mustBe Some(taxSummaries(configDecorator.annualTaxPayeSummariesTileLink))
        }
      }
    }

    "the tax summaries card is disabled" must {
      "return None" in {

        implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
          buildUserRequest(request = FakeRequest())

        val stubConfigDecorator = new ConfigDecorator(
          injected[Configuration],
          injected[Langs],
          injected[ServicesConfig]
        ) {
          override lazy val isAtsTileEnabled = false
          override lazy val saItsaTileEnabled: Boolean = false
        }

        def sut: HomeCardGenerator =
          new HomeCardGenerator(
            payAsYouEarn,
            taxCalculation,
            selfAssessment,
            nationalInsurance,
            taxCredits,
            childBenefit,
            marriageAllowance,
            statePension,
            taxSummaries,
            seissView,
            itsaView,
            enrolmentsHelper
          )(stubConfigDecorator)

        lazy val cardBody = sut.getAnnualTaxSummaryCard

        cardBody mustBe None
      }
    }

    "Calling getItsaCard" must {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
          request = FakeRequest()
        )
      "return Itsa Card when toggled on" in {

        val stubConfigDecorator = new ConfigDecorator(
          injected[Configuration],
          injected[Langs],
          injected[ServicesConfig]
        ) {
          override lazy val saItsaTileEnabled: Boolean = true
        }

        def sut: HomeCardGenerator =
          new HomeCardGenerator(
            payAsYouEarn,
            taxCalculation,
            selfAssessment,
            nationalInsurance,
            taxCredits,
            childBenefit,
            marriageAllowance,
            statePension,
            taxSummaries,
            seissView,
            itsaView,
            enrolmentsHelper
          )(stubConfigDecorator)

        lazy val cardBody = sut.getItsaCard()

        cardBody mustBe Some(itsaView((current.currentYear + 1).toString))
      }

      "return nothing when toggled off" in {

        homeCardGenerator.getItsaCard() mustBe None
      }
    }
  }
}
