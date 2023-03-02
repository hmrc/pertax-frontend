/*
 * Copyright 2023 HM Revenue & Customs
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
import models._
import models.admin.{ChildBenefitSingleAccountToggle, FeatureFlag, NationalInsuranceTileToggle}
import org.mockito.ArgumentMatchers
import play.api.Configuration
import play.api.i18n.Langs
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import services.admin.FeatureFlagService
import testUtils.Fixtures
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.{ConfidenceLevel, Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import util.DateTimeTools.current
import util.EnrolmentsHelper
import views.html.ViewSpec
import views.html.cards.home._

import java.time.LocalDate
import scala.concurrent.Future

class HomeCardGeneratorSpec extends ViewSpec {

  implicit val configDecorator: ConfigDecorator = config

  private val payAsYouEarn              = injected[PayAsYouEarnView]
  private val taxCalculation            = injected[TaxCalculationView]
  private val nationalInsurance         = injected[NationalInsuranceView]
  private val taxCredits                = injected[TaxCreditsView]
  private val childBenefit              = injected[ChildBenefitView]
  private val childBenefitSingleAccount = injected[ChildBenefitSingleAccountView]
  private val marriageAllowance         = injected[MarriageAllowanceView]
  private val statePension              = injected[StatePensionView]
  private val taxSummaries              = injected[TaxSummariesView]
  private val latestNewsAndUpdatesView  = injected[LatestNewsAndUpdatesView]
  private val saAndItsaMergeView        = injected[SaAndItsaMergeView]
  private val enrolmentsHelper          = injected[EnrolmentsHelper]
  private val newsAndTilesConfig        = mock[NewsAndTilesConfig]
  private val mockFeatureFlagService    = mock[FeatureFlagService]
  private val stubConfigDecorator       = new ConfigDecorator(
    injected[Configuration],
    injected[Langs],
    injected[ServicesConfig]
  )

  private val homeCardGenerator =
    new HomeCardGenerator(
      mockFeatureFlagService,
      payAsYouEarn,
      taxCalculation,
      nationalInsurance,
      taxCredits,
      childBenefit,
      childBenefitSingleAccount,
      marriageAllowance,
      statePension,
      taxSummaries,
      latestNewsAndUpdatesView,
      saAndItsaMergeView,
      enrolmentsHelper,
      newsAndTilesConfig
    )(stubConfigDecorator, ec)

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

  "Calling getNationalInsuranceCard" must {
    "return NI Card when toggled on" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle)))
        .thenReturn(Future.successful(FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)))

      lazy val cardBody = homeCardGenerator.getNationalInsuranceCard.futureValue

      cardBody mustBe Some(nationalInsurance())
    }

    "return None when toggled off" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle)))
        .thenReturn(Future.successful(FeatureFlag(NationalInsuranceTileToggle, isEnabled = false)))

      lazy val cardBody = homeCardGenerator.getNationalInsuranceCard.futureValue

      cardBody mustBe None
    }
  }

  "Calling getTaxCreditsCard" must {
    "always return the same markup when taxCreditsPaymentLinkEnabled is enabled" in {

      lazy val cardBody = homeCardGenerator.getTaxCreditsCard()

      cardBody mustBe Some(taxCredits())
    }

    "always return the same markup when taxCreditsPaymentLinkEnabled is disabled" in {

      lazy val cardBody = homeCardGenerator.getTaxCreditsCard()

      cardBody mustBe Some(taxCredits())
    }
  }

  "Calling getChildBenefitCard" must {
    "returns the child Benefit markup if ChildBenefitSingleAccountToggle is false" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(ChildBenefitSingleAccountToggle)))
        .thenReturn(Future.successful(FeatureFlag(ChildBenefitSingleAccountToggle, isEnabled = false)))

      lazy val cardBody = homeCardGenerator.getChildBenefitCard()

      cardBody.futureValue mustBe Some(childBenefit())
    }

    "returns the child Benefit single sign on markup if ChildBenefitSingleAccountToggle is true" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(ChildBenefitSingleAccountToggle)))
        .thenReturn(Future.successful(FeatureFlag(ChildBenefitSingleAccountToggle, isEnabled = true)))

      lazy val cardBody = homeCardGenerator.getChildBenefitCard()

      cardBody.futureValue mustBe Some(childBenefitSingleAccount())
    }
  }

  "Calling getMarriageAllowanceCard" must {
    "return correct markup when called with a user who has tax summary and receives Marriage Allowance" in {
      val hasTaxComponents: Boolean = true
      val taxComponents             = List("MarriageAllowanceReceived")

      lazy val tc =
        if (hasTaxComponents) Some(Fixtures.buildTaxComponents.copy(taxComponents = taxComponents)) else None

      lazy val cardBody = homeCardGenerator.getMarriageAllowanceCard(tc)

      cardBody mustBe Some(marriageAllowance(tc))
    }

    "return nothing when called with a user who has tax summary and transfers Marriage Allowance" in {
      val hasTaxComponents: Boolean = true
      val taxComponents             = List("MarriageAllowanceTransferred")

      lazy val tc =
        if (hasTaxComponents) Some(Fixtures.buildTaxComponents.copy(taxComponents = taxComponents)) else None

      lazy val cardBody = homeCardGenerator.getMarriageAllowanceCard(tc)

      cardBody mustBe Some(marriageAllowance(tc))
    }

    "return correct markup when called with a user who has no tax summary" in {
      val hasTaxComponents = false
      val taxComponents    = List.empty

      lazy val tc =
        if (hasTaxComponents) Some(Fixtures.buildTaxComponents.copy(taxComponents = taxComponents)) else None

      lazy val cardBody = homeCardGenerator.getMarriageAllowanceCard(tc)

      cardBody mustBe Some(marriageAllowance(tc))
    }

    "return correct markup when called with a user who has tax summary but no marriage allowance" in {
      val hasTaxComponents = true
      val taxComponents    = List("MedicalInsurance")

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
      val incorrectSaUsers = List(
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
            nationalInsurance,
            taxCredits,
            childBenefit,
            childBenefitSingleAccount,
            marriageAllowance,
            statePension,
            taxSummaries,
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
      "return Itsa Card when with Itsa enrolments" in {

        implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
          buildUserRequest(
            saUser = ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
            enrolments =
              Set(Enrolment("HMRC-MTD-IT", List(EnrolmentIdentifier("MTDITID", "XAIT00000888888")), "Activated")),
            request = FakeRequest()
          )

        val stubConfigDecorator = new ConfigDecorator(
          injected[Configuration],
          injected[Langs],
          injected[ServicesConfig]
        )

        def sut: HomeCardGenerator =
          new HomeCardGenerator(
            mockFeatureFlagService,
            payAsYouEarn,
            taxCalculation,
            nationalInsurance,
            taxCredits,
            childBenefit,
            childBenefitSingleAccount,
            marriageAllowance,
            statePension,
            taxSummaries,
            latestNewsAndUpdatesView,
            saAndItsaMergeView,
            enrolmentsHelper,
            newsAndTilesConfig
          )(stubConfigDecorator, ec)

        lazy val cardBody = sut.getSaAndItsaMergeCard()

        cardBody mustBe Some(saAndItsaMergeView((current.currentYear + 1).toString, isItsa = true))
      }

      "return Itsa Card when without Itsa enrolments" in {

        val stubConfigDecorator = new ConfigDecorator(
          injected[Configuration],
          injected[Langs],
          injected[ServicesConfig]
        )

        def sut: HomeCardGenerator =
          new HomeCardGenerator(
            mockFeatureFlagService,
            payAsYouEarn,
            taxCalculation,
            nationalInsurance,
            taxCredits,
            childBenefit,
            childBenefitSingleAccount,
            marriageAllowance,
            statePension,
            taxSummaries,
            latestNewsAndUpdatesView,
            saAndItsaMergeView,
            enrolmentsHelper,
            newsAndTilesConfig
          )(stubConfigDecorator, ec)

        lazy val cardBody = sut.getSaAndItsaMergeCard()

        cardBody mustBe Some(saAndItsaMergeView((current.currentYear + 1).toString, isItsa = false))
      }

      "return None when without Itsa enrolments and sa enrolment type is NonFilerSelfAssessmentUser" in {

        implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
          buildUserRequest(
            saUser = NonFilerSelfAssessmentUser,
            request = FakeRequest()
          )

        val stubConfigDecorator = new ConfigDecorator(
          injected[Configuration],
          injected[Langs],
          injected[ServicesConfig]
        )

        def sut: HomeCardGenerator =
          new HomeCardGenerator(
            mockFeatureFlagService,
            payAsYouEarn,
            taxCalculation,
            nationalInsurance,
            taxCredits,
            childBenefit,
            childBenefitSingleAccount,
            marriageAllowance,
            statePension,
            taxSummaries,
            latestNewsAndUpdatesView,
            saAndItsaMergeView,
            enrolmentsHelper,
            newsAndTilesConfig
          )(stubConfigDecorator, ec)

        lazy val cardBody = sut.getSaAndItsaMergeCard()

        cardBody mustBe None
      }

      "return Itsa/sa Card when without Itsa enrolments and sa enrolment type is WrongCredentialsSelfAssessmentUser" in {

        implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
          buildUserRequest(
            saUser = WrongCredentialsSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
            request = FakeRequest()
          )

        val stubConfigDecorator = new ConfigDecorator(
          injected[Configuration],
          injected[Langs],
          injected[ServicesConfig]
        )

        def sut: HomeCardGenerator =
          new HomeCardGenerator(
            mockFeatureFlagService,
            payAsYouEarn,
            taxCalculation,
            nationalInsurance,
            taxCredits,
            childBenefit,
            childBenefitSingleAccount,
            marriageAllowance,
            statePension,
            taxSummaries,
            latestNewsAndUpdatesView,
            saAndItsaMergeView,
            enrolmentsHelper,
            newsAndTilesConfig
          )(stubConfigDecorator, ec)

        lazy val cardBody = sut.getSaAndItsaMergeCard()

        cardBody mustBe Some(saAndItsaMergeView((current.currentYear + 1).toString, isItsa = false))
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

      lazy val cardBody = homeCardGenerator.getLatestNewsAndUpdatesCard

      cardBody mustBe Some(latestNewsAndUpdatesView())
    }

    "return nothing when toggled on and newsAndTilesModel is empty" in {

      when(newsAndTilesConfig.getNewsAndContentModelList()).thenReturn(List[NewsAndContentModel]())

      lazy val cardBody = homeCardGenerator.getLatestNewsAndUpdatesCard

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
          nationalInsurance,
          taxCredits,
          childBenefit,
          childBenefitSingleAccount,
          marriageAllowance,
          statePension,
          taxSummaries,
          latestNewsAndUpdatesView,
          saAndItsaMergeView,
          enrolmentsHelper,
          newsAndTilesConfig
        )(stubConfigDecorator, ec)

      sut.getLatestNewsAndUpdatesCard mustBe None
    }
  }
}
