/*
 * Copyright 2022 HM Revenue & Customs
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

import models.admin.{FeatureFlag, FeatureFlagName}
import repositories.admin.FeatureFlagRepository
import uk.gov.hmrc.mongo.MongoComponent

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FeatureFlagRepositoryUtils @Inject() (mongoComponent: MongoComponent)(implicit
  ec: ExecutionContext
) extends FeatureFlagRepository(mongoComponent)(ec) {

  def insertFeatureFlag(name: FeatureFlagName, enabled: Boolean): Future[Boolean] =
    collection
      .insertOne(FeatureFlag(name, enabled))
      .map(_.wasAcknowledged())
      .toSingle()
      .toFuture()
}
