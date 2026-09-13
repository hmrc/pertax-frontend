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

package services

import cats.data.EitherT
import connectors.TasksAndActivitiesConnector
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models.admin.{ShowTaxCalcTileToggle, TasksAndActivitiesServiceToggle}
import models.*
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.i18n.{Lang, Messages, MessagesImpl}
import play.api.mvc.AnyContent
import play.api.test.FakeRequest
import play.twirl.api.Html
import services.partials.TaxCalcPartialService
import testUtils.BaseSpec
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import viewmodels.{Task, TaskStatus}

import scala.concurrent.Future

class TasksServiceSpec extends BaseSpec {

  private val mockConfigDecorator: ConfigDecorator             = mock[ConfigDecorator]
  private val mockTaxCalcPartialService: TaxCalcPartialService = mock[TaxCalcPartialService]
  private val mockFeatureFlagService: FeatureFlagService       = mock[FeatureFlagService]
  private val mockTasksAndActivitiesConnector                  = mock[TasksAndActivitiesConnector]

  private lazy val service: TasksService =
    new TasksService(
      mockConfigDecorator,
      mockTaxCalcPartialService,
      mockFeatureFlagService,
      mockTasksAndActivitiesConnector
    )
  implicit lazy val messages: Messages   = MessagesImpl(Lang("en"), messagesApi)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConfigDecorator)
    reset(mockFeatureFlagService)
    reset(mockTaxCalcPartialService)
    reset(mockTasksAndActivitiesConnector)
  }

  val overpaid: String =
    """
      |<div class="card-body active" data-journey-click="button - click:summary card - 2024 :overpaid" style="height: 632.047px; border-bottom: 0px;">
      |    <h3 class="govuk-heading-s card-heading">
      |      <a class="govuk-link card-link" href="/tax-you-paid/2024-2025/paid-too-much">
      |        6&nbsp;April&nbsp;2024&nbsp;to&nbsp;5&nbsp;April&nbsp;2025
      |      </a>
      |    </h3>
      |<p class="govuk-body owe_message">HMRC owe you £84.23 .</p>
      |<p class="govuk-body">Get your refund paid online.</p>
      |  </div>
      |""".stripMargin

  val underpaid: String =
    """
      |<div class="card-body active" data-journey-click="button - click:summary card - 2023 :underpaid" style="height: 632.047px; border-bottom: 0px;">
      |    <h3 class="govuk-heading-s card-heading govuk-!-margin-bottom-2">
      |        <a class="govuk-link card-link" href="/tax-you-paid/2023-2024/paid-too-little">
      |            6&nbsp;April&nbsp;2023&nbsp;to&nbsp;5&nbsp;April&nbsp;2024
      |        </a>
      |    </h3>
      |<p class="govuk-body owe_message">You owe £500 .</p>
      |<p class="govuk-body">You should have paid by 19 February 2018 but you can still make a payment now.</p>
      |  </div>
      |""".stripMargin

  private def userRequest: UserRequest[AnyContent] = UserRequest(
    generatedNino,
    NonFilerSelfAssessmentUser,
    Credentials("credId", "GovernmentGateway"),
    ConfidenceLevel.L200,
    None,
    Set.empty,
    None,
    None,
    FakeRequest(),
    UserAnswers.empty
  )

  "getListOfTasks" when {
    "the Tasks and Activities Service toggle is disabled" must {
      "use the existing task logic" in {
        implicit val request: UserRequest[AnyContent] = userRequest
        when(mockConfigDecorator.taxCalcHomePageUrl).thenReturn("http://link/to/taxcalc")
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(TasksAndActivitiesServiceToggle)))
          .thenReturn(Future.successful(FeatureFlag(TasksAndActivitiesServiceToggle, false)))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(ShowTaxCalcTileToggle)))
          .thenReturn(Future.successful(FeatureFlag(ShowTaxCalcTileToggle, true)))
        when(mockTaxCalcPartialService.getTaxCalcPartial(any())).thenReturn(
          Future.successful(Seq(SummaryCardPartial("tc1", Html(underpaid), Underpaid, 2026)))
        )

        val result = service.getListOfTasks.futureValue

        result mustBe Seq(
          Task("You owe £500 for tax year 2026 to 2027", TaskStatus.Incomplete, "http://link/to/taxcalc", None)
        )
        verify(mockTasksAndActivitiesConnector, times(0)).getTasks(any())(any(), any())
      }
    }

    "the Tasks and Activities Service toggle is enabled" must {
      "return tasks from the Tasks and Activities service" in {
        implicit val request: UserRequest[AnyContent] = userRequest
        val tasks                                     =
          Seq(Task("You owe £500 for tax year 2026 to 2027", TaskStatus.Incomplete, "/tax-you-paid", None))

        when(mockFeatureFlagService.get(ArgumentMatchers.eq(TasksAndActivitiesServiceToggle)))
          .thenReturn(Future.successful(FeatureFlag(TasksAndActivitiesServiceToggle, true)))
        when(mockTasksAndActivitiesConnector.getTasks(ArgumentMatchers.eq(generatedNino))(any(), any()))
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](tasks))

        val result = service.getListOfTasks.futureValue

        result mustBe tasks
        verify(mockTaxCalcPartialService, times(0)).getTaxCalcPartial(any())
      }

      "handle an empty response from the Tasks and Activities service" in {
        implicit val request: UserRequest[AnyContent] = userRequest

        when(mockFeatureFlagService.get(ArgumentMatchers.eq(TasksAndActivitiesServiceToggle)))
          .thenReturn(Future.successful(FeatureFlag(TasksAndActivitiesServiceToggle, true)))
        when(mockTasksAndActivitiesConnector.getTasks(ArgumentMatchers.eq(generatedNino))(any(), any()))
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](Seq.empty[Task]))

        val result = service.getListOfTasks.futureValue

        result mustBe Seq.empty
      }

      "handle an error from the Tasks and Activities service" in {
        implicit val request: UserRequest[AnyContent] = userRequest

        when(mockFeatureFlagService.get(ArgumentMatchers.eq(TasksAndActivitiesServiceToggle)))
          .thenReturn(Future.successful(FeatureFlag(TasksAndActivitiesServiceToggle, true)))
        when(mockTasksAndActivitiesConnector.getTasks(ArgumentMatchers.eq(generatedNino))(any(), any()))
          .thenReturn(EitherT.leftT[Future, Seq[Task]](UpstreamErrorResponse("service unavailable", 503)))

        val result = service.getListOfTasks.futureValue

        result mustBe Seq.empty
        verify(mockTaxCalcPartialService, times(0)).getTaxCalcPartial(any())
      }
    }

    "the Tasks and Activities Service toggle cannot be retrieved" must {
      "log the toggle issue and use the existing task logic" in {
        implicit val request: UserRequest[AnyContent] = userRequest
        when(mockConfigDecorator.taxCalcHomePageUrl).thenReturn("http://link/to/taxcalc")
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(TasksAndActivitiesServiceToggle)))
          .thenReturn(Future.failed(new RuntimeException("toggle unavailable")))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(ShowTaxCalcTileToggle)))
          .thenReturn(Future.successful(FeatureFlag(ShowTaxCalcTileToggle, true)))
        when(mockTaxCalcPartialService.getTaxCalcPartial(any())).thenReturn(
          Future.successful(Seq(SummaryCardPartial("tc1", Html(underpaid), Underpaid, 2026)))
        )

        val result = service.getListOfTasks.futureValue

        result mustBe Seq(
          Task("You owe £500 for tax year 2026 to 2027", TaskStatus.Incomplete, "http://link/to/taxcalc", None)
        )
      }
    }
  }

  "getTaxcalcTasks" when {
    "trusted helper is disable" must {
      "show underpaid / overpaid" in {
        implicit val request: UserRequest[AnyContent] = UserRequest(
          generatedNino,
          NonFilerSelfAssessmentUser,
          Credentials("credId", "GovernmentGateway"),
          ConfidenceLevel.L200,
          None,
          Set.empty,
          None,
          None,
          FakeRequest(),
          UserAnswers.empty
        )
        when(mockConfigDecorator.taxCalcHomePageUrl).thenReturn("http://link/to/taxcalc")
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(ShowTaxCalcTileToggle)))
          .thenReturn(Future.successful(FeatureFlag(ShowTaxCalcTileToggle, true)))
        when(mockTaxCalcPartialService.getTaxCalcPartial(any())).thenReturn(
          Future.successful(
            Seq(
              SummaryCardPartial("tc1", Html(overpaid), Overpaid, 2026),
              SummaryCardPartial("tc2", Html(underpaid), Underpaid, 2026),
              SummaryCardPartial("tc2", Html(""), NoReconciliationStatus, 2026),
              SummaryCardPartial("tc2", Html(""), Balanced, 2026),
              SummaryCardPartial("tc2", Html(""), OverpaidWithinTolerance, 2026),
              SummaryCardPartial("tc2", Html(""), UnderpaidWithinTolerance, 2026),
              SummaryCardPartial("tc2", Html(""), BalancedSA, 2026),
              SummaryCardPartial("tc2", Html(""), BalancedNoEmployment, 2026)
            )
          )
        )

        val result = service.getTaxCalcTasks(false).futureValue

        result mustBe Seq(
          Task(
            "HMRC owes you a £84.23 refund for year 2026 to 2027",
            TaskStatus.Incomplete,
            "http://link/to/taxcalc",
            None
          ),
          Task("You owe £500 for tax year 2026 to 2027", TaskStatus.Incomplete, "http://link/to/taxcalc", None)
        )
      }
    }

    "trusted helper is enable" must {
      "not show taxcalc tasks" in {
        implicit val request: UserRequest[AnyContent] = UserRequest(
          generatedNino,
          NonFilerSelfAssessmentUser,
          Credentials("credId", "GovernmentGateway"),
          ConfidenceLevel.L200,
          None,
          Set.empty,
          None,
          None,
          FakeRequest(),
          UserAnswers.empty
        )
        when(mockConfigDecorator.taxCalcHomePageUrl).thenReturn("http://link/to/taxcalc")
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(ShowTaxCalcTileToggle)))
          .thenReturn(Future.successful(FeatureFlag(ShowTaxCalcTileToggle, true)))
        when(mockTaxCalcPartialService.getTaxCalcPartial(any())).thenReturn(
          Future.successful(
            Seq(
              SummaryCardPartial("tc1", Html(overpaid), Overpaid, 2026),
              SummaryCardPartial("tc2", Html(underpaid), Underpaid, 2026),
              SummaryCardPartial("tc2", Html(""), NoReconciliationStatus, 2026),
              SummaryCardPartial("tc2", Html(""), Balanced, 2026),
              SummaryCardPartial("tc2", Html(""), OverpaidWithinTolerance, 2026),
              SummaryCardPartial("tc2", Html(""), UnderpaidWithinTolerance, 2026),
              SummaryCardPartial("tc2", Html(""), BalancedSA, 2026),
              SummaryCardPartial("tc2", Html(""), BalancedNoEmployment, 2026)
            )
          )
        )

        val result = service.getTaxCalcTasks(true).futureValue

        result mustBe Seq.empty
      }
    }

    "trusted helper is disable and taxcalc toggle is disabled" must {
      "not show taxcalc tasks" in {
        implicit val request: UserRequest[AnyContent] = UserRequest(
          generatedNino,
          NonFilerSelfAssessmentUser,
          Credentials("credId", "GovernmentGateway"),
          ConfidenceLevel.L200,
          None,
          Set.empty,
          None,
          None,
          FakeRequest(),
          UserAnswers.empty
        )
        when(mockConfigDecorator.taxCalcHomePageUrl).thenReturn("http://link/to/taxcalc")
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(ShowTaxCalcTileToggle)))
          .thenReturn(Future.successful(FeatureFlag(ShowTaxCalcTileToggle, false)))
        when(mockTaxCalcPartialService.getTaxCalcPartial(any())).thenReturn(
          Future.successful(
            Seq(
              SummaryCardPartial("tc1", Html(overpaid), Overpaid, 2026),
              SummaryCardPartial("tc2", Html(underpaid), Underpaid, 2026),
              SummaryCardPartial("tc2", Html(""), NoReconciliationStatus, 2026),
              SummaryCardPartial("tc2", Html(""), Balanced, 2026),
              SummaryCardPartial("tc2", Html(""), OverpaidWithinTolerance, 2026),
              SummaryCardPartial("tc2", Html(""), UnderpaidWithinTolerance, 2026),
              SummaryCardPartial("tc2", Html(""), BalancedSA, 2026),
              SummaryCardPartial("tc2", Html(""), BalancedNoEmployment, 2026)
            )
          )
        )

        val result = service.getTaxCalcTasks(false).futureValue

        result mustBe Seq.empty
        verify(mockTaxCalcPartialService, times(0)).getTaxCalcPartial(any())
      }
    }
  }

}
