/*
 * Copyright 2024 HM Revenue & Customs
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

package testUtils

import config.ConfigDecorator
import controllers.auth.AuthJourney
import models.admin.{AddressChangeAllowedToggle, AllFeatureFlags, DfsDigitalFormFrontendAvailableToggle, GetPersonFromCitizenDetailsToggle}
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentMatchers, MockitoSugar}
import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.i18n.MessagesApi
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.MessagesControllerComponents
import play.api.test.Helpers.baseApplicationBuilder.injector
import play.api.test.Injecting
import play.twirl.api.Html
import repositories.EditAddressLockRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.play.partials.FormPartialRetriever

import scala.concurrent.{ExecutionContext, Future}

trait BaseSpec
    extends AnyWordSpec
    with GuiceOneAppPerSuite
    with Matchers
    with BeforeAndAfterEach
    with MockitoSugar
    with ScalaFutures
    with IntegrationPatience
    with Injecting {
  this: Suite =>

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockPartialRetriever: FormPartialRetriever = mock[FormPartialRetriever]
  when(mockPartialRetriever.getPartialContentAsync(any(), any(), any())(any(), any()))
    .thenReturn(Future.successful(Html("")))

  val mockEditAddressLockRepository: EditAddressLockRepository = mock[EditAddressLockRepository]

  val configValues: Map[String, Any] =
    Map(
      "cookie.encryption.key"         -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "sso.encryption.key"            -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "queryParameter.encryption.key" -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "json.encryption.key"           -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "metrics.enabled"               -> false,
      "auditing.enabled"              -> false
    )

  val mockFeatureFlagService: FeatureFlagService = mock[FeatureFlagService]

  protected def localGuiceApplicationBuilder(extraConfigValues: Map[String, Any] = Map.empty): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .overrides(
        bind[FormPartialRetriever].toInstance(mockPartialRetriever),
        bind[EditAddressLockRepository].toInstance(mockEditAddressLockRepository),
        bind[FeatureFlagService].toInstance(mockFeatureFlagService)
      )
      .configure(configValues ++ extraConfigValues)

  override implicit lazy val app: Application = localGuiceApplicationBuilder().build()
  val mockAuthJourney: AuthJourney            = mock[AuthJourney]

  implicit lazy val ec: ExecutionContext     = injector().instanceOf[ExecutionContext]
  lazy val mcc: MessagesControllerComponents = inject[MessagesControllerComponents]
  implicit def messagesApi: MessagesApi      = inject[MessagesApi]

  lazy val config: ConfigDecorator = app.injector.instanceOf[ConfigDecorator]

  override def beforeEach(): Unit = {
    super.beforeEach()
    org.mockito.MockitoSugar.reset(mockFeatureFlagService)
    AllFeatureFlags.list.foreach { flag =>
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(flag)))
        .thenReturn(Future.successful(FeatureFlag(flag, isEnabled = false)))
    }

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(GetPersonFromCitizenDetailsToggle)))
      .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressChangeAllowedToggle)))
      .thenReturn(Future.successful(FeatureFlag(AddressChangeAllowedToggle, isEnabled = true)))

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(DfsDigitalFormFrontendAvailableToggle)))
      .thenReturn(Future.successful(FeatureFlag(DfsDigitalFormFrontendAvailableToggle, isEnabled = true)))
  }
}
