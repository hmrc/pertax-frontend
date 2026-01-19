/*
 * Copyright 2026 HM Revenue & Customs
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
import models.admin.{AddressChangeAllowedToggle, AllFeatureFlags, DfsFormsFrontendAvailabilityToggle, GetPersonFromCitizenDetailsToggle}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.i18n.MessagesApi
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.typedmap.TypedMap
import play.api.mvc.request.{Cell, RequestAttrKey}
import play.api.mvc.*
import play.api.test.{FakeRequest, Injecting}
import play.twirl.api.Html
import repositories.EditAddressLockRepository
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.play.partials.FormPartialRetriever
import uk.gov.hmrc.sca.models.{PtaMinMenuConfig, WrapperDataResponse}
import uk.gov.hmrc.sca.utils.Keys

import scala.concurrent.{ExecutionContext, Future}

trait BaseSpec
    extends AnyWordSpec
    with GuiceOneAppPerSuite
    with Matchers
    with BeforeAndAfterEach
    with MockitoSugar
    with ScalaFutures
    with IntegrationPatience
    with Injecting
    with CleanMongoCollectionSupport {
  this: Suite =>

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockPartialRetriever: FormPartialRetriever = mock[FormPartialRetriever]
  when(mockPartialRetriever.getPartialContentAsync(any(), any(), any())(any(), any()))
    .thenReturn(Future.successful(Html("")))

  val mockEditAddressLockRepository: EditAddressLockRepository = mock[EditAddressLockRepository]

  val configValues: Map[String, Any] =
    Map(
      "cookie.encryption.key"               -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "sso.encryption.key"                  -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "queryParameter.encryption.key"       -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "json.encryption.key"                 -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "metrics.enabled"                     -> false,
      "auditing.enabled"                    -> false,
      "external-url.mtd-claim-from-pta.url" -> "http://localhost:9999/mtd-claim-from-pta/handoff"
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

  implicit lazy val ec: ExecutionContext     = inject[ExecutionContext]
  lazy val mcc: MessagesControllerComponents = inject[MessagesControllerComponents]
  implicit def messagesApi: MessagesApi      = inject[MessagesApi]

  lazy val config: ConfigDecorator = app.injector.instanceOf[ConfigDecorator]

  val generatedTrustedHelperNino: Nino = new Generator().nextNino
  val generatedNino: Nino              = new Generator().nextNino

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockFeatureFlagService)
    reset(mockEditAddressLockRepository)

    AllFeatureFlags.list.foreach { flag =>
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(flag)))
        .thenReturn(Future.successful(FeatureFlag(flag, isEnabled = false)))
    }

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressChangeAllowedToggle)))
      .thenReturn(Future.successful(FeatureFlag(AddressChangeAllowedToggle, isEnabled = true)))

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(GetPersonFromCitizenDetailsToggle)))
      .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(DfsFormsFrontendAvailabilityToggle)))
      .thenReturn(Future.successful(FeatureFlag(DfsFormsFrontendAvailabilityToggle, isEnabled = true)))
  }

  val wrapperDataResponse: WrapperDataResponse = WrapperDataResponse(
    Seq.empty,
    PtaMinMenuConfig("", ""),
    List.empty,
    List.empty,
    None,
    None
  )

  def fakeScaRequest(method: String = "GET", path: String = ""): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(
      method,
      path,
      headers = Headers(),
      body = AnyContentAsEmpty,
      attrs = TypedMap(
        Keys.wrapperIsAuthenticatedKey -> true,
        Keys.wrapperFilterHasRun       -> true,
        Keys.wrapperDataKey            -> wrapperDataResponse,
        Keys.messageDataKey            -> 0,
        RequestAttrKey.Cookies         -> Cell(Cookies(Seq(Cookie("PLAY_LANG", "en"))))
      )
    )

}
