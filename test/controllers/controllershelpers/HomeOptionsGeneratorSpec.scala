/*
 * Copyright 2025 HM Revenue & Customs
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
import controllers.routes
import models.*
import models.admin.*
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import play.api
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.{Application, Configuration}
import play.twirl.api.{Html, HtmlFormat}
import repositories.JourneyCacheRepository
import services.partials.TaxCalcPartialService
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.auth.core.{ConfidenceLevel, Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.domain.{Generator, Nino, SaUtr, SaUtrGenerator}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import util.DateTimeTools.current
import util.EnrolmentsHelper
import views.html.ViewSpec
import views.html.home.options.*

import java.time.LocalDate
import scala.concurrent.Future
import scala.util.Random

class HomeOptionsGeneratorSpec extends ViewSpec with MockitoSugar {

  implicit val configDecorator: ConfigDecorator = config

  private val generator = new Generator(new Random())

  private val payAsYouEarn              = inject[PayAsYouEarnView]
  private val childBenefitSingleAccount = inject[ChildBenefitSingleAccountView]
  private val marriageAllowance         = inject[MarriageAllowanceView]
  private val taxSummaries              = inject[TaxSummariesView]
  private val latestNewsAndUpdatesView  = inject[LatestNewsAndUpdatesView]
  private val saMergeView               = inject[SaMergeView]
  private val mtditAdvertTileView       = inject[MTDITAdvertView]
  private val itsaMergeView             = inject[ItsaMergeView]
  private val nispView                  = inject[NISPView]
  private val trustedHelpersView        = inject[TrustedHelpersView]
  private val taxCalcView               = inject[TaxCalcView]
  private val enrolmentsHelper          = inject[EnrolmentsHelper]
  private val mockConfigDecorator       = mock[ConfigDecorator]

  private val newsAndTilesConfig  = mock[NewsAndTilesConfig]
  private val stubConfigDecorator = new ConfigDecorator(
    inject[Configuration],
    inject[ServicesConfig]
  )

  private val mockTaxCalcPartialService = mock[TaxCalcPartialService]

  private def createHomeOptionsGenerator(usedConfigDecorator: ConfigDecorator): HomeOptionsGenerator =
    new HomeOptionsGenerator(
      mockFeatureFlagService,
      childBenefitSingleAccount,
      marriageAllowance,
      taxSummaries,
      latestNewsAndUpdatesView,
      enrolmentsHelper,
      newsAndTilesConfig,
      nispView,
      itsaMergeView,
      payAsYouEarn,
      saMergeView,
      taxCalcView,
      mtditAdvertTileView,
      trustedHelpersView
    )(usedConfigDecorator, ec)

  private lazy val homeOptionsGenerator = createHomeOptionsGenerator(stubConfigDecorator)

  private val saUtr: SaUtr = SaUtr("test utr")

  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
    buildUserRequest(
      saUser = NonFilerSelfAssessmentUser,
      request = FakeRequest(),
      authNino = generatedNino
    )

  "Calling getPayAsYouEarnCard" must {
    "return PAYE card pointing to TAI when toggle is disabled" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(PayeToPegaRedirectToggle)))
        .thenReturn(Future.successful(FeatureFlag(PayeToPegaRedirectToggle, isEnabled = false)))

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
        saUser = NonFilerSelfAssessmentUser,
        request = FakeRequest()
      )

      val result = homeOptionsGenerator.getPayAsYouEarnCard.futureValue

      result.toString must include("check-income-tax/what-do-you-want-to-do")
    }
  }

  "getCurrentTaxesAndBenefits" when {
    "should not have benefit options when the trusted helper exists in the request" in {

      val principalName = "John Doe"
      val url           = "/return-url"
      val helper        = TrustedHelper(
        principalName,
        "Attorney name",
        url,
        Some(generator.nextNino.nino)
      )

      lazy val cardBody =
        homeOptionsGenerator.getBenefitCards(Some(helper))

      cardBody.isEmpty

      verify(mockFeatureFlagService, times(0)).get(any())
    }
  }

  "Calling getNationalInsuranceCard" must {
    "Always returns NI and SP markup" in {

      lazy val cardBody = homeOptionsGenerator.getNationalInsuranceCard()

      cardBody mustBe nispView()
    }
  }

  "Calling getChildBenefitCard" must {
    "returns the child Benefit single sign on markup" in {
      lazy val cardBody = homeOptionsGenerator.getChildBenefitCard()

      cardBody mustBe childBenefitSingleAccount()
    }
  }

  "Calling getMarriageAllowanceCard" must {
    "return correct markup when called with a user who has tax summary and receives Marriage Allowance" in {

      lazy val cardBody = homeOptionsGenerator.getMarriageAllowanceCard()

      cardBody mustBe marriageAllowance()
    }
  }

  "Calling getAnnualTaxSummaryCard" when {

    "the tax summaries card is enabled" must {
      val saUtr: SaUtr = SaUtr(new SaUtrGenerator().nextSaUtr.utr)
      val saUserTypes  = List(
        ActivatedOnlineFilerSelfAssessmentUser(saUtr),
        NonFilerSelfAssessmentUser,
        NotYetActivatedOnlineFilerSelfAssessmentUser(saUtr),
        WrongCredentialsSelfAssessmentUser(saUtr),
        NotEnrolledSelfAssessmentUser(saUtr)
      )

      saUserTypes.foreach { saType =>
        s"always return the same markup for a $saType user" in {
          when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxSummariesTileToggle)))
            .thenReturn(Future.successful(FeatureFlag(TaxSummariesTileToggle, isEnabled = true)))

          lazy val cardBody = homeOptionsGenerator.getAnnualTaxSummaryCard.futureValue

          cardBody mustBe List(taxSummaries(configDecorator.annualTaxSaSummariesTileLinkShow))
        }
      }
    }

    "the tax summaries card is disabled" must {
      "return None" in {

        when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxSummariesTileToggle)))
          .thenReturn(Future.successful(FeatureFlag(TaxSummariesTileToggle, isEnabled = false)))

        lazy val cardBody = homeOptionsGenerator.getAnnualTaxSummaryCard.futureValue

        cardBody mustBe Nil
      }
    }
  }

  "Calling getSelfAssessmentCard" must {

    def createController(pegaEnabled: Boolean = true): HomeOptionsGenerator = {
      when(mockConfigDecorator.pegaSaRegistrationEnabled).thenReturn(pegaEnabled)

      createHomeOptionsGenerator(mockConfigDecorator)
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

      lazy val cardBody = createController().getSelfAssessmentCards()

      cardBody mustBe Seq(itsaMergeView((current.currentYear + 1).toString)(implicitly))
    }

    "return Itsa Card with correct name when the user has ITSA enrolments when name change toggle set to false" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
          enrolments =
            Set(Enrolment("HMRC-MTD-IT", List(EnrolmentIdentifier("MTDITID", "XAIT00000888888")), "Activated")),
          request = FakeRequest()
        )

      lazy val cardBody: Seq[HtmlFormat.Appendable] =
        createController().getSelfAssessmentCards

      cardBody mustBe Seq(itsaMergeView((current.currentYear + 1).toString))
      cardBody.mkString("") must include("Making Tax Digital for Income Tax")
    }

    "return PTA Card with link to display self assessment when active user is an SA user but without ITSA enrolments" in {

      lazy val cardBody = homeOptionsGenerator.getSelfAssessmentCards

      cardBody mustBe Seq(
        saMergeView(
          (current.currentYear + 1).toString,
          controllers.interstitials.routes.InterstitialController.displaySelfAssessment.url,
          "label.newViewAndManageSA"
        )
      )
    }

    "return PTA Card with link to self assessment when not yet activated user is an SA user but without ITSA enrolments" in {

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
          request = FakeRequest()
        )

      lazy val cardBody = homeOptionsGenerator.getSelfAssessmentCards

      cardBody mustBe Seq(
        saMergeView(
          (current.currentYear + 1).toString,
          routes.SelfAssessmentController.handleSelfAssessment.url,
          "label.activate_your_self_assessment"
        )
      )
    }
    "return PTA Card with link to self assessment when not enrolled user is an SA user but without ITSA enrolments" in {

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = NotEnrolledSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
          request = FakeRequest()
        )

      lazy val cardBody = homeOptionsGenerator.getSelfAssessmentCards

      cardBody mustBe Seq(
        saMergeView(
          (current.currentYear + 1).toString,
          routes.SelfAssessmentController.redirectToEnrolForSa.url,
          "label.request_access_to_your_sa"
        )
      )
    }

    "return PTA Card with link to self assessment when not enrolled user is an SA user but without ITSA enrolments minus mdtit tile when not requested" in {

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = NotEnrolledSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
          request = FakeRequest()
        )

      lazy val cardBody = homeOptionsGenerator.getSelfAssessmentCards

      cardBody mustBe Seq(
        saMergeView(
          (current.currentYear + 1).toString,
          routes.SelfAssessmentController.redirectToEnrolForSa.url,
          "label.request_access_to_your_sa"
        )
      )
    }

    "return no card when non-filing user (cannot confirm user's identity) is an SA user but without ITSA enrolments" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = NonFilerSelfAssessmentUser,
          request = FakeRequest()
        )

      lazy val cardBody = homeOptionsGenerator.getSelfAssessmentCards

      cardBody mustBe Nil
    }

    "return None when the trustedHelper is not empty" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          trustedHelper = Some(TrustedHelper("", "", "", Some(generatedTrustedHelperNino.nino))),
          request = FakeRequest()
        )

      lazy val cardBody = createController().getSelfAssessmentCards

      cardBody mustBe Nil
    }

    "return sa Card when user with wrong creds has wrong credentials but no ITSA enrolment" in {

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = WrongCredentialsSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
          request = FakeRequest()
        )

      lazy val cardBody = createController().getSelfAssessmentCards

      cardBody mustBe Seq(
        saMergeView(
          (current.currentYear + 1).toString,
          routes.SelfAssessmentController.handleSelfAssessment.url,
          "label.find_out_how_to_access_your_self_assessment"
        )
      )
    }

    "return None when pegaEnabled is false" in {

      val controller = createController(pegaEnabled = false)

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = NonFilerSelfAssessmentUser,
          request = FakeRequest()
        )

      lazy val cardBody = controller.getSelfAssessmentCards

      cardBody mustBe Nil
    }
  }

  "Calling getLatestNewsAndUpdatesCard" must {
    "return News and Updates Card when toggled on and newsAndTilesModel contains elements" in {

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          request = FakeRequest()
        )

      when(newsAndTilesConfig.getNewsAndContentModelList()(any(), any())).thenReturn(
        List[NewsAndContentModel](
          NewsAndContentModel("newsSectionName", "shortDescription", "content", isDynamic = false, LocalDate.now, true)
        )
      )

      lazy val cardBody = homeOptionsGenerator.getLatestNewsAndUpdatesCard()

      cardBody mustBe Some(latestNewsAndUpdatesView())
    }

    "return nothing when toggled on and newsAndTilesModel is empty" in {

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          request = FakeRequest()
        )

      when(newsAndTilesConfig.getNewsAndContentModelList()(any(), any())).thenReturn(List[NewsAndContentModel]())

      lazy val cardBody = homeOptionsGenerator.getLatestNewsAndUpdatesCard()

      cardBody mustBe None
    }

    "return nothing when toggled off" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          request = FakeRequest()
        )

      homeOptionsGenerator.getLatestNewsAndUpdatesCard() mustBe None
    }
  }

  "getCurrentTaxesAndBenefits" must {
    def setupForIncomeCards = {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(ShowTaxCalcTileToggle)))
        .thenReturn(Future.successful(FeatureFlag(ShowTaxCalcTileToggle, isEnabled = true)))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(PayeToPegaRedirectToggle)))
        .thenReturn(Future.successful(FeatureFlag(PayeToPegaRedirectToggle, isEnabled = false)))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(MDTITAdvertToggle)))
        .thenReturn(Future.successful(FeatureFlag(MDTITAdvertToggle, isEnabled = true)))
      when(mockConfigDecorator.taiHost).thenReturn("https://tai.host.test")
    }

    def checkNonSAIncomeCardsPresent(cards: Seq[Html]) = {
      cards.map(_.toString).exists(_.contains("paye-card")) mustBe true
      cards.map(_.toString).exists(_.contains("taxcalc-card")) mustBe true
      cards.map(_.toString).exists(_.contains("ni-and-sp-card")) mustBe true
    }

    Seq(
      ActivatedOnlineFilerSelfAssessmentUser(saUtr = saUtr),
      NotYetActivatedOnlineFilerSelfAssessmentUser(saUtr = saUtr),
      WrongCredentialsSelfAssessmentUser(saUtr = saUtr),
      NotEnrolledSelfAssessmentUser(saUtr = saUtr)
    ).foreach { saType =>
      s"return all expected cards when all toggles are enabled: SA and MDTIT tiles should be displayed for sa user type $saType" in {
        setupForIncomeCards
        implicit val request: UserRequest[AnyContentAsEmpty.type] =
          buildUserRequest(saUser = saType, request = FakeRequest())

        val cards = homeOptionsGenerator.getCurrentTaxesAndBenefits.futureValue
        cards.size mustBe 4
        checkNonSAIncomeCardsPresent(cards)
        cards.map(_.toString).exists(_.contains("sa-card")) mustBe true
      }
    }

    s"return all expected cards when all toggles are enabled: SA & MDTIT tiles should not be displayed for sa user type $NonFilerSelfAssessmentUser" in {
      setupForIncomeCards
      implicit val request: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(saUser = NonFilerSelfAssessmentUser, request = FakeRequest())

      val cards = homeOptionsGenerator.getCurrentTaxesAndBenefits.futureValue
      cards.size mustBe 3
      checkNonSAIncomeCardsPresent(cards)
      cards.map(_.toString).exists(_.contains("sa-card")) mustBe false
    }
  }

  "getOtherTaxesAndBenefits" must {
    def setupForOtherTaxesOptions = {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(MDTITAdvertToggle)))
        .thenReturn(Future.successful(FeatureFlag(MDTITAdvertToggle, isEnabled = true)))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxSummariesTileToggle)))
        .thenReturn(Future.successful(FeatureFlag(TaxSummariesTileToggle, isEnabled = true)))
    }

    def checkOptionsPresent(cards: Seq[Html]) = {
      cards.map(_.toString).exists(_.contains("ats-card")) mustBe true
      cards.map(_.toString).exists(_.contains("marriage-allowance-card")) mustBe true
      cards.map(_.toString).exists(_.contains("trusted-helpers-card")) mustBe true
      cards.map(_.toString).exists(_.contains("child-benefit-card")) mustBe true
    }

    Seq(
      ActivatedOnlineFilerSelfAssessmentUser(saUtr = saUtr),
      NotYetActivatedOnlineFilerSelfAssessmentUser(saUtr = saUtr),
      WrongCredentialsSelfAssessmentUser(saUtr = saUtr),
      NotEnrolledSelfAssessmentUser(saUtr = saUtr)
    ).foreach { saType =>
      s"return all expected cards when all toggles are enabled: MDTIT tiles should be displayed for sa user type $saType" in {
        setupForOtherTaxesOptions
        implicit val request: UserRequest[AnyContentAsEmpty.type] =
          buildUserRequest(saUser = saType, request = FakeRequest())

        val cards = homeOptionsGenerator.getOtherTaxesAndBenefits.futureValue
        cards.size mustBe 5
        checkOptionsPresent(cards)
        cards.map(_.toString).exists(_.contains("mtdit-card")) mustBe true
      }
    }

    s"return all expected cards when all toggles are enabled: MDTIT tiles should not be displayed for sa user type $NonFilerSelfAssessmentUser" in {
      setupForOtherTaxesOptions
      implicit val request: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(saUser = NonFilerSelfAssessmentUser, request = FakeRequest())

      val cards = homeOptionsGenerator.getOtherTaxesAndBenefits.futureValue
      cards.size mustBe 4
      checkOptionsPresent(cards)
      cards.map(_.toString).exists(_.contains("mtdit-card")) mustBe false
    }
  }

  "Calling getTrustedHelpersCard" must {
    "return trusted helpers markup" in {
      val result = homeOptionsGenerator.getTrustedHelpersCard()
      result mustBe Seq(trustedHelpersView())
    }
  }

  "return PAYE card pointing to PEGA when toggle is enabled and NINO matches the redirect list" in {
    val matchingNino = "AA000055A"

    val injectedMockConfigDecorator = mock[ConfigDecorator]
    when(injectedMockConfigDecorator.payeToPegaRedirectList).thenReturn(Seq(5))
    when(injectedMockConfigDecorator.payeToPegaRedirectUrl).thenReturn("https://pega.test.redirect")
    when(injectedMockConfigDecorator.taiHost).thenReturn("https://tai.test")

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(PayeToPegaRedirectToggle)))
      .thenReturn(Future.successful(FeatureFlag(PayeToPegaRedirectToggle, isEnabled = true)))

    val app: Application = localGuiceApplicationBuilder()
      .overrides(
        api.inject.bind[ConfigDecorator].toInstance(injectedMockConfigDecorator),
        api.inject.bind[NewsAndTilesConfig].toInstance(newsAndTilesConfig),
        api.inject.bind[TaxCalcPartialService].toInstance(mockTaxCalcPartialService),
        api.inject.bind[JourneyCacheRepository].toInstance(mock[JourneyCacheRepository])
      )
      .build()

    val controller = new HomeOptionsGenerator(
      mockFeatureFlagService,
      childBenefitSingleAccount,
      marriageAllowance,
      taxSummaries,
      latestNewsAndUpdatesView,
      enrolmentsHelper,
      newsAndTilesConfig,
      nispView,
      itsaMergeView,
      app.injector.instanceOf[PayAsYouEarnView],
      saMergeView,
      taxCalcView,
      mtditAdvertTileView,
      trustedHelpersView
    )(injectedMockConfigDecorator, ec)

    implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
      buildUserRequest(
        saUser = NonFilerSelfAssessmentUser,
        request = FakeRequest(),
        authNino = Nino(matchingNino)
      )

    val result = controller.getPayAsYouEarnCard.futureValue

    result.toString must include("https://pega.test.redirect")

    app.stop()
  }
}
