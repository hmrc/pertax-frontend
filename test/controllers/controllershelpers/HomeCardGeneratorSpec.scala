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

import config.{ConfigDecorator, NewsAndTilesConfig}
import controllers.auth.requests.UserRequest
import models.{ActivatedOnlineFilerSelfAssessmentUser, NewsAndContentModel, NonFilerSelfAssessmentUser, NotEnrolledSelfAssessmentUser}
import models.{NotYetActivatedOnlineFilerSelfAssessmentUser, TaxComponentsAvailableState, TaxComponentsDisabledState}
import models.{TaxComponentsNotAvailableState, TaxComponentsUnreachableState, WrongCredentialsSelfAssessmentUser}
import models.admin.{FeatureFlag, NationalInsuranceTileToggle}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.i18n.Langs
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import services.admin.FeatureFlagService
import testUtils.Fixtures
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.auth.core.{ConfidenceLevel, Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import util.DateTimeTools.{current, previousAndCurrentTaxYear}
import util.EnrolmentsHelper
import views.html.ViewSpec
import views.html.cards.home._

import java.time.LocalDate
import scala.concurrent.Future

class HomeCardGeneratorSpec extends ViewSpec with MockitoSugar {

  implicit val configDecorator: ConfigDecorator = config

  private val payAsYouEarn             = injected[PayAsYouEarnView]
  private val taxCalculation           = injected[TaxCalculationView]
  private val selfAssessment           = injected[SelfAssessmentView]
  private val nationalInsurance        = injected[NationalInsuranceView]
  private val taxCredits               = injected[TaxCreditsView]
  private val childBenefit             = injected[ChildBenefitView]
  private val marriageAllowance        = injected[MarriageAllowanceView]
  private val statePension             = injected[StatePensionView]
  private val taxSummaries             = injected[TaxSummariesView]
  private val seissView                = injected[SeissView]
  private val latestNewsAndUpdatesView = injected[LatestNewsAndUpdatesView]
  private val saAndItsaMergeView       = injected[SaAndItsaMergeView]
  private val enrolmentsHelper         = injected[EnrolmentsHelper]
  private val newsAndTilesConfig       = mock[NewsAndTilesConfig]
  private val mockFeatureFlagService   = mock[FeatureFlagService]
  private val stubConfigDecorator      = new ConfigDecorator(
    injected[Configuration],
    injected[Langs],
    injected[ServicesConfig]
  ) {
    override lazy val saItsaTileEnabled: Boolean = false
  }
  private val testUtr                  = SaUtr(new SaUtrGenerator().nextSaUtr.utr)

  private val homeCardGenerator =
    new HomeCardGenerator(
      mockFeatureFlagService,
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
      latestNewsAndUpdatesView,
      saAndItsaMergeView,
      enrolmentsHelper,
      newsAndTilesConfig
    )(stubConfigDecorator, ec)

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

        when(newsAndTilesConfig.getNewsAndContentModelList()).thenReturn(List[NewsAndContentModel]())
        when(mockFeatureFlagService.get(NationalInsuranceTileToggle))
          .thenReturn(Future.successful(FeatureFlag(NationalInsuranceTileToggle, true)))

        def sut: HomeCardGenerator =
          new HomeCardGenerator(
            mockFeatureFlagService,
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
            latestNewsAndUpdatesView,
            saAndItsaMergeView,
            enrolmentsHelper,
            newsAndTilesConfig
          )(stubConfigDecorator, ec)

        lazy val cardBody =
          sut
            .getIncomeCards(
              TaxComponentsDisabledState,
              None,
              None,
              NonFilerSelfAssessmentUser,
              showSeissCard = true
            )
            .futureValue

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

        when(newsAndTilesConfig.getNewsAndContentModelList()).thenReturn(List[NewsAndContentModel]())
        when(mockFeatureFlagService.get(NationalInsuranceTileToggle))
          .thenReturn(Future.successful(FeatureFlag(NationalInsuranceTileToggle, true)))

        def sut: HomeCardGenerator =
          new HomeCardGenerator(
            mockFeatureFlagService,
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
            latestNewsAndUpdatesView,
            saAndItsaMergeView,
            enrolmentsHelper,
            newsAndTilesConfig
          )(stubConfigDecorator, ec)

        lazy val cardBody =
          sut
            .getIncomeCards(
              TaxComponentsDisabledState,
              None,
              None,
              NonFilerSelfAssessmentUser,
              showSeissCard = true
            )
            .futureValue

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

        when(newsAndTilesConfig.getNewsAndContentModelList()).thenReturn(List[NewsAndContentModel]())
        when(mockFeatureFlagService.get(NationalInsuranceTileToggle))
          .thenReturn(Future.successful(FeatureFlag(NationalInsuranceTileToggle, true)))

        def sut: HomeCardGenerator =
          new HomeCardGenerator(
            mockFeatureFlagService,
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
            latestNewsAndUpdatesView,
            saAndItsaMergeView,
            enrolmentsHelper,
            newsAndTilesConfig
          )(stubConfigDecorator, ec)

        lazy val cardBody =
          sut
            .getIncomeCards(
              TaxComponentsDisabledState,
              None,
              None,
              NonFilerSelfAssessmentUser,
              showSeissCard = true
            )
            .futureValue

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
        credentials = Credentials("", "GovernmentGateway"),
        confidenceLevel = ConfidenceLevel.L200,
        request = FakeRequest()
      )

      lazy val cardBody = homeCardGenerator.getPayAsYouEarnCard(TaxComponentsNotAvailableState)

      cardBody mustBe None
    }

    "return the static version of the markup (no card actions) when called with with a user that is PAYE but there was an error calling the endpoint" in {

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
        saUser = NonFilerSelfAssessmentUser,
        credentials = Credentials("", "GovernmentGateway"),
        confidenceLevel = ConfidenceLevel.L200,
        request = FakeRequest()
      )

      lazy val cardBody = homeCardGenerator.getPayAsYouEarnCard(TaxComponentsUnreachableState)

      cardBody mustBe Some(payAsYouEarn(config))
    }

    "return the static version of the markup (no card actions) when called with with a Pertax user that is PAYE but the tax summary call is disabled" in {

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
        saUser = NonFilerSelfAssessmentUser,
        credentials = Credentials("", "GovernmentGateway"),
        confidenceLevel = ConfidenceLevel.L200,
        request = FakeRequest()
      )

      lazy val cardBody = homeCardGenerator.getPayAsYouEarnCard(TaxComponentsDisabledState)

      cardBody mustBe Some(payAsYouEarn(config))
    }

    "return correct markup when called with with a Pertax user that is PAYE" in {

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
        saUser = NonFilerSelfAssessmentUser,
        credentials = Credentials("", "GovernmentGateway"),
        confidenceLevel = ConfidenceLevel.L200,
        request = FakeRequest()
      )

      lazy val cardBody =
        homeCardGenerator.getPayAsYouEarnCard(TaxComponentsAvailableState(Fixtures.buildTaxComponents))

      cardBody mustBe Some(payAsYouEarn(config))
    }
  }

  "Calling getSelfAssessmentCard" must {
    val taxYear             = previousAndCurrentTaxYear
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
          mockFeatureFlagService,
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
          latestNewsAndUpdatesView,
          saAndItsaMergeView,
          enrolmentsHelper,
          newsAndTilesConfig
        )(stubConfigDecorator, ec)

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

      cardBody mustBe Some(selfAssessment(saUserType, taxYear, (current.currentYear + 1).toString))
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
  }

  "Calling getNationalInsuranceCard" must {
    "return NI Card when toggled on" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle)))
        .thenReturn(Future.successful(FeatureFlag(NationalInsuranceTileToggle, true)))

      lazy val cardBody = homeCardGenerator.getNationalInsuranceCard().futureValue

      cardBody mustBe Some(nationalInsurance())
    }

    "return None when toggled off" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle)))
        .thenReturn(Future.successful(FeatureFlag(NationalInsuranceTileToggle, false)))

      lazy val cardBody = homeCardGenerator.getNationalInsuranceCard().futureValue

      cardBody mustBe None
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
      val taxComponents             = Seq("MarriageAllowanceReceived")

      lazy val tc =
        if (hasTaxComponents) Some(Fixtures.buildTaxComponents.copy(taxComponents = taxComponents)) else None

      lazy val cardBody = homeCardGenerator.getMarriageAllowanceCard(tc)

      cardBody mustBe Some(marriageAllowance(tc))
    }

    "return nothing when called with a user who has tax summary and transfers Marriage Allowance" in {
      val hasTaxComponents: Boolean = true
      val taxComponents             = Seq("MarriageAllowanceTransferred")

      lazy val tc =
        if (hasTaxComponents) Some(Fixtures.buildTaxComponents.copy(taxComponents = taxComponents)) else None

      lazy val cardBody = homeCardGenerator.getMarriageAllowanceCard(tc)

      cardBody mustBe Some(marriageAllowance(tc))
    }

    "return correct markup when called with a user who has no tax summary" in {
      val hasTaxComponents = false
      val taxComponents    = Seq()

      lazy val tc =
        if (hasTaxComponents) Some(Fixtures.buildTaxComponents.copy(taxComponents = taxComponents)) else None

      lazy val cardBody = homeCardGenerator.getMarriageAllowanceCard(tc)

      cardBody mustBe Some(marriageAllowance(tc))
    }

    "return correct markup when called with a user who has tax summary but no marriage allowance" in {
      val hasTaxComponents = true
      val taxComponents    = Seq("MedicalInsurance")

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

      val saUtr: SaUtr     = SaUtr("test utr")
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
        }

        def sut: HomeCardGenerator =
          new HomeCardGenerator(
            mockFeatureFlagService,
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
            latestNewsAndUpdatesView,
            saAndItsaMergeView,
            enrolmentsHelper,
            newsAndTilesConfig
          )(stubConfigDecorator, ec)

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
      "return Itsa Card when toggled on with Itsa enrollments" in {

        implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
          buildUserRequest(
            saUser = ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
            enrolments =
              Set(Enrolment("HMRC-MTD-IT", Seq(EnrolmentIdentifier("MTDITID", "XAIT00000888888")), "Activated")),
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
            mockFeatureFlagService,
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
            latestNewsAndUpdatesView,
            saAndItsaMergeView,
            enrolmentsHelper,
            newsAndTilesConfig
          )(stubConfigDecorator, ec)

        lazy val cardBody = sut.getSaAndItsaMergeCard()

        cardBody mustBe Some(saAndItsaMergeView((current.currentYear + 1).toString, isItsa = true))
      }

      "return Itsa Card when toggled on without Itsa enrollments" in {

        val stubConfigDecorator = new ConfigDecorator(
          injected[Configuration],
          injected[Langs],
          injected[ServicesConfig]
        ) {
          override lazy val saItsaTileEnabled: Boolean = true
        }

        def sut: HomeCardGenerator =
          new HomeCardGenerator(
            mockFeatureFlagService,
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
            latestNewsAndUpdatesView,
            saAndItsaMergeView,
            enrolmentsHelper,
            newsAndTilesConfig
          )(stubConfigDecorator, ec)

        lazy val cardBody = sut.getSaAndItsaMergeCard()

        cardBody mustBe Some(saAndItsaMergeView((current.currentYear + 1).toString, isItsa = false))
      }

      "return None when toggled on without Itsa enrollments and enrollment type is NonFilerSelfAssessmentUser" in {

        implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
          buildUserRequest(
            saUser = NonFilerSelfAssessmentUser,
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
            mockFeatureFlagService,
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
            latestNewsAndUpdatesView,
            saAndItsaMergeView,
            enrolmentsHelper,
            newsAndTilesConfig
          )(stubConfigDecorator, ec)

        lazy val cardBody = sut.getSaAndItsaMergeCard()

        cardBody mustBe None
      }

      "return Itsa/sa Card when toggled on without Itsa enrollments and enrollment type is WrongCredentialsSelfAssessmentUser" in {

        implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
          buildUserRequest(
            saUser = WrongCredentialsSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
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
            mockFeatureFlagService,
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
            latestNewsAndUpdatesView,
            saAndItsaMergeView,
            enrolmentsHelper,
            newsAndTilesConfig
          )(stubConfigDecorator, ec)

        lazy val cardBody = sut.getSaAndItsaMergeCard()

        cardBody mustBe Some(saAndItsaMergeView((current.currentYear + 1).toString, isItsa = false))
      }

      "return nothing when toggled off" in {

        homeCardGenerator.getSaAndItsaMergeCard() mustBe None
      }
    }
  }

  "Calling getLatestNewsAndUpdatesCard" must {
    "return News and Updates Card when toggled on and newsAndTilesModel contains elements" in {

      when(newsAndTilesConfig.getNewsAndContentModelList()).thenReturn(
        List[NewsAndContentModel](
          NewsAndContentModel("newsSectionName", "shortDescription", "content", isDynamic = false, LocalDate.now)
        )
      )

      lazy val cardBody = homeCardGenerator.getLatestNewsAndUpdatesCard()

      cardBody mustBe Some(latestNewsAndUpdatesView())
    }

    "return nothing when toggled on and newsAndTilesModel is empty" in {

      when(newsAndTilesConfig.getNewsAndContentModelList()).thenReturn(List[NewsAndContentModel]())

      lazy val cardBody = homeCardGenerator.getLatestNewsAndUpdatesCard()

      cardBody mustBe None
    }

    "return nothing when toggled off" in {
      val stubConfigDecorator = new ConfigDecorator(
        injected[Configuration],
        injected[Langs],
        injected[ServicesConfig]
      ) {
        override lazy val isNewsAndUpdatesTileEnabled: Boolean = false
      }

      def sut: HomeCardGenerator =
        new HomeCardGenerator(
          mockFeatureFlagService,
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
          latestNewsAndUpdatesView,
          saAndItsaMergeView,
          enrolmentsHelper,
          newsAndTilesConfig
        )(stubConfigDecorator, ec)

      sut.getLatestNewsAndUpdatesCard mustBe None
    }
  }
}
