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

package services.admin

import akka.Done
import org.mockito.{ArgumentCaptor, MockitoSugar}
import org.mockito.ArgumentMatchers.any
import play.api.cache.AsyncCacheApi
import play.api.inject.bind
import config.ConfigDecorator
import models.admin.{AddressTaxCreditsBrokerCallToggle, NationalInsuranceTileToggle, TaxcalcToggle}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.admin.FeatureFlagRepository

import scala.jdk.CollectionConverters._
import scala.concurrent.Future

class FeatureFlagServiceSpec
    extends AnyWordSpec
    with GuiceOneAppPerSuite
    with Matchers
    with PatienceConfiguration
    with BeforeAndAfterEach
    with MockitoSugar {

  val mockConfigDecorator       = mock[ConfigDecorator]
  val mockFeatureFlagRepository = mock[FeatureFlagRepository]
  val mockCache                 = mock[AsyncCacheApi]

  override implicit lazy val app = GuiceApplicationBuilder()
    .overrides(
      bind[ConfigDecorator].toInstance(mockConfigDecorator),
      bind[FeatureFlagRepository].toInstance(mockFeatureFlagRepository),
      bind[AsyncCacheApi].toInstance(mockCache)
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConfigDecorator, mockFeatureFlagRepository, mockCache)
  }

  val featureFlagService = app.injector.instanceOf[FeatureFlagService]

  "set" must {
    "set a feature flag" in {
      when(mockCache.remove(any())).thenReturn(Future.successful(Done))
      when(mockFeatureFlagRepository.setFeatureFlag(any(), any())).thenReturn(Future.successful(true))

      val result = featureFlagService.set(NationalInsuranceTileToggle, true).futureValue

      result mustBe true
      val eventCaptor             = ArgumentCaptor.forClass(classOf[String])
      verify(mockCache, times(2)).remove(eventCaptor.capture())
      verify(mockFeatureFlagRepository, times(1)).setFeatureFlag(any(), any())
      val arguments: List[String] = eventCaptor.getAllValues.asScala.toList
      arguments.sorted mustBe List(
        NationalInsuranceTileToggle.toString,
        "*$*$allFeatureFlags*$*$"
      ).sorted
    }
  }

  "setAll" must {
    "set all the feature flags provided" in {
      when(mockCache.remove(any())).thenReturn(Future.successful(Done))
      when(mockFeatureFlagRepository.setFeatureFlags(any()))
        .thenReturn(Future.successful(()))

      val result = featureFlagService
        .setAll(
          Map(AddressTaxCreditsBrokerCallToggle -> false, NationalInsuranceTileToggle -> true, TaxcalcToggle -> true)
        )
        .futureValue

      result mustBe ((): Unit)

      val eventCaptor = ArgumentCaptor.forClass(classOf[String])
      verify(mockCache, times(4)).remove(eventCaptor.capture())
      verify(mockFeatureFlagRepository, times(1)).setFeatureFlags(any())

      val arguments: List[String] = eventCaptor.getAllValues.asScala.toList
      arguments.sorted mustBe List(
        AddressTaxCreditsBrokerCallToggle.toString,
        NationalInsuranceTileToggle.toString,
        TaxcalcToggle.toString,
        "*$*$allFeatureFlags*$*$"
      ).sorted
    }
  }
}
