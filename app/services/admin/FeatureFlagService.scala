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

import config.ConfigDecorator
import models.admin.{DeletedToggle, FeatureFlag, FeatureFlagName}
import play.api.Logging
import repositories.admin.FeatureFlagRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.{Duration, FiniteDuration, SECONDS => Seconds}
import scala.concurrent.{ExecutionContext, Future}
import play.api.cache.AsyncCacheApi

@Singleton
class FeatureFlagService @Inject() (
  configDecorator: ConfigDecorator,
  featureFlagRepository: FeatureFlagRepository,
  cache: AsyncCacheApi
)(implicit
  ec: ExecutionContext
) extends Logging {
  val cacheValidFor: FiniteDuration   =
    Duration(configDecorator.ehCacheTtlInSeconds, Seconds)
  private val allFeatureFlagsCacheKey = "*$*$allFeatureFlags*$*$"

  def set(flagName: FeatureFlagName, enabled: Boolean): Future[Boolean] =
    for {
      _      <- cache.remove(flagName.toString)
      _      <- cache.remove(allFeatureFlagsCacheKey)
      result <- featureFlagRepository.setFeatureFlag(flagName, enabled)
      //blocking thread to let time to other containers to update their cache
      _      <- Future.successful(Thread.sleep(configDecorator.ehCacheTtlInSeconds * 1000))
    } yield result

  def get(flagName: FeatureFlagName): Future[FeatureFlag] =
    cache.getOrElseUpdate(flagName.toString, cacheValidFor) {
      featureFlagRepository
        .getFeatureFlag(flagName)
        .map(_.getOrElse(FeatureFlag(flagName, false)))
    }

  def getAll: Future[List[FeatureFlag]] =
    cache.getOrElseUpdate(allFeatureFlagsCacheKey, cacheValidFor) {
      featureFlagRepository.getAllFeatureFlags.map { mongoFlags =>
        val (deletedFlags, validMongoFlags) = mongoFlags.partition(_.name.isInstanceOf[DeletedToggle])

        Future(deletedFlags.foreach { flag =>
          featureFlagRepository.deleteFeatureFlag(flag.name).map {
            case true  => logger.warn(s"Flag `${flag.name}` has been deleted from Mongo")
            case false => logger.error(s"Flag `${flag.name}` could not be deleted from Mongo")
          }
        })

        FeatureFlagName.allFeatureFlags
          .foldLeft(validMongoFlags.reverse) { (featureFlags, missingFlag) =>
            if (featureFlags.map(_.name).contains(missingFlag)) {
              featureFlags
            } else {
              FeatureFlag(missingFlag, false) :: featureFlags
            }
          }
          .reverse
      }
    }

  def setAll(flags: Map[FeatureFlagName, Boolean]): Future[Unit] =
    Future
      .sequence(flags.keys.map(flag => cache.remove(flag.toString)))
      .flatMap { _ =>
        cache.remove(allFeatureFlagsCacheKey)
        featureFlagRepository.setFeatureFlags(flags)
      }
      .map { _ =>
        //blocking thread to let time to other containers to update their cache
        Thread.sleep(configDecorator.ehCacheTtlInSeconds * 1000)
        ()
      }

}
