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
import models.admin._
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentMatchers, MockitoSugar}
import play.api.Configuration
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.Html
import services.partials.TaxCalcPartialService
import testUtils.Fixtures
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.auth.core.{ConfidenceLevel, Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.domain.{Generator, SaUtr, SaUtrGenerator}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import util.DateTimeTools.current
import util.EnrolmentsHelper
import views.html.ViewSpec
import views.html.cards.home._

import java.time.LocalDate
import scala.concurrent.Future
import scala.util.Random

class HomeCardGeneratorSpec extends ViewSpec with MockitoSugar {

  implicit val configDecorator: ConfigDecorator = config

  private val generator = new Generator(new Random())

  private val payAsYouEarn                   = inject[PayAsYouEarnView]
  private val taxCredits                     = inject[TaxCreditsView]
  private val childBenefitSingleAccount      = inject[ChildBenefitSingleAccountView]
  private val marriageAllowance              = inject[MarriageAllowanceView]
  private val taxSummaries                   = inject[TaxSummariesView]
  private val latestNewsAndUpdatesView       = inject[LatestNewsAndUpdatesView]
  private val saAndItsaMergeView             = inject[SaAndItsaMergeView]
  private val nispView                       = inject[NISPView]
  private val enrolmentsHelper               = inject[EnrolmentsHelper]
  private val selfAssessmentRegistrationView = inject[SelfAssessmentRegistrationView]
  private val mockConfigDecorator            = mock[ConfigDecorator]

  private val newsAndTilesConfig  = mock[NewsAndTilesConfig]
  private val stubConfigDecorator = new ConfigDecorator(
    inject[Configuration],
    inject[ServicesConfig]
  )

  private val mockTaxCalcPartialService = mock[TaxCalcPartialService]

  private val homeCardGenerator =
    new HomeCardGenerator(
      mockFeatureFlagService,
      payAsYouEarn,
      taxCredits,
      childBenefitSingleAccount,
      marriageAllowance,
      taxSummaries,
      latestNewsAndUpdatesView,
      saAndItsaMergeView,
      enrolmentsHelper,
      newsAndTilesConfig,
      nispView,
      selfAssessmentRegistrationView,
      mockTaxCalcPartialService
    )(stubConfigDecorator, ec)

  def sut: HomeCardGenerator =
    new HomeCardGenerator(
      mockFeatureFlagService,
      payAsYouEarn,
      taxCredits,
      childBenefitSingleAccount,
      marriageAllowance,
      taxSummaries,
      latestNewsAndUpdatesView,
      saAndItsaMergeView,
      enrolmentsHelper,
      newsAndTilesConfig,
      nispView,
      selfAssessmentRegistrationView,
      mockTaxCalcPartialService
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

  "benefit cards" when {
    "should not have benefit cards when the trusted helper exists in the request" in {

      val principalName = "John Doe"
      val url           = "/return-url"
      val helper        = TrustedHelper(
        principalName,
        "Attorney name",
        url,
        generator.nextNino.nino
      )

      lazy val cardBody =
        homeCardGenerator.getBenefitCards(Some(Fixtures.buildTaxComponents), Some(helper))

      cardBody.isEmpty

      verify(mockFeatureFlagService, times(0)).get(any())

    }

    "should have benefit cards when no trusted helper exists in the request" in {

      homeCardGenerator.getBenefitCards(Some(Fixtures.buildTaxComponents), None)

    }
  }

  "Calling getNationalInsuranceCard" must {
    "Always returns NI and SP markup" in {

      lazy val cardBody = homeCardGenerator.getNationalInsuranceCard()

      cardBody mustBe Some(nispView())
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
    "returns the child Benefit single sign on markup" in {
      lazy val cardBody = homeCardGenerator.getChildBenefitCard()

      cardBody mustBe Some(childBenefitSingleAccount())
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

  "Calling getAnnualTaxSummaryCard" when {

    "the tax summaries card is enabled" must {
      "always return the same markup for a SA user" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxSummariesTileToggle)))
          .thenReturn(Future.successful(FeatureFlag(TaxSummariesTileToggle, isEnabled = true)))

        implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
          buildUserRequest(
            saUser = ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
            request = FakeRequest()
          )

        lazy val cardBody = homeCardGenerator.getAnnualTaxSummaryCard.value.futureValue

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
          when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxSummariesTileToggle)))
            .thenReturn(Future.successful(FeatureFlag(TaxSummariesTileToggle, isEnabled = true)))

          implicit val payeRequest: UserRequest[AnyContentAsEmpty.type] =
            buildUserRequest(saUser = saType, request = FakeRequest())

          lazy val cardBody = homeCardGenerator.getAnnualTaxSummaryCard.value.futureValue
          cardBody mustBe Some(taxSummaries(configDecorator.annualTaxPayeSummariesTileLink))
        }
      }
    }

    "the tax summaries card is disabled" must {
      "return None" in {

        when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxSummariesTileToggle)))
          .thenReturn(Future.successful(FeatureFlag(TaxSummariesTileToggle, isEnabled = false)))

        implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
          buildUserRequest(request = FakeRequest())

        lazy val cardBody = sut.getAnnualTaxSummaryCard.value.futureValue

        cardBody mustBe None
      }
    }
  }

  "Calling getItsaCardOrSaWithoutUtrCard" must {

    def createController(pegaEnabled: Boolean = true): HomeCardGenerator = {
      when(mockConfigDecorator.pegaEnabled).thenReturn(pegaEnabled)

      new HomeCardGenerator(
        mockFeatureFlagService,
        payAsYouEarn,
        taxCredits,
        childBenefitSingleAccount,
        marriageAllowance,
        taxSummaries,
        latestNewsAndUpdatesView,
        saAndItsaMergeView,
        enrolmentsHelper,
        newsAndTilesConfig,
        nispView,
        selfAssessmentRegistrationView,
        mockTaxCalcPartialService
      )(mockConfigDecorator, ec)
    }

    implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
      buildUserRequest(
        saUser = ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
        request = FakeRequest()
      )

    "return Itsa Card when the user has ITSA enrolments" in {

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
          enrolments =
            Set(Enrolment("HMRC-MTD-IT", List(EnrolmentIdentifier("MTDITID", "XAIT00000888888")), "Activated")),
          request = FakeRequest()
        )

      lazy val cardBody = createController().getSaAndItsaMergeCardOrSaWithoutUtrCard()

      cardBody mustBe Some(saAndItsaMergeView((current.currentYear + 1).toString, isItsa = true))
    }

    "return Itsa Card when the user is an SA user but without ITSA enrolments" in {

      lazy val cardBody = sut.getSaAndItsaMergeCardOrSaWithoutUtrCard()

      cardBody mustBe Some(saAndItsaMergeView((current.currentYear + 1).toString, isItsa = false))
    }

    "return None when the trustedHelper is not empty" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          trustedHelper = Some(TrustedHelper("", "", "", "")),
          request = FakeRequest()
        )

      lazy val cardBody = createController().getSaAndItsaMergeCardOrSaWithoutUtrCard()

      cardBody mustBe None
    }

    "return selfAssessmentRegistrationView when there is no ITSA enrolment and the user is not an SA user" in {

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = NonFilerSelfAssessmentUser,
          request = FakeRequest()
        )

      lazy val cardBody = createController().getSaAndItsaMergeCardOrSaWithoutUtrCard()

      cardBody mustBe Some(selfAssessmentRegistrationView())
    }

    "return Itsa/sa Card when user has wrong credentials but no ITSA enrolment" in {

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = WrongCredentialsSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
          request = FakeRequest()
        )

      lazy val cardBody = createController().getSaAndItsaMergeCardOrSaWithoutUtrCard()

      cardBody mustBe Some(saAndItsaMergeView((current.currentYear + 1).toString, isItsa = false))
    }

    "return None when pegaEnabled is false" in {

      val controller = createController(pegaEnabled = false)

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = NonFilerSelfAssessmentUser,
          request = FakeRequest()
        )

      lazy val cardBody = controller.getSaAndItsaMergeCardOrSaWithoutUtrCard()

      cardBody mustBe None
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

      sut.getLatestNewsAndUpdatesCard() mustBe None
    }
  }

  "Calling getIncomeCards" must {
    "when taxcalc toggle on return tax calc cards plus surrounding cards, all in correct position" in {

      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle)))
        .thenReturn(Future.successful(FeatureFlag(TaxcalcToggle, isEnabled = true)))

      when(newsAndTilesConfig.getNewsAndContentModelList()).thenReturn(
        List[NewsAndContentModel](
          NewsAndContentModel("newsSectionName", "shortDescription", "content", isDynamic = false, LocalDate.now)
        )
      )

      when(mockTaxCalcPartialService.getTaxCalcPartial(any())).thenReturn(
        Future.successful(
          Seq(
            SummaryCardPartial("name1", Html("<p>test1</p>"), Overpaid),
            SummaryCardPartial("name2", Html("<p>test2</p>"), Underpaid)
          )
        )
      )

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
        nino = None,
        saUser = NonFilerSelfAssessmentUser,
        confidenceLevel = ConfidenceLevel.L50,
        personDetails = None,
        request = FakeRequest()
      )

      lazy val cards =
        homeCardGenerator.getIncomeCards(TaxComponentsAvailableState(Fixtures.buildTaxComponents)).futureValue
      cards.size mustBe 5
      cards.head.toString().contains("news-card") mustBe true
      cards(1).toString().contains("test1") mustBe true
      cards(2).toString().contains("test2") mustBe true
      cards(3).toString().contains("sa-non-utr-card") mustBe true
      cards(4).toString().contains("ni-and-sp-card") mustBe true
    }

    "when taxcalc toggle off return no tax calc cards" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle)))
        .thenReturn(Future.successful(FeatureFlag(TaxcalcToggle, isEnabled = false)))

      when(newsAndTilesConfig.getNewsAndContentModelList()).thenReturn(
        List[NewsAndContentModel](
          NewsAndContentModel("newsSectionName", "shortDescription", "content", isDynamic = false, LocalDate.now)
        )
      )

      when(mockTaxCalcPartialService.getTaxCalcPartial(any())).thenReturn(
        Future.successful(
          Seq(
            SummaryCardPartial("name1", Html("<p>test1</p>"), Overpaid),
            SummaryCardPartial("name2", Html("<p>test2</p>"), Underpaid)
          )
        )
      )

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
        nino = None,
        saUser = NonFilerSelfAssessmentUser,
        confidenceLevel = ConfidenceLevel.L50,
        personDetails = None,
        request = FakeRequest()
      )

      lazy val cards =
        homeCardGenerator.getIncomeCards(TaxComponentsAvailableState(Fixtures.buildTaxComponents)).futureValue
      cards.size mustBe 3
      cards.head.toString().contains("news-card") mustBe true
      cards(2).toString().contains("ni-and-sp-card") mustBe true
    }

    "when taxcalc toggle on but trusted helper present return no tax calc cards" in {

      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle)))
        .thenReturn(Future.successful(FeatureFlag(TaxcalcToggle, isEnabled = true)))

      when(newsAndTilesConfig.getNewsAndContentModelList()).thenReturn(
        List[NewsAndContentModel](
          NewsAndContentModel("newsSectionName", "shortDescription", "content", isDynamic = false, LocalDate.now)
        )
      )

      when(mockTaxCalcPartialService.getTaxCalcPartial(any())).thenReturn(
        Future.successful(
          Seq(
            SummaryCardPartial("name1", Html("<p>test1</p>"), Overpaid),
            SummaryCardPartial("name2", Html("<p>test2</p>"), Underpaid)
          )
        )
      )

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
        nino = None,
        saUser = NonFilerSelfAssessmentUser,
        confidenceLevel = ConfidenceLevel.L50,
        personDetails = None,
        trustedHelper = Some(TrustedHelper("principalName", "attorneyName", "returnUrl", "fakePrincipalNino")),
        request = FakeRequest()
      )

      lazy val cards =
        homeCardGenerator.getIncomeCards(TaxComponentsAvailableState(Fixtures.buildTaxComponents)).futureValue
      cards.size mustBe 2
      cards.head.toString().contains("news-card") mustBe true
      cards(1).toString().contains("ni-and-sp-card") mustBe true
    }
  }
}
