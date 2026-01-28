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
import models.*
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.Configuration
import play.twirl.api.Html
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import views.html.ViewSpec
import views.html.home.options.*

import java.time.LocalDate

class HomeOptionsGeneratorSpec extends ViewSpec with MockitoSugar {

  implicit val configDecorator: ConfigDecorator = config

  private val latestNewsAndUpdatesView = inject[LatestNewsAndUpdatesView]
  private val taskListView             = inject[TaskListView]

  private val newsAndTilesConfig  = mock[NewsAndTilesConfig]
  private val stubConfigDecorator = new ConfigDecorator(
    inject[Configuration],
    inject[ServicesConfig]
  )

  private def createHomeOptionsGenerator(usedConfigDecorator: ConfigDecorator): HomeOptionsGenerator =
    new HomeOptionsGenerator(
      latestNewsAndUpdatesView,
      newsAndTilesConfig,
      taskListView
    )(usedConfigDecorator)

  private lazy val homeOptionsGenerator = createHomeOptionsGenerator(stubConfigDecorator)

  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
    buildUserRequest(
      saUser = NonFilerSelfAssessmentUser,
      request = FakeRequest(),
      authNino = generatedNino
    )

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

  "Calling listOfTasks" must {
    "return listOfTasks markup" in {
      val result = homeOptionsGenerator.getListOfTasks().futureValue
      result mustBe taskListView(Nil)
    }
  }
}
