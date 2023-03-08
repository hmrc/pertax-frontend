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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.cache.AsyncCacheApi
import play.api.inject.bind
import config.ConfigDecorator
import models.admin.{DeletedToggle, FeatureFlag, FeatureFlagName, SingleAccountCheckToggle, TaxcalcToggle}
import repositories.admin.FeatureFlagRepository
import testUtils.BaseSpec

import scala.concurrent.Future

class FeatureFlagServiceWithCacheSpec extends BaseSpec {

  val mockAppConfig             = mock[ConfigDecorator]
  val mockFeatureFlagRepository = mock[FeatureFlagRepository]

  override implicit lazy val app = localGuiceApplicationBuilder()
    .overrides(
      bind[ConfigDecorator].toInstance(mockAppConfig),
      bind[FeatureFlagRepository].toInstance(mockFeatureFlagRepository)
    )
    .build()

  override def beforeEach(): Unit = {
    reset(mockAppConfig, mockFeatureFlagRepository)
    FeatureFlagName.allFeatureFlags.foreach { flag =>
      cache.remove(flag.toString)
    }
    cache.remove("*$*$allFeatureFlags*$*$")
  }

  lazy val featureFlagService = inject[FeatureFlagService]
  lazy val cache              = inject[AsyncCacheApi]

  "getAll" must {
    "get all the feature flags defaulted to false" when {
      "No toggle are in Mongo" in {
        val expectedFeatureFlags = FeatureFlagName.allFeatureFlags.map(FeatureFlag(_, false))
        when(mockFeatureFlagRepository.getAllFeatureFlags).thenReturn(Future.successful(List.empty))

        val result = featureFlagService.getAll.futureValue
        result mustBe expectedFeatureFlags

        verify(mockFeatureFlagRepository, times(1)).getAllFeatureFlags
      }
    }

    "get all the feature flags" when {
      "All toggles are in Mongo" in {
        val expectedFeatureFlags = FeatureFlagName.allFeatureFlags.map(FeatureFlag(_, true))
        when(mockFeatureFlagRepository.getAllFeatureFlags).thenReturn(Future.successful(expectedFeatureFlags))

        val result = featureFlagService.getAll.futureValue
        result mustBe expectedFeatureFlags

        verify(mockFeatureFlagRepository, times(1)).getAllFeatureFlags
      }

      "some toggles are in Mongo" in {
        val mongoFlags           = List(FeatureFlag(SingleAccountCheckToggle, true), FeatureFlag(TaxcalcToggle, true))
        val expectedFeatureFlags = FeatureFlagName.allFeatureFlags
          .map(FeatureFlag(_, false))
          .filterNot(flag => mongoFlags.map(_.name.toString).contains(flag.name.toString)) ::: mongoFlags
        when(mockFeatureFlagRepository.getAllFeatureFlags).thenReturn(Future.successful(mongoFlags))

        val result = featureFlagService.getAll.futureValue
        result.sortBy(_.name.toString) mustBe expectedFeatureFlags.sortBy(_.name.toString)

        verify(mockFeatureFlagRepository, times(1)).getAllFeatureFlags
      }
    }

    "get flags and delete the unused ones" in {
      val expectedFeatureFlags = FeatureFlagName.allFeatureFlags.map(FeatureFlag(_, false))
      when(mockFeatureFlagRepository.getAllFeatureFlags).thenReturn(
        Future.successful(
          List(FeatureFlag(DeletedToggle("deleted-toggle"), true), FeatureFlag(DeletedToggle("deleted-toggle2"), true))
        )
      )
      when(mockFeatureFlagRepository.deleteFeatureFlag(any())).thenReturn(Future.successful(true))

      val result = featureFlagService.getAll.futureValue
      result mustBe expectedFeatureFlags

      verify(mockFeatureFlagRepository, times(1)).getAllFeatureFlags
      verify(mockFeatureFlagRepository, times(2)).deleteFeatureFlag(any())
    }

  }

  "get" must {
    "get a feature flag set to false if not present in Mongo" in {
      val expectedFeatureFlag = FeatureFlag(SingleAccountCheckToggle, false)
      when(mockFeatureFlagRepository.getFeatureFlag(any())).thenReturn(Future.successful(None))

      val result = featureFlagService.get(SingleAccountCheckToggle).futureValue
      result mustBe expectedFeatureFlag

      verify(mockFeatureFlagRepository, times(1)).getFeatureFlag(any())
    }

    "get a feature flag and the response is cached" in {
      val featureFlag = FeatureFlag(SingleAccountCheckToggle, true)
      when(mockFeatureFlagRepository.getFeatureFlag(any())).thenReturn(Future.successful(Some(featureFlag)))

      val result = (for {
        _      <- featureFlagService.get(SingleAccountCheckToggle)
        _      <- featureFlagService.get(SingleAccountCheckToggle)
        result <- featureFlagService.get(SingleAccountCheckToggle)
      } yield result).futureValue

      result mustBe featureFlag

      verify(mockFeatureFlagRepository, times(1)).getFeatureFlag(any())
    }

    "get all feature flags and the response is cached" in {
      val featureFlags = FeatureFlagName.allFeatureFlags.map(name => FeatureFlag(name, false))
      when(mockFeatureFlagRepository.getAllFeatureFlags).thenReturn(Future.successful(List.empty))

      val result = (for {
        _      <- featureFlagService.getAll
        _      <- featureFlagService.getAll
        result <- featureFlagService.getAll
      } yield result).futureValue

      result mustBe featureFlags

      verify(mockFeatureFlagRepository, times(1)).getAllFeatureFlags
    }

    "get all feature flags but Mongo returns None" in {
      implicit def ordering: Ordering[FeatureFlagName] = new Ordering[FeatureFlagName] {
        override def compare(x: FeatureFlagName, y: FeatureFlagName): Int =
          x.toString.compareTo(y.toString)
      }
      val expectedFeatureFlags                         = FeatureFlagName.allFeatureFlags.map(name => FeatureFlag(name, false))
      when(mockFeatureFlagRepository.getAllFeatureFlags).thenReturn(Future.successful(List.empty))

      val result = featureFlagService.getAll.futureValue
      result.sortBy(_.name) mustBe expectedFeatureFlags.sortBy(_.name)

      verify(mockFeatureFlagRepository, times(1)).getAllFeatureFlags
    }
  }
}
