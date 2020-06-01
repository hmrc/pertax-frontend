/*
 * Copyright 2020 HM Revenue & Customs
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

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.{Reads, Writes}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.http.logging.SessionId

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SafeLocalSessionCache @Inject()(localSessionCache: LocalSessionCache) {
  private def ensureSessionIdExists(nino: Nino)(implicit hc: HeaderCarrier): HeaderCarrier =
    if (hc.sessionId.isEmpty) {
      hc.copy(sessionId = Some(SessionId(nino.nino)))
    } else {
      hc
    }

  def cache[A](nino: Nino, formId: String, body: A)(
    implicit wts: Writes[A],
    hc: HeaderCarrier,
    ec: ExecutionContext): Future[CacheMap] = {
    implicit val safeHc = ensureSessionIdExists(nino)

    localSessionCache.cache(formId, body)(wts, safeHc, ec)
  }

  def fetch(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[CacheMap]] = {
    implicit val safeHc = ensureSessionIdExists(nino)

    localSessionCache.fetch()(safeHc, ec)
  }

  def fetchAndGetEntry[T](
    nino: Nino,
    key: String)(implicit hc: HeaderCarrier, rds: Reads[T], ec: ExecutionContext): Future[Option[T]] = {
    implicit val safeHc = ensureSessionIdExists(nino)

    localSessionCache.fetchAndGetEntry(key)(safeHc, rds, ec)
  }

  def remove(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    implicit val safeHc = ensureSessionIdExists(nino)

    localSessionCache.remove()(safeHc, ec)
  }
}
